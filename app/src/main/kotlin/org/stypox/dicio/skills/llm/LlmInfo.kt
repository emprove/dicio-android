package org.stypox.dicio.skills.llm

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.Skill
import org.dicio.skill.skill.SkillInfo
import org.stypox.dicio.R
import org.stypox.dicio.sentences.Sentences
import org.stypox.dicio.settings.ui.StringSetting

object LlmInfo : SkillInfo("llm") {
    const val DEFAULT_MODEL = "openrouter/auto"
    private const val PREFS = "skill_settings_llm"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_MODEL = "model"

    fun apiKey(context: Context): String =
        prefs(context).getString(KEY_API_KEY, "")!!.trim()

    fun model(context: Context): String =
        prefs(context).getString(KEY_MODEL, "")!!.trim().ifEmpty { DEFAULT_MODEL }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun name(context: Context) =
        context.getString(R.string.skill_name_llm)

    override fun sentenceExample(context: Context) =
        context.getString(R.string.skill_sentence_example_llm)

    @Composable
    override fun icon() =
        rememberVectorPainter(Icons.Default.Psychology)

    override fun build(ctx: SkillContext): Skill<*>? {
        val data = Sentences.Llm[ctx.sentencesLanguage] ?: return null
        return LlmSkill(LlmInfo, data)
    }

    override val renderSettings: @Composable () -> Unit get() = @Composable {
        val context = LocalContext.current
        var apiKey by remember { mutableStateOf(apiKey(context)) }
        var model by remember { mutableStateOf(prefs(context).getString(KEY_MODEL, "")!!) }

        Column {
            StringSetting(
                title = stringResource(R.string.pref_llm_api_key),
                descriptionWhenEmpty = stringResource(R.string.pref_llm_api_key_empty),
            ).Render(
                // never show the key itself on screen
                value = if (apiKey.isEmpty()) "" else "••••" + apiKey.takeLast(4),
                onValueChange = { newValue ->
                    if (!newValue.startsWith("••••")) {
                        apiKey = newValue.trim()
                        prefs(context).edit().putString(KEY_API_KEY, apiKey).apply()
                    }
                },
            )
            StringSetting(
                title = stringResource(R.string.pref_llm_model),
                descriptionWhenEmpty = DEFAULT_MODEL,
            ).Render(
                value = model,
                onValueChange = { newValue ->
                    model = newValue.trim()
                    prefs(context).edit().putString(KEY_MODEL, model).apply()
                },
            )
        }
    }
}
