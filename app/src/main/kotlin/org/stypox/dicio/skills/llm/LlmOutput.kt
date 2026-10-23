package org.stypox.dicio.skills.llm

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import org.dicio.skill.context.SkillContext
import org.stypox.dicio.R
import org.stypox.dicio.io.graphical.HeadlineSpeechSkillOutput
import org.stypox.dicio.util.getString
import org.dicio.skill.skill.SkillOutput

sealed interface LlmOutput : SkillOutput {
    data class Answer(val question: String, val answer: String) : LlmOutput {
        override fun getSpeechOutput(ctx: SkillContext): String = answer

        @Composable
        override fun GraphicalOutput(ctx: SkillContext) {
            Column {
                Text(text = question, style = MaterialTheme.typography.labelLarge)
                Text(text = answer, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }

    data object NoQuestion : LlmOutput, HeadlineSpeechSkillOutput {
        override fun getSpeechOutput(ctx: SkillContext): String =
            ctx.getString(R.string.skill_llm_no_question)
    }

    data object NoApiKey : LlmOutput, HeadlineSpeechSkillOutput {
        override fun getSpeechOutput(ctx: SkillContext): String =
            ctx.getString(R.string.skill_llm_no_api_key)
    }

    data class Error(val message: String) : LlmOutput, HeadlineSpeechSkillOutput {
        override fun getSpeechOutput(ctx: SkillContext): String =
            ctx.getString(R.string.skill_llm_error, message)
    }
}
