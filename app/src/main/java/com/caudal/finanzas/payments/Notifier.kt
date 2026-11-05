package com.caudal.finanzas.payments

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.caudal.finanzas.MainActivity
import com.caudal.finanzas.R
import com.caudal.finanzas.data.FinanceDatabase
import com.caudal.finanzas.data.Prefs
import com.caudal.finanzas.domain.LimitAlert
import com.caudal.finanzas.domain.LimitAlerts
import com.caudal.finanzas.domain.LimitSnapshot
import kotlinx.coroutines.flow.first
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Currency
import java.util.Locale

/** Notificaciones del móvil cuando te acercas o te pasas de un límite. Todo local: sin servidores. */
object Notifier {
    private const val CHANNEL = "limites"

    private fun channel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Avisos de límite", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Cuando te acercas o te pasas de lo que puedes gastar"
            })
        }
    }

    fun canNotify(context: Context) =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(context: Context, alert: LimitAlert) {
        if (!canNotify(context)) return
        channel(context)
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(alert.title)
            .setContentText(alert.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try { NotificationManagerCompat.from(context).notify(alert.key.hashCode(), n) } catch (_: SecurityException) {}
    }

    /** Tras un pago con tarjeta: comprueba los límites y avisa una vez al día por cada uno. */
    suspend fun checkAfterPayment(context: Context) {
        val prefs = Prefs.get(context)
        val snap = LimitSnapshot.decode(prefs.settings.value.limitSnapshot) ?: return
        val dao = FinanceDatabase.get(context).dao()
        val savingIds = dao.categories().first().filter { it.isSavings }.map { it.id }.toSet()
        val fmt = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-ES")).apply {
            currency = Currency.getInstance(prefs.settings.value.currency)
        }
        val today = LocalDate.now()
        LimitAlerts.check(today, dao.allExpenses().first(), dao.bills().first(), savingIds, snap) { fmt.format(it) }
            .filter { !prefs.wasAlerted(it.key, today) }
            .forEach { show(context, it); prefs.markAlerted(it.key, today) }
    }
}
