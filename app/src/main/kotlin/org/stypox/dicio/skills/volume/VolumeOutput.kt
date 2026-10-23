package org.stypox.dicio.skills.volume

import org.dicio.skill.context.SkillContext
import org.stypox.dicio.R
import org.stypox.dicio.io.graphical.HeadlineSpeechSkillOutput
import org.stypox.dicio.util.getString

sealed interface VolumeOutput : HeadlineSpeechSkillOutput {
    data class Level(val percent: Int) : VolumeOutput {
        override fun getSpeechOutput(ctx: SkillContext): String =
            ctx.getString(R.string.skill_volume_level, percent)
    }

    data object Muted : VolumeOutput {
        override fun getSpeechOutput(ctx: SkillContext): String =
            ctx.getString(R.string.skill_volume_muted)
    }

    data class NotUnderstood(val level: String) : VolumeOutput {
        override fun getSpeechOutput(ctx: SkillContext): String =
            ctx.getString(R.string.skill_volume_not_understood, level)
    }

    data object Failed : VolumeOutput {
        override fun getSpeechOutput(ctx: SkillContext): String =
            ctx.getString(R.string.skill_volume_failed)
    }
}
