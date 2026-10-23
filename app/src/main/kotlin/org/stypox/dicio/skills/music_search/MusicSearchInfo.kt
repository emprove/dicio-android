package org.stypox.dicio.skills.music_search

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.Permission
import org.dicio.skill.skill.Skill
import org.dicio.skill.skill.SkillInfo
import org.stypox.dicio.R
import org.stypox.dicio.sentences.Sentences
import org.stypox.dicio.util.PERMISSION_NOTIFICATION_LISTENER
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.stypox.dicio.settings.ui.StringSetting

object MusicSearchInfo : SkillInfo("music_search") {
    private const val PREFS = "skill_settings_music_search"
    private const val KEY_YOUTUBE_API_KEY = "youtube_api_key"

    fun youtubeApiKey(context: Context): String =
        prefs(context).getString(KEY_YOUTUBE_API_KEY, "")!!.trim()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override val renderSettings: @Composable () -> Unit get() = @Composable {
        val context = LocalContext.current
        var apiKey by remember { mutableStateOf(youtubeApiKey(context)) }
        StringSetting(
            title = stringResource(R.string.pref_music_search_youtube_api_key),
            descriptionWhenEmpty = stringResource(R.string.pref_music_search_youtube_api_key_empty),
        ).Render(
            // never show the key itself on screen
            value = if (apiKey.isEmpty()) "" else "••••" + apiKey.takeLast(4),
            onValueChange = { newValue ->
                if (!newValue.startsWith("••••")) {
                    apiKey = newValue.trim()
                    prefs(context).edit().putString(KEY_YOUTUBE_API_KEY, apiKey).apply()
                }
            },
        )
    }

    override fun name(context: Context) =
        context.getString(R.string.skill_name_music_search)

    override fun sentenceExample(context: Context) =
        context.getString(R.string.skill_sentence_example_music_search)

    @Composable
    override fun icon() =
        rememberVectorPainter(Icons.Default.LibraryMusic)

    // needed to talk directly to YouTube Music's media session (no app launch required,
    // which is what makes it work on the lock screen and with screen pinning)
    override val neededPermissions: List<Permission>
            = listOf(PERMISSION_NOTIFICATION_LISTENER)

    override fun build(ctx: SkillContext): Skill<*>? {
        val data = Sentences.MusicSearch[ctx.sentencesLanguage] ?: return null
        return MusicSearchSkill(MusicSearchInfo, data)
    }
}
