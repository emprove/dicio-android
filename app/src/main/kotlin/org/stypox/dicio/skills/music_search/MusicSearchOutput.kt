package org.stypox.dicio.skills.music_search

import org.dicio.skill.context.SkillContext
import org.stypox.dicio.R
import org.stypox.dicio.io.graphical.HeadlineSpeechSkillOutput
import org.stypox.dicio.util.getString

sealed interface MusicSearchOutput : HeadlineSpeechSkillOutput {
    data class Playing(val query: String) : MusicSearchOutput {
        override fun getSpeechOutput(ctx: SkillContext): String =
            ctx.getString(R.string.skill_music_search_playing, query)
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

    data class Failed(val query: String) : MusicSearchOutput {
        override fun getSpeechOutput(ctx: SkillContext): String =
            ctx.getString(R.string.skill_music_search_failed, query)
    }
}
