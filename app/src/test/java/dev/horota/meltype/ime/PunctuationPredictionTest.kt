// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PunctuationPredictionTest {
    @Test
    fun offersAQuestionMarkForQuestionEndings() {
        assertEquals("？", PunctuationPrediction.questionMark("明日行きますか", ""))
        assertEquals("？", PunctuationPrediction.questionMark("そうかな", ""))
        assertEquals("？", PunctuationPrediction.questionMark("本当だろう", ""))
        assertEquals("？", PunctuationPrediction.questionMark("昨日は休んだ。今日は行くのか", ""))
    }

    @Test
    fun skipsWhenPunctuationIsAlreadyThereOrTheSentenceContinues() {
        assertNull(PunctuationPrediction.questionMark("行きますか？", ""))
        assertNull(PunctuationPrediction.questionMark("行きますか", "明日"))
        assertNull(PunctuationPrediction.questionMark("どこか", ""))
        assertNull(PunctuationPrediction.questionMark("何か", ""))
        assertNull(PunctuationPrediction.questionMark("今日は晴れ", ""))
    }
}
