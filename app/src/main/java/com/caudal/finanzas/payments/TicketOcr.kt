package com.caudal.finanzas.payments

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * Saca el texto de la foto de un ticket con el lector de texto de Google que va dentro de la app
 * (gratis, sin internet: la foto no sale del móvil). Lo que pone el ticket lo interpretan reglas fijas ([com.caudal.finanzas.domain.SharedReader]).
 */
object TicketOcr {
    fun read(context: Context, uri: Uri, done: (String?) -> Unit) {
        val image = runCatching { InputImage.fromFilePath(context, uri) }.getOrElse { done(null); return }
        val reader = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        reader.process(image)
            .addOnSuccessListener { done(it.text.takeIf(String::isNotBlank)) }
            .addOnFailureListener { done(null) }
            .addOnCompleteListener { reader.close() }
    }
}
