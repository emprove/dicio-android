package org.stypox.dicio.skills.llm

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.standard.StandardRecognizerData
import org.dicio.skill.standard.StandardRecognizerSkill
import org.json.JSONArray
import org.json.JSONObject
import org.stypox.dicio.sentences.Sentences.Llm
import org.stypox.dicio.skills.music_search.MusicSearchSkill
import java.net.HttpURLConnection
import java.net.URL

/**
 * "гав гав <вопрос>" -> sends ONLY the recognized text (never audio) to OpenRouter.
 * The model either answers (spoken aloud) or, for music requests, returns a concrete
 * YouTube Music search query which is then played via [MusicSearchSkill].
 */
class LlmSkill(correspondingSkillInfo: SkillInfo, data: StandardRecognizerData<Llm>)
    : StandardRecognizerSkill<Llm>(correspondingSkillInfo, data) {

    override suspend fun generateOutput(ctx: SkillContext, inputData: Llm): SkillOutput {
        val question = when (inputData) {
            is Llm.Query -> inputData.question?.trim()
        }
        if (question.isNullOrEmpty()) return LlmOutput.NoQuestion

        val apiKey = LlmInfo.apiKey(ctx.android)
        if (apiKey.isEmpty()) return LlmOutput.NoApiKey

        return try {
            val reply = withContext(Dispatchers.IO) {
                ask(apiKey, LlmInfo.model(ctx.android), question, ctx.locale.language,
                    LlmInfo.reasoningEffort(ctx.android))
            }
            when (val decision = parseDecision(reply)) {
                is Decision.Play ->
                    MusicSearchSkill.playOnYouTubeMusic(ctx.android, decision.query)
                is Decision.Answer ->
                    LlmOutput.Answer(question, decision.text)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "OpenRouter request failed", e)
            LlmOutput.Error(e.message ?: e.javaClass.simpleName)
        }
    }

    companion object {
        val TAG: String = LlmSkill::class.simpleName!!
        private const val ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"

        sealed interface Decision {
            data class Play(val query: String) : Decision
            data class Answer(val text: String) : Decision
        }

        private fun systemPrompt(language: String) = """
            You are a voice assistant on a phone with YouTube Music. The user's words were
            transcribed by offline speech recognition and may contain recognition errors:
            guess the intended meaning.

            Reply with ONE JSON object and nothing else.

            If the user wants music played (any request to play/put on songs, a genre, a mood,
            an artist, "something for dancing", etc.), reply:
            {"action":"play","query":"<search query>"}
            The query is typed into YouTube Music search and its TOP result is played, so make
            it concrete and searchable: a well-known artist + song, or a specific popular
            playlist/genre name (e.g. "Gipsy Kings Bamboleo", "Eurodance 90s hits",
            "Russian rock classics"). Vague moods search badly - translate them into concrete
            artists/genres. Prefer one strong song or a playlist name over a long phrase.

            Otherwise reply:
            {"action":"answer","text":"<answer>"}
            The answer is read aloud: language with ISO code '$language', 1-3 short sentences,
            plain text, no markdown, no lists, no emoji, no links.
        """.trimIndent()

        /** Tolerant parsing: models sometimes wrap JSON in prose or ```fences```. */
        fun parseDecision(reply: String): Decision {
            val start = reply.indexOf('{')
            val end = reply.lastIndexOf('}')
            if (start >= 0 && end > start) {
                runCatching {
                    val json = JSONObject(reply.substring(start, end + 1))
                    val query = json.optString("query").trim()
                    if (json.optString("action") == "play" && query.isNotEmpty()) {
                        return Decision.Play(query)
                    }
                    val text = json.optString("text").trim()
                    if (text.isNotEmpty()) {
                        return Decision.Answer(text)
                    }
                }
            }
            // not JSON at all: treat the whole thing as a spoken answer
            return Decision.Answer(reply.replace(Regex("[*_#`{}]"), "").trim())
        }

        fun ask(
            apiKey: String,
            model: String,
            question: String,
            language: String,
            reasoningEffort: String = "",
        ): String {
            val body = JSONObject()
                .put("model", model)
                .put("max_tokens", 300)
                .put("messages", JSONArray()
                    .put(JSONObject().put("role", "system").put("content", systemPrompt(language)))
                    .put(JSONObject().put("role", "user").put("content", question)))
            // thinking is what makes a voice reply slow: by default ask for minimal reasoning
            // https://openrouter.ai/docs/guides/best-practices/reasoning-tokens
            if (reasoningEffort.isNotEmpty()) {
                body.put("reasoning", JSONObject().put("effort", reasoningEffort).put("exclude", true))
            }

            val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 10_000
                connection.readTimeout = 30_000
                connection.doOutput = true
                connection.setRequestProperty("Authorization", "Bearer $apiKey")
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("X-Title", "Dicio")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code !in 200..299) {
                    val message = runCatching {
                        JSONObject(text).getJSONObject("error").getString("message")
                    }.getOrNull()
                    throw IllegalStateException("HTTP $code" + (message?.let { ": $it" } ?: ""))
                }

                return JSONObject(text)
                    .getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content")
                    .trim()
            } finally {
                connection.disconnect()
            }
        }
    }
}
