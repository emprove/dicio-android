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

object MusicSearchInfo : SkillInfo("music_search") {
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
