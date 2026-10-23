package org.stypox.dicio.skills.music_search

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillOutput
import org.stypox.dicio.R
import org.stypox.dicio.io.graphical.Headline
import org.stypox.dicio.util.getString

sealed interface MusicSearchOutput : SkillOutput {
    /** Diagnostics are shown on screen (small text) but never spoken. */
    val details: String get() = ""

    @Composable
    override fun GraphicalOutput(ctx: SkillContext) {
        Column {
            Headline(text = getSpeechOutput(ctx))
            if (details.isNotEmpty()) {
                Text(text = details, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    data class Playing(
        val query: String,
        val method: String = "",
        override val details: String = "",
    ) : MusicSearchOutput {
        override fun getSpeechOutput(ctx: SkillContext): String =
            ctx.getString(R.string.skill_music_search_playing, query)

        @Composable
        override fun GraphicalOutput(ctx: SkillContext) {
            Column {
                Headline(text = getSpeechOutput(ctx))
                if (method.isNotEmpty()) {
                    Text(text = "Способ: $method", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    data object Resuming : MusicSearchOutput {
        override fun getSpeechOutput(ctx: SkillContext): String =
            ctx.getString(R.string.skill_music_search_resuming)
    }

    data object Paused : MusicSearchOutput {
        override fun getSpeechOutput(ctx: SkillContext): String =
            ctx.getString(R.string.skill_media_pausing)
    }

    data object NotInstalled : MusicSearchOutput {
        override fun getSpeechOutput(ctx: SkillContext): String =
            ctx.getString(R.string.skill_music_search_not_installed)
    }

    data class Failed(val query: String, override val details: String = "") : MusicSearchOutput {
        override fun getSpeechOutput(ctx: SkillContext): String =
            ctx.getString(R.string.skill_music_search_failed, query)
    }
}
