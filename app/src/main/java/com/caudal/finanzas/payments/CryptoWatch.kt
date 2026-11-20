package com.caudal.finanzas.payments

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.caudal.finanzas.MainActivity
import com.caudal.finanzas.R
import com.caudal.finanzas.data.FinanceDatabase
import com.caudal.finanzas.data.PriceService
import com.caudal.finanzas.data.Prefs
import com.caudal.finanzas.domain.CryptoMoves
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlin.math.abs

/**
 * Vigila cada hora (más o menos, lo decide Android para ahorrar batería) el precio de tus cripto con CoinGecko (gratis).
 * Si una sube o baja un 5 % o más desde el último aviso, actualiza lo que vale lo tuyo y te avisa.
 */
object CryptoWatch {
    private const val CHANNEL = "cripto"
    private const val STORE = "crypto_watch"
    private const val REFS = "refs"

    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(context, 7, Intent(context, CryptoAlarmReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_HOUR, AlarmManager.INTERVAL_HOUR, pi)
    }

    suspend fun check(context: Context) {
        val dao = FinanceDatabase.get(context).dao()
        val holdings = dao.crypto().first().filter { it.amount > 0 }
        if (holdings.isEmpty()) return
        val currency = Prefs.get(context).settings.value.currency
        val quotes = runCatching { PriceService.cryptoQuotes(holdings.map { it.coinId }.distinct(), currency) }.getOrNull() ?: return
        val now = System.currentTimeMillis()
        // El dinero que tienes, siempre al día.
        holdings.forEach { h -> quotes[h.coinId]?.let { (price, _) -> dao.upsertCrypto(h.copy(price = price, updatedAt = now)) } }

        val store = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        val (moves, refs) = CryptoMoves.check(holdings, quotes.mapValues { it.value.first }, CryptoMoves.decode(store.getString(REFS, null)))
        store.edit().putString(REFS, CryptoMoves.encode(refs)).apply()
        if (moves.isEmpty() || !Notifier.canNotify(context)) return

        val fmt = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-ES")).apply { this.currency = Currency.getInstance(currency) }
        val es = Locale.forLanguageTag("es-ES")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Movimientos de tus cripto", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Cuando una cripto que tienes sube o baja un 5 % o más"
                },
            )
        }
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        moves.forEach { m ->
            val up = m.change > 0
            val title = "${m.name} ${if (up) "sube" else "baja"} un ${String.format(es, "%.1f", abs(m.change) * 100)} %"
            val text = "Lo tuyo en ${m.symbol} vale ahora ${fmt.format(m.value)} (${if (up) "+" else "−"}${fmt.format(abs(m.delta))} desde el último aviso). " +
                "1 ${m.symbol} = ${fmt.format(m.price)}."
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title).setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open).setAutoCancel(true).build()
            try { NotificationManagerCompat.from(context).notify("cripto-${m.symbol}".hashCode(), n) } catch (_: SecurityException) {}
        }
    }
}

/** Lo despierta la alarma (cada hora) y al encender el móvil vuelve a programarla. */
class CryptoAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) { CryptoWatch.schedule(context); return }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runCatching { CryptoWatch.check(context.applicationContext) }
                runCatching { BillCharger.chargeDue(context.applicationContext) }
                runCatching { BillWatch.check(context.applicationContext) }
                runCatching { VacaWidget.refresh(context.applicationContext) }
            } finally { pending.finish() }
        }
    }
}
