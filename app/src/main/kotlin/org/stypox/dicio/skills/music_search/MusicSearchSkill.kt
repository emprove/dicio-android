package org.stypox.dicio.skills.music_search

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
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
         */
        fun playOnYouTubeMusic(context: Context, raw: String?): MusicSearchOutput {
            val query = cleanQuery(raw)

            if (!isInstalled(context)) {
                return MusicSearchOutput.NotInstalled
            }

            val session = findYouTubeMusicSession(context)

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
                return if (startPlayFromSearchIntent(context, ""))
                    MusicSearchOutput.Resuming else MusicSearchOutput.Failed(raw.orEmpty())
            }

            if (session != null && supportsPlayFromSearch(session)) {
                session.transportControls.playFromSearch(query, Bundle())
                return MusicSearchOutput.Playing(query)
            }

            if (startPlayFromSearchIntent(context, query)) {
                return MusicSearchOutput.Playing(query)
            }

            return if (openSearchPage(context, query))
                MusicSearchOutput.Playing(query) else MusicSearchOutput.Failed(query)
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

        private fun supportsPlayFromSearch(controller: MediaController): Boolean {
            val actions = controller.playbackState?.actions ?: return true // unknown -> try
            return (actions and PlaybackState.ACTION_PLAY_FROM_SEARCH) != 0L
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
