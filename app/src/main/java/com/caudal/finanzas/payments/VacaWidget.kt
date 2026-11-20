package com.caudal.finanzas.payments

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.caudal.finanzas.MainActivity
import com.caudal.finanzas.R
import com.caudal.finanzas.data.AccountType
import com.caudal.finanzas.data.Prefs
import com.caudal.finanzas.domain.BillReminders
import com.caudal.finanzas.domain.CashPlanner
import com.caudal.finanzas.domain.TodayLeft
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Widget de la pantalla de inicio: "Hoy te quedan X", lo que hay en el banco y el próximo cobro.
 * Se actualiza al salir de la app y con la alarma de cada hora. Respeta "Ocultar importes".
 */
class VacaWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { refresh(context.applicationContext) } finally { pending.finish() }
        }
    }

    companion object {
        const val ACTION_ADD = "com.caudal.finanzas.ADD"

        suspend fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, VacaWidget::class.java))
            if (ids.isEmpty()) return
            val settings = Prefs.get(context).settings.value
            val d = BackgroundData.load(context)
            val fmt = BackgroundData.money(context)
            fun m(v: Double) = if (settings.hideAmounts) "••• €" else fmt(v)

            val views = RemoteViews(context.packageName, R.layout.widget_vaca)
            val today = TodayLeft.of(d, CashPlanner.of(d, settings.bankBuffer))
            if (today == null) {
                views.setTextViewText(R.id.widget_label, "Ahorra, vaca")
                views.setTextViewText(R.id.widget_left, "Abre la app")
            } else {
                val (_, left) = today
                views.setTextViewText(R.id.widget_label, if (left >= 0) "Hoy te quedan" else "Hoy te has pasado")
                views.setTextViewText(R.id.widget_left, m(kotlin.math.abs(left)))
            }
            val bank = d.accounts.filter { it.type == AccountType.BANK }.sumOf { it.balance }
            views.setTextViewText(R.id.widget_bank, "En el banco ${m(bank)}")
            val next = BillReminders.upcoming(d).firstOrNull { it.daysFrom(d.today) in 0..7 && (it.bill.trialEnds == null || it.bill.trialEnds < d.today.toEpochDay()) }
            if (next != null) {
                val whenTxt = when (next.daysFrom(d.today)) { 0 -> "Hoy"; 1 -> "Mañana"; else -> "En ${next.daysFrom(d.today)} días" }
                views.setTextViewText(R.id.widget_next, "$whenTxt: ${next.bill.name} · ${m(next.bill.amount)}")
                views.setViewVisibility(R.id.widget_next, View.VISIBLE)
            } else views.setViewVisibility(R.id.widget_next, View.GONE)

            val open = PendingIntent.getActivity(context, 10, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val add = PendingIntent.getActivity(
                context, 11, Intent(context, MainActivity::class.java).setAction(ACTION_ADD),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)
            views.setOnClickPendingIntent(R.id.widget_add, add)
            manager.updateAppWidget(ids, views)
        }
    }
}
