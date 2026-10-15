package com.verto.ui.common

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/** Shows the digits of a time field as 00:00; the field itself holds only the digits. */
object ClockMask : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val shown = if (digits.length > 2) "${digits.take(2)}:${digits.drop(2)}" else digits
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = if (offset > 2) offset + 1 else offset

            override fun transformedToOriginal(offset: Int): Int = if (offset > 2) offset - 1 else offset
        }
        return TransformedText(AnnotatedString(shown), mapping)
    }
}
