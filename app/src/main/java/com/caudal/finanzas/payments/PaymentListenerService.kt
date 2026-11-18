package com.caudal.finanzas.payments

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.caudal.finanzas.data.Expense
import com.caudal.finanzas.domain.Classifier
import com.caudal.finanzas.data.FinanceDatabase
import com.caudal.finanzas.data.Prefs
import com.caudal.finanzas.data.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Lee las notificaciones de Google Wallet y de las apps de bancos. Cuando una es un pago con
 * tarjeta, lo apunta como gasto y lo descuenta de la cuenta elegida. Nada sale del móvil.
 */
class PaymentListenerService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val settings = Prefs.get(this).settings.value
        if (!settings.cardPayments || sbn.packageName == packageName) return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val pkg = sbn.packageName
        val wallet = PaymentParser.WALLET_APPS[pkg]
        val appName = wallet ?: PaymentParser.BANK_APPS[pkg] ?: if (settings.cardAnyApp) pkg else return

        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = (n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: n.extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()
        // Bizum (recibido o enviado) desde el aviso del banco, sin conectar el banco.
        if (wallet == null) BizumParser.parse(title, text)?.let { b ->
            if (PaymentParser.signature(b.raw) in settings.ignoredPayments) return
            scope.launch { PaymentRecorder.recordBizum(applicationContext, b, appName) }
            return
        }
        val payment = PaymentParser.parse(title, text, isWallet = wallet != null) ?: return
        // Avisos que marcaste como "no era un pago": no se vuelven a apuntar.
        if (PaymentParser.signature(payment.raw) in settings.ignoredPayments) return

        scope.launch { PaymentRecorder.record(applicationContext, payment, appName) }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun hasAccess(context: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }
}

object PaymentRecorder {
    /** Un Bizum: si lo recibes, ingreso en la cuenta de la tarjeta; si lo envías, gasto con la categoría del concepto. */
    suspend fun recordBizum(context: Context, b: Bizum, appName: String, now: Long = System.currentTimeMillis()) {
        val settings = Prefs.get(context).settings.value
        val dao = FinanceDatabase.get(context).dao()
        val accountId = settings.cardAccountId.takeIf { id -> id != Settings.NONE && dao.accounts().first().any { it.id == id } }
        val who = b.person ?: "alguien"
        val note = (if (b.incoming) "Bizum de $who" else "Bizum a $who") + (b.concept?.let { " · $it" } ?: "")
        if (b.incoming) {
            // Mismo Bizum avisado dos veces en un minuto: solo uno.
            if (dao.incomes().first().any { kotlin.math.abs(it.amount - b.amount) < 0.005 && it.note == note && now - it.createdAt < 60_000 }) return
            dao.saveIncome(com.caudal.finanzas.data.Income(sourceId = null, amount = b.amount, note = note, epochDay = LocalDate.now().toEpochDay(), accountId = accountId, createdAt = now))
            return
        }
        if (dao.recentCardPayments(b.amount, now - 60_000) > 0) return
        val categories = dao.categories().first()
        val tag = b.concept?.let { Classifier.guess(it) } ?: Classifier.classify(note, dao.allExpenses().first())
        val categoryId = Classifier.bucketFor(tag, categories) ?: categories.firstOrNull { !it.isSavings }?.id ?: return
        dao.saveExpense(
            Expense(
                categoryId = categoryId, amount = b.amount, note = note, epochDay = LocalDate.now().toEpochDay(), accountId = accountId,
                fromCard = true, sourceApp = "$appName · Bizum", createdAt = now, tag = tag.name, rawText = b.raw.take(300),
            ),
        )
        Notifier.checkAfterPayment(context, lastNote = note, lastAmount = b.amount, lastTag = tag)
    }

    private const val DUPLICATE_WINDOW_MS = 3 * 60_000L

    /** Devuelve el gasto apuntado, o null si se descartó (otra moneda o repetido). */
    suspend fun record(context: Context, payment: DetectedPayment, appName: String, now: Long = System.currentTimeMillis()): Expense? {
        val settings = Prefs.get(context).settings.value
        if (!payment.currency.equals(settings.currency, ignoreCase = true)) return null
        val dao = FinanceDatabase.get(context).dao()
        if (dao.recentCardPayments(payment.amount, now - DUPLICATE_WINDOW_MS) > 0) return null

        val categories = dao.categories().first()
        // Se clasifica solo: por cómo clasificaste ese comercio antes o por palabras clave.
        val note = payment.merchant ?: "Pago con tarjeta"
        val tag = Classifier.classify(note, dao.allExpenses().first())
        val categoryId = Classifier.bucketFor(tag, categories)
            ?: categories.firstOrNull { it.id == settings.cardCategoryId }?.id
            ?: categories.firstOrNull { !it.isSavings }?.id
            ?: categories.firstOrNull()?.id
            ?: return null
        val accountId = settings.cardAccountId.takeIf { id -> id != Settings.NONE && dao.accounts().first().any { it.id == id } }

        val expense = Expense(
            categoryId = categoryId,
            amount = payment.amount,
            note = note,
            epochDay = LocalDate.now().toEpochDay(),
            accountId = accountId,
            fromCard = true,
            sourceApp = appName,
            createdAt = now,
            tag = tag.name,
            rawText = payment.raw.take(300),
        )
        dao.saveExpense(expense)
        Notifier.checkAfterPayment(context, lastNote = com.caudal.finanzas.domain.Merchants.clean(note), lastAmount = payment.amount, lastTag = tag)
        return expense
    }
}
