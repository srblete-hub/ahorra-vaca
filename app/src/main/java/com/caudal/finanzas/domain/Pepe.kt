package com.caudal.finanzas.domain

import java.time.YearMonth

/**
 * "Pregúntale a Pepe": Pepe es un pastor que ayuda con las cuentas. La app NO tiene IA: solo prepara un resumen
 * de tus números y tu pregunta, y lo abre en la app oficial de Claude, que contesta con tu suscripción (gratis).
 * Antes de enviar, ves y puedes editar todo el texto.
 */
object Pepe {
    val QUESTIONS = listOf("¿Cuánto me queda hoy?", "¿Cómo voy este mes?", "¿En qué puedo recortar?", "¿Llego a fin de mes?", "¿Cuánto debería ahorrar?", "¿Cómo va mi cartera hoy?")

    fun brief(
        question: String, userName: String, d: FinanceData, plan: BankPlan?, budget: SpendingBudget?, goal: GoalStatus?,
        expected: List<String>, fmt: (Double) -> String,
    ): String {
        val m = YearMonth.from(d.today)
        val es = java.util.Locale.forLanguageTag("es-ES")
        val monthName = m.month.getDisplayName(java.time.format.TextStyle.FULL, es)
        val name = userName.ifBlank { "mí" }
        val bank = d.accounts.filter { it.type == com.caudal.finanzas.data.AccountType.BANK }
        val savings = d.accounts.filter { it.isSaving() && it.balance > 0 }
        val spending = d.spendingIn(m)
        val variable = spending.filter { !Budgeting.isFixed(it, d.bills) }
        val fixed = spending.filter { Budgeting.isFixed(it, d.bills) }.sumOf { it.amount }
        val byTag = variable.groupBy { it.spendTag() }.map { (t, l) -> t to l.sumOf { it.amount } }.sortedByDescending { it.second }.take(6)

        val lines = mutableListOf<String>()
        lines += "- En el banco (mi dinero para el día a día): ${fmt(bank.sumOf { it.balance })}" +
            if (bank.isNotEmpty()) " (${bank.joinToString(", ") { "${it.name} ${fmt(it.balance)}" }})" else ""
        if (savings.isNotEmpty()) lines += "- Ahorro (no lo toco): ${savings.joinToString(", ") { a -> "${a.name} ${fmt(a.balance)}" + if (a.interestRate > 0) " al ${String.format(es, "%.1f", a.interestRate)} % TAE" else "" }}"
        if (d.wealth.invested > 0) lines += "- Inversiones (cripto y acciones): ${fmt(d.wealth.invested)}"
        val holdings = d.crypto.map { Triple(it.name, it.symbol, it.amount * it.price) to (it.amount * it.avgBuyPrice) } +
            d.stocks.map { Triple(it.name.ifBlank { it.ticker }, it.ticker, it.shares * it.price) to (it.shares * it.avgBuyPrice) }
        if (holdings.isNotEmpty()) lines += "- Mi cartera: " + holdings.sortedByDescending { it.first.third }.take(8).joinToString(", ") { (h, cost) ->
            "${h.first} (${h.second}) ${fmt(h.third)}" + if (cost > 0) " (${if (h.third >= cost) "+" else "−"}${fmt(kotlin.math.abs(h.third - cost))} desde que compré)" else ""
        }
        lines += "- Este mes ha entrado: ${fmt(d.receivedIn(m))}" + if (expected.isNotEmpty()) ". Por llegar: ${expected.joinToString(", ")}" else ""
        lines += "- Gastado en $monthName (día a día, sin alquiler ni fijos): ${fmt(variable.sumOf { it.amount })}" + if (fixed > 0) ". Alquiler y fijos: ${fmt(fixed)}" else ""
        if (byTag.isNotEmpty()) lines += "- Por categorías: " + byTag.joinToString(", ") { (t, v) ->
            val lim = budget?.items?.firstOrNull { it.tag == t }?.monthly
            "${t.label} ${fmt(v)}" + (lim?.takeIf { it > 0 }?.let { " (límite ${fmt(it)})" } ?: "")
        }
        plan?.let { lines += "- Me tocan unos ${fmt(it.perDay)} al día durante ${it.days} días hasta el próximo cobro" }
        goal?.let { lines += "- Objetivo de ahorro «${it.goal.name.ifBlank { "mi objetivo" }}»: llevo ${fmt(it.current)} de ${fmt(it.goal.target)}, ahorro unos ${fmt(it.pace)} al mes" }
        if (d.bills.isNotEmpty()) lines += "- Gastos fijos: " + d.bills.take(8).joinToString(", ") { "${it.name} ${fmt(it.amount)}" }

        return """Eres Pepe, un pastor de toda la vida, sabio y cercano, que ayuda a $name a cuidar su dinero como cuida su rebaño. Contesta en español, en pocas frases claras y prácticas, con alguna expresión de pastor sin pasarte. Usa solo los datos de abajo y, si te falta algo, pregúntamelo. No me recomiendes comprar ni vender inversiones concretas.

Mis números (día ${d.today.dayOfMonth} de $monthName):
${lines.joinToString("\n")}

Mi pregunta: ${question.trim()}"""
    }
}
