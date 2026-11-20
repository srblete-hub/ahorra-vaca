package com.caudal.finanzas.payments

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.caudal.finanzas.MainActivity
import com.caudal.finanzas.R
import com.caudal.finanzas.data.FinanceDatabase
import com.caudal.finanzas.data.Prefs
import com.caudal.finanzas.domain.BillReminders
import com.caudal.finanzas.domain.FinanceData
import kotlinx.coroutines.flow.first
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalTime
import java.util.Currency
import java.util.Locale

/**
 * Avisos de recibos y suscripciones, y el resumen de la noche: unos días antes de cada cobro, cuando se acaba una prueba gratis
 * y cuando en el banco queda poco para lo que te van a cobrar. Lo despierta la misma alarma que las cripto
 * (más o menos cada hora) y solo avisa de 9:00 a 23:00. Cada aviso sale una sola vez.
 */
object BillWatch {
    private const val CHANNEL = "recibos"
    private const val NIGHT_CHANNEL = "resumen_noche"
    private const val STORE = "bill_watch"
    private const val DONE = "done"

    suspend fun check(context: Context, now: LocalTime = LocalTime.now()) {
        if (now.hour !in 9..22 || !Notifier.canNotify(context)) return
        val settings = Prefs.get(context).settings.value
        val d = BackgroundData.load(context)
        val today = d.today
        val store = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        // "clave|día en que se avisó"; se olvidan a los 60 días.
        val saved = (store.getStringSet(DONE, emptySet()) ?: emptySet())
            .filter { (it.substringAfterLast('|').toLongOrNull() ?: 0) >= today.toEpochDay() - 60 }.toSet()
        val done = saved.flatMap { it.substringBeforeLast('|').split(',') }.toSet()

        val fmt = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-ES")).apply { currency = Currency.getInstance(settings.currency) }
        val reminders = BillReminders.due(d, settings.billRemindDays, done) { fmt.format(it) }.toMutableList()
        // Resumen de la noche: a partir de las 21:00, una vez al día.
        if (settings.nightSummary && now.hour >= 21) {
            val night = com.caudal.finanzas.domain.NightSummary.of(d, BackgroundData.perDay(context, d)) { fmt.format(it) }
            if (night.key !in done) reminders += night
        }
        store.edit().putStringSet(DONE, saved + reminders.map { "${it.key}|${today.toEpochDay()}" }).apply()
        if (reminders.isEmpty()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Recibos y suscripciones", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Antes de cada cobro, al acabar una prueba gratis y si te queda poco en el banco"
                },
            )
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(NIGHT_CHANNEL, "Resumen de la noche", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "A las 21:00, lo que has gastado hoy y lo que te cobran mañana"
                },
            )
        }
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        reminders.forEach { r ->
            val n = NotificationCompat.Builder(context, if (r.key.startsWith("night-")) NIGHT_CHANNEL else CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(r.title).setContentText(r.text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(r.text))
                .setContentIntent(open).setAutoCancel(true).build()
            try { NotificationManagerCompat.from(context).notify(r.key.hashCode(), n) } catch (_: SecurityException) {}
        }
    }
}
