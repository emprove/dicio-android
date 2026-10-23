package org.stypox.dicio.skills.volume

import android.media.AudioManager
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.standard.StandardRecognizerData
import org.dicio.skill.standard.StandardRecognizerSkill
import org.stypox.dicio.sentences.Sentences.Volume
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Media (music) volume: "громче", "тише", "громкость на семь", "громкость 40 процентов",
 * "на всю катушку", "выключи звук". Works from the background, no permission needed.
 */
class VolumeSkill(correspondingSkillInfo: SkillInfo, data: StandardRecognizerData<Volume>)
    : StandardRecognizerSkill<Volume>(correspondingSkillInfo, data) {

    override suspend fun generateOutput(ctx: SkillContext, inputData: Volume): SkillOutput {
        val audio = ctx.android.getSystemService(AudioManager::class.java)
            ?: return VolumeOutput.Failed
        val stream = AudioManager.STREAM_MUSIC
        val max = audio.getStreamMaxVolume(stream)
        val current = audio.getStreamVolume(stream)
        val step = ceil(max * STEP_FRACTION).toInt().coerceAtLeast(1)
        val flags = AudioManager.FLAG_SHOW_UI

        when (inputData) {
            is Volume.Up -> {
                unmute(audio, stream, flags)
                audio.setStreamVolume(stream, (current + step).coerceAtMost(max), flags)
            }
            is Volume.Down ->
                audio.setStreamVolume(stream, (current - step).coerceAtLeast(0), flags)
            is Volume.Max -> {
                unmute(audio, stream, flags)
                audio.setStreamVolume(stream, max, flags)
            }
            is Volume.Mute -> {
                audio.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, flags)
                return VolumeOutput.Muted
            }
            is Volume.Unmute ->
                unmute(audio, stream, flags)
            is Volume.SetLevel -> {
                val percent = parseLevelPercent(inputData.level)
                    ?: return VolumeOutput.NotUnderstood(inputData.level.orEmpty())
                unmute(audio, stream, flags)
                audio.setStreamVolume(stream, (max * percent / 100.0).roundToInt(), flags)
            }
        }

        val now = audio.getStreamVolume(stream)
        return VolumeOutput.Level((now * 100.0 / max).roundToInt())
    }

    private fun unmute(audio: AudioManager, stream: Int, flags: Int) {
        if (audio.isStreamMute(stream)) {
            audio.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, flags)
        }
    }

    companion object {
        /** "громче"/"тише" change the volume by ~10% of the range. */
        const val STEP_FRACTION = 0.1

        private val UNITS = mapOf(
            "ноль" to 0, "нуль" to 0, "один" to 1, "одна" to 1, "одну" to 1, "два" to 2,
            "две" to 2, "три" to 3, "четыре" to 4, "пять" to 5, "шесть" to 6, "семь" to 7,
            "восемь" to 8, "девять" to 9, "десять" to 10, "одиннадцать" to 11,
            "двенадцать" to 12, "тринадцать" to 13, "четырнадцать" to 14, "пятнадцать" to 15,
            "шестнадцать" to 16, "семнадцать" to 17, "восемнадцать" to 18,
            "девятнадцать" to 19,
        )
        private val TENS = mapOf(
            "двадцать" to 20, "тридцать" to 30, "сорок" to 40, "пятьдесят" to 50,
            "шестьдесят" to 60, "семьдесят" to 70, "восемьдесят" to 80, "девяносто" to 90,
            "сто" to 100,
        )
        private val SPECIAL = mapOf(
            "половину" to 50, "половина" to 50, "середину" to 50, "максимум" to 100,
            "полную" to 100, "минимум" to 0,
        )

        /**
         * Accepts digits ("7", "40%") or Russian number words ("семь", "сорок пять").
         * Values up to 10 are a 0..10 scale ("громкость на семь" = 70%), unless "процент"
         * is said; bigger values are percent.
         */
        fun parseLevelPercent(raw: String?): Int? {
            val text = raw?.lowercase()?.trim() ?: return null
            val isPercent = text.contains("процент") || text.contains("%")
            SPECIAL.entries.firstOrNull { text.contains(it.key) }?.let { return it.value }

            val number = Regex("\\d+").find(text)?.value?.toIntOrNull() ?: run {
                var sum = 0
                var found = false
                for (word in text.split(Regex("[^\\p{L}]+"))) {
                    (TENS[word] ?: UNITS[word])?.let { sum += it; found = true }
                }
                if (found) sum else null
            } ?: return null

            val percent = if (!isPercent && number <= 10) number * 10 else number
            return percent.coerceIn(0, 100)
        }
    }
}
