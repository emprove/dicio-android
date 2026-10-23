package org.stypox.dicio.skills.music_search

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.standard.StandardRecognizerData
import org.dicio.skill.standard.StandardRecognizerSkill
import org.stypox.dicio.sentences.Sentences.MusicSearch
import org.stypox.dicio.skills.notify.NotifyHandler

/**
 * "Включи <что угодно>" -> YouTube Music searches for it and starts playing the top result.
 *
 * Order of attempts:
 * 1. YouTube Music's live media session: MediaController.TransportControls.playFromSearch().
 *    Does not launch any activity, so it works on the lock screen and while the screen is pinned.
 * 2. The standard Android intent MEDIA_PLAY_FROM_SEARCH addressed to YouTube Music
 *    (the same contract voice assistants use). Needs YouTube Music to come to the foreground.
 * 3. Opening the YouTube Music search page for the query (shows results, no autoplay).
 */
class MusicSearchSkill(correspondingSkillInfo: SkillInfo, data: StandardRecognizerData<MusicSearch>)
    : StandardRecognizerSkill<MusicSearch>(correspondingSkillInfo, data) {

    override suspend fun generateOutput(ctx: SkillContext, inputData: MusicSearch): SkillOutput {
        val raw = when (inputData) {
            is MusicSearch.Query -> inputData.what
        }
        return playOnYouTubeMusic(ctx.android, raw)
    }

    companion object {
        val TAG: String = MusicSearchSkill::class.simpleName!!
        const val YT_MUSIC_PACKAGE = "com.google.android.apps.youtube.music"
        const val METHOD_SESSION = "медиасессия"
        const val METHOD_INTENT = "интент голосового поиска"
        const val METHOD_URI_SESSION = "ID трека через медиасессию"
        const val METHOD_URI_LINK = "ссылка на трек"

        private val GENERIC_WORDS = setOf(
            "музыку", "музыка", "музычку", "песню", "песенку", "трек", "что-нибудь",
            "что нибудь", "что то", "что-то", "плеер", "радио", "music", "something",
        )
        private val PAUSE_WORDS = setOf("на паузу", "паузу", "пауза")

        // "... в ютуб музыке", "... на youtube music" etc. at the end of the request
        private val APP_SUFFIX = Regex(
            "\\s*(в|на|через|из|по)?\\s*(ютуб|ютюб|ютьюб|youtube|you tube)" +
                    "(\\s*(музык\\p{L}*|мьюзик|мюзик|мьюзике|music))?\\s*$"
        )
        private val LEADING_FILLER = Regex("^(мне|нам|пожалуйста)\\s+")
        private val TRAILING_FILLER = Regex("\\s+(пожалуйста)$")

        /**
         * Shared entry point, also used by the LLM skill once the model has turned a vague
         * request ("музыку чтоб ноги в пляс пошли") into a concrete search query.
         *
         * Every method is verified: we watch YouTube Music's media session and only report
         * success if the playing track actually changed. Otherwise we try the next method.
         */
        suspend fun playOnYouTubeMusic(context: Context, raw: String?): MusicSearchOutput {
            val query = cleanQuery(raw)
            Log.i(TAG, "request raw='$raw' cleaned='$query'")

            if (!isInstalled(context)) {
                return MusicSearchOutput.NotInstalled
            }

            val session = findYouTubeMusicSession(context)
            val diag = StringBuilder()
            diag.append(describeSession(context, session))

            // "поставь на паузу" / "выключи музыку" might land here instead of the media skill
            if (query in PAUSE_WORDS) {
                session?.transportControls?.pause()
                return MusicSearchOutput.Paused
            }

            // "включи музыку" -> just resume (or let YouTube Music pick something)
            if (query.isEmpty() || query in GENERIC_WORDS) {
                if (session != null) {
                    session.transportControls.play()
                    return MusicSearchOutput.Resuming
                }
                startPlayFromSearchIntent(context, "")
                return MusicSearchOutput.Resuming
            }

            // 0. exact track via YouTube Data API -> play a concrete watch link
            val apiKey = MusicSearchInfo.youtubeApiKey(context)
            if (apiKey.isEmpty()) {
                diag.append("\n0) пропущен: не задан ключ YouTube Data API")
            } else {
                val found = try {
                    withContext(Dispatchers.IO) { YouTubeSearch.findVideo(apiKey, query) }
                } catch (e: Throwable) {
                    Log.w(TAG, "YouTube Data API search failed", e)
                    diag.append("\n0) ошибка YouTube Data API: ${e.message}")
                    null
                }
                if (found == null) {
                    diag.append("\n0) YouTube Data API ничего не нашёл")
                } else {
                    val (videoId, title) = found
                    val watchUri = Uri.parse("https://music.youtube.com/watch?v=$videoId")
                    Log.i(TAG, "method 0: videoId=$videoId title='$title'")

                    // 0a. through the media session: no activity launch needed
                    val session0 = findYouTubeMusicSession(context)
                    if (session0 != null) {
                        val before = fingerprint(session0)
                        session0.transportControls.playFromUri(watchUri, Bundle())
                        if (waitForChange(context, before, 6000)) {
                            return MusicSearchOutput.Playing(title, METHOD_URI_SESSION, diag.toString())
                        }
                        diag.append("\n0a) playFromUri: трек не сменился за 6 с")
                    }

                    // 0b. open the watch link in YouTube Music: it autoplays the track
                    val before = findYouTubeMusicSession(context)?.let { fingerprint(it) }
                    val intent = Intent(Intent.ACTION_VIEW, watchUri)
                        .setPackage(YT_MUSIC_PACKAGE)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (tryStart(context, intent) && waitForChange(context, before, 10000)) {
                        return MusicSearchOutput.Playing(title, METHOD_URI_LINK, diag.toString())
                    }
                    diag.append("\n0b) ссылка на трек «$title»: трек не сменился за 10 с")
                }
            }

            // 1. media session, no activity launch (works on lock screen / pinned screen)
            if (session != null) {
                val before = fingerprint(session)
                Log.i(TAG, "method 1: session.playFromSearch, before='$before'")
                session.transportControls.playFromSearch(query, Bundle())
                if (waitForChange(context, before, 10000)) {
                    return MusicSearchOutput.Playing(query, METHOD_SESSION, diag.toString())
                }
                diag.append("\n1) playFromSearch: трек не сменился за 10 с")
            } else {
                diag.append("\n1) пропущен: нет медиасессии YouTube Music")
            }

            // 2. standard voice-search intent (needs Dicio to be allowed to start activities)
            val before2 = findYouTubeMusicSession(context)?.let { fingerprint(it) }
            val started2 = startPlayFromSearchIntent(context, query)
            Log.i(TAG, "method 2: MEDIA_PLAY_FROM_SEARCH intent started=$started2")
            if (started2 && waitForChange(context, before2, 8000)) {
                return MusicSearchOutput.Playing(query, METHOD_INTENT, diag.toString())
            }
            diag.append(
                if (started2) "\n2) интент отправлен, но трек не сменился за 8 с"
                else "\n2) интент не принят системой"
            )

            // 3. at least show the search results
            val started3 = openSearchPage(context, query)
            Log.i(TAG, "method 3: open search page started=$started3")
            diag.append(
                if (started3) "\n3) открыт поиск YouTube Music (без автозапуска)"
                else "\n3) не удалось открыть поиск"
            )
            Log.i(TAG, "diagnostics:\n$diag")
            return MusicSearchOutput.Failed(query, diag.toString())
        }

        private fun describeSession(context: Context, session: MediaController?): String {
            if (!hasListenerAccess(context)) {
                return "Нет доступа к уведомлениям — медиасессии не видны"
            }
            if (session == null) {
                return "Медиасессия YouTube Music не найдена"
            }
            val actions = session.playbackState?.actions
            val search = actions?.let { (it and PlaybackState.ACTION_PLAY_FROM_SEARCH) != 0L }
            return "Сессия YouTube Music: state=${session.playbackState?.state}, " +
                    "actions=$actions, playFromSearch заявлен=$search"
        }

        private fun hasListenerAccess(context: Context): Boolean = try {
            context.getSystemService(MediaSessionManager::class.java)
                ?.getActiveSessions(ComponentName(context, NotifyHandler::class.java))
            true
        } catch (e: SecurityException) {
            false
        }

        /** Identifies what is playing right now: title + artist + playing/paused. */
        private fun fingerprint(controller: MediaController): String {
            val m = controller.metadata
            val title = m?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
            val artist = m?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
            return "$title|$artist"
        }

        /** Polls YouTube Music's session until a different track is playing, or timeout. */
        private suspend fun waitForChange(context: Context, before: String?, timeoutMs: Long): Boolean {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                delay(300)
                val session = findYouTubeMusicSession(context) ?: continue
                val now = fingerprint(session)
                val playing = session.playbackState?.state.let {
                    it == PlaybackState.STATE_PLAYING || it == PlaybackState.STATE_BUFFERING ||
                            it == PlaybackState.STATE_CONNECTING
                }
                if (playing && now != "|" && now != before) {
                    Log.i(TAG, "track changed '$before' -> '$now'")
                    return true
                }
            }
            return false
        }

        fun cleanQuery(raw: String?): String {
            var q = raw?.trim()?.lowercase() ?: return ""
            q = q.replace(APP_SUFFIX, "")
            q = q.replace(LEADING_FILLER, "")
            q = q.replace(TRAILING_FILLER, "")
            return q.trim()
        }

        private fun isInstalled(context: Context): Boolean = try {
            context.packageManager.getPackageInfo(YT_MUSIC_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }

        private fun findYouTubeMusicSession(context: Context): MediaController? {
            val manager = context.getSystemService(MediaSessionManager::class.java)
                ?: return null
            return try {
                manager.getActiveSessions(ComponentName(context, NotifyHandler::class.java))
                    .firstOrNull { it.packageName == YT_MUSIC_PACKAGE }
            } catch (e: SecurityException) {
                Log.w(TAG, "No notification listener access, cannot see media sessions", e)
                null
            }
        }

        private fun startPlayFromSearchIntent(context: Context, query: String): Boolean {
            val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
                .setPackage(YT_MUSIC_PACKAGE)
                .putExtra(SearchManager.QUERY, query)
                .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return tryStart(context, intent)
        }

        private fun openSearchPage(context: Context, query: String): Boolean {
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://music.youtube.com/search?q=" + Uri.encode(query))
            ).setPackage(YT_MUSIC_PACKAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return tryStart(context, intent)
        }

        private fun tryStart(context: Context, intent: Intent): Boolean = try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No activity for $intent", e)
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "Not allowed to start $intent", e)
            false
        }
    }
}
