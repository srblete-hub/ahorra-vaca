package com.caudal.finanzas.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import kotlin.math.max
import kotlin.math.min

/** Zonas de color, como la recuperación de WHOOP: verde 67–100, amarillo 34–66, rojo 0–33. */
enum class Zone { GREEN, YELLOW, RED }

fun zoneOf(pct: Double): Zone = when { pct >= 67 -> Zone.GREEN; pct >= 34 -> Zone.YELLOW; else -> Zone.RED }

/** Un día: tu margen ese día (%) y lo que gastaste. */
data class DayPulse(val date: LocalDate, val margin: Double, val spent: Double)

/**
 * El "pulso" de tu dinero. Todo se compara con lo que tocaba *hasta hoy*, así el color tiene
 * sentido cualquier día del mes o de la semana:
 * - Colchón: tu ahorro frente a 3 meses de tu coste de vida.
 * - Margen: lo gastado este mes frente a lo que tocaba a estas alturas.
 * - Gasto de la semana: lo de desde el lunes frente a lo que tocaba hasta hoy.
 */
data class Pulse(
    val cushion: Double,
    val margin: Double,
    val weekSpent: Double,
    val weekOptimal: Double,
    /** Lo óptimo de la semana hasta hoy (lunes = 1/7, domingo = toda). */
    val weekOptimalToDate: Double,
    val todayAllowance: Double,
    val healthInRange: Int,
    val healthTotal: Int,
    /** Presión de gasto: lo gastado este mes frente a lo que tocaba a estas alturas (1 = justo). */
    val pressure: Double,
    val days: List<DayPulse>,
    val statement: String,
    val tip: String,
    val monthBudget: Double = 0.0,
    val spentMonth: Double = 0.0,
    val expectedSoFar: Double = 0.0,
    val savings: Double = 0.0,
    val monthlyCost: Double = 0.0,
    val health: Insights.HealthReport? = null,
    /** Lo gastado hoy (sin recibos). */
    val spentToday: Double = 0.0,
) {
    /** Color del gasto semanal según lo que tocaba hasta hoy. */
    val weekZone get() = when {
        weekOptimalToDate <= 0 -> Zone.GREEN
        weekSpent <= weekOptimalToDate -> Zone.GREEN
        weekSpent <= weekOptimalToDate * 1.3 -> Zone.YELLOW
        else -> Zone.RED
    }
    val pressureZone get() = when { pressure <= 1.0 -> Zone.GREEN; pressure <= 1.3 -> Zone.YELLOW; else -> Zone.RED }
    val pressureLabel get() = when (pressureZone) { Zone.GREEN -> "BAJA"; Zone.YELLOW -> "MEDIA"; Zone.RED -> "ALTA" }
    /** Gasto de la semana en una escala cerrada 0–100 (logarítmica, como el Esfuerzo de WHOOP). */
    val weekScore get() = PulseCalculator.spendScore(weekSpent, weekOptimal)
    /** Marca gris: dónde deberías estar hoy en esa escala. */
    val optimalScore get() = PulseCalculator.spendScore(weekOptimalToDate, weekOptimal)
}

object PulseCalculator {
    /**
     * Escala logarítmica 0–100 para el gasto, inspirada en el Esfuerzo de WHOOP (0–21, cada punto cuesta más):
     * 100 · ln(1 + 3r) / ln(10), con r = gastado / óptimo. Lo óptimo (r = 1) da 60; el doble, 85; el triple o más, 100.
     */
    fun spendScore(spent: Double, optimal: Double): Double {
        if (optimal <= 0) return if (spent > 0) 100.0 else 0.0
        val r = (spent / optimal).coerceAtLeast(0.0)
        return (100 * kotlin.math.ln(1 + 3 * r) / kotlin.math.ln(10.0)).coerceIn(0.0, 100.0)
    }

    /**
     * Margen de un día: lo gastado en el mes hasta ese día frente a lo que tocaba (con 2 días de cortesía,
     * para que una compra grande a principios de mes no te ponga en rojo).
     * Gastar un 40 % de lo que tocaba = 100 %; justo lo que tocaba = 50 %; un 60 % de más = 0 %.
     */
    fun marginFor(spentToDate: Double, monthBudget: Double, day: Int, length: Int): Double {
        val allowance = monthBudget * min(length, day + 2) / length
        if (allowance <= 0) return if (spentToDate <= 0) 100.0 else 0.0
        val ratio = spentToDate / allowance
        return ((1.6 - ratio) / 1.2).coerceIn(0.0, 1.0) * 100
    }

    fun of(d: FinanceData, budget: SpendingBudget?, plan: BankPlan?, fmt: (Double) -> String): Pulse {
        val today = d.today
        val month = YearMonth.from(today)
        val base = budget?.base ?: 0.0
        fun spentUntil(day: LocalDate) = Budgeting.variable(d, YearMonth.from(day)).filter { it.epochDay <= day.toEpochDay() }.sumOf { it.amount }
        val days = (6 downTo 0).map { back ->
            val day = today.minusDays(back.toLong())
            val spentDay = Budgeting.variable(d, YearMonth.from(day)).filter { it.epochDay == day.toEpochDay() }.sumOf { it.amount }
            DayPulse(day, marginFor(spentUntil(day), base, day.dayOfMonth, YearMonth.from(day).lengthOfMonth()), spentDay)
        }
        val margin = days.last().margin
        val cost = Insights.costOfLiving(d).monthly
        val cushion = if (cost > 0) min(100.0, d.wealth.savings / (cost * 3) * 100) else 100.0
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekSpent = d.spending.filter { it.epochDay in monday.toEpochDay()..today.toEpochDay() && !Budgeting.isBillPayment(it, d.bills) }.sumOf { it.amount }
        val weekOptimal = base / month.lengthOfMonth() * 7
        val weekToDate = weekOptimal * today.dayOfWeek.value / 7
        val spentMonth = spentUntil(today)
        val expectedSoFar = base * min(month.lengthOfMonth(), today.dayOfMonth + 2) / month.lengthOfMonth()
        val health = Insights.health(d, fmt)
        val todayAllowance = max(0.0, plan?.perDay ?: (base / month.lengthOfMonth()))
        // Presión de hoy: lo gastado hoy frente a lo que te toca hoy (1,0 = justo).
        val spentToday = days.last().spent
        val pressure = when { todayAllowance > 0 -> spentToday / todayAllowance; spentToday > 0 -> 2.0; else -> 0.0 }

        val pulse = Pulse(
            cushion, margin, weekSpent, weekOptimal, weekToDate, todayAllowance, health.good, health.indicators.size, pressure, days, "", "",
            base, spentMonth, expectedSoFar, d.wealth.savings, cost, health, spentToday,
        )
        return pulse.copy(statement = statement(pulse, plan), tip = tip(pulse, fmt))
    }

    /** Frases fijas según la situación (sin IA): se elige la que encaja. */
    private fun statement(p: Pulse, plan: BankPlan?): String {
        val redDays = p.days.takeLast(3).count { zoneOf(it.margin) == Zone.RED }
        return when {
            redDays >= 3 -> "Llevas varios días seguidos en rojo: estás gastando más rápido de lo que te permite el mes. Hasta tu próximo cobro, prioriza lo imprescindible."
            zoneOf(p.margin) == Zone.RED -> "Margen bajo: vas por encima del ritmo que te permite el mes. Unos días tranquilos te devuelven al amarillo."
            zoneOf(p.margin) == Zone.YELLOW && p.weekZone != Zone.GREEN -> "Margen justo y semana por encima de lo que tocaba. Afloja unos días para no comerte el resto del mes."
            zoneOf(p.margin) == Zone.YELLOW -> "Margen moderado: vas en línea con lo que te permite el mes."
            p.weekZone == Zone.RED -> "Vas bien en el mes, pero esta semana gastas bastante más de lo que tocaba hasta hoy."
            p.cushion < 34 -> "Tu día a día va bien, pero tu colchón aún es pequeño. Lo que te sobre, mejor al ahorro."
            else -> "Todo en verde: gastas por detrás de lo que te permite el mes" + (plan?.target?.let { "; buen momento para mandar algo extra a ${it.name}." } ?: ".")
        }
    }

    private fun tip(p: Pulse, fmt: (Double) -> String): String = when (zoneOf(p.margin)) {
        Zone.RED -> "Hoy, mejor no gastar más de ${fmt(p.todayAllowance)}: así recuperas margen."
        else -> "Hoy puedes gastar hasta ${fmt(p.todayAllowance)} y seguir en lo óptimo."
    }

    /** La ficha de cada anillo o cuadro: qué mide, tu dato, lo óptimo y qué hacer hoy. Textos fijos. */
    data class Info(val title: String, val value: String, val zone: Zone, val what: String, val now: String, val optimal: String, val todo: String)

    fun info(key: String, p: Pulse, fmt: (Double) -> String, today: LocalDate): Info = when (key) {
        "cushion" -> {
            val months = if (p.monthlyCost > 0) p.savings / p.monthlyCost else 0.0
            val z = zoneOf(p.cushion)
            Info(
                "Colchón", "${p.cushion.toInt()} %", z,
                "Cuánto te cubre tu ahorro si un mes no entrara dinero. 100 % = 3 meses de tu coste de vida.",
                "Tienes ${fmt(p.savings)} ahorrados: ${String.format(java.util.Locale.forLanguageTag("es-ES"), "%.1f", months)} meses de tus ${fmt(p.monthlyCost)} de coste al mes.",
                "100 % (3 meses: ${fmt(p.monthlyCost * 3)}).",
                when (z) {
                    Zone.GREEN -> "Bien cubierto. Lo que pase de 6 meses puede ir a inversiones a largo plazo."
                    Zone.YELLOW -> "A medio camino. Aparta un poco cada mes en tu cuenta remunerada hasta llegar a 3 meses."
                    Zone.RED -> "Colchón pequeño: tu primera meta debería ser llegar a 1 mes de gastos (${fmt(p.monthlyCost)})."
                },
            )
        }
        "margin" -> {
            val z = zoneOf(p.margin)
            Info(
                "Margen", "${p.margin.toInt()} %", z,
                "Si vas por delante o por detrás del ritmo de gasto que te permite el mes. Se mide con lo que tocaba hasta hoy, así que es justo cualquier día del mes.",
                "A día ${today.dayOfMonth} llevas ${fmt(p.spentMonth)} de gasto del día a día; a estas alturas tocaban unos ${fmt(p.expectedSoFar)} (de ${fmt(p.monthBudget)} para todo el mes).",
                "67 % o más (verde): gastar lo que toca da 50 %; gastar menos te sube.",
                when (z) {
                    Zone.GREEN -> "Vas por delante. Puedes gastar hasta ${fmt(p.todayAllowance)} hoy y seguir en verde."
                    Zone.YELLOW -> "Vas justo. Si hoy gastas menos de ${fmt(p.todayAllowance)}, mañana subirá."
                    Zone.RED -> "Vas por detrás. Un par de días gastando solo lo imprescindible te devuelve al amarillo."
                },
            )
        }
        "week" -> Info(
            "Gasto de la semana", "${p.weekScore.toInt()}", p.weekZone,
            "Lo que llevas gastado desde el lunes, en una escala de 0 a 100 que cuesta más subir cuanto más alto estás (como el Esfuerzo de WHOOP). El color compara con lo que tocaba hasta hoy.",
            "Llevas ${fmt(p.weekSpent)}. Hasta hoy (${today.dayOfWeek.value} de 7 días) tocaban ${fmt(p.weekOptimalToDate)}; para toda la semana, ${fmt(p.weekOptimal)}.",
            "Quedarte en la marca gris: ${p.optimalScore.toInt()} hoy, 60 al acabar la semana.",
            when (p.weekZone) {
                Zone.GREEN -> "Vas bien esta semana. Margen de gasto: ${fmt(max(0.0, p.weekOptimal - p.weekSpent))} hasta el domingo."
                Zone.YELLOW -> "Un poco por encima de lo que tocaba. Deja el resto de la semana en ~${fmt(max(0.0, p.weekOptimal - p.weekSpent) / max(1, 8 - today.dayOfWeek.value))} al día."
                Zone.RED -> "Bastante por encima. Lo que gastes de más esta semana se descuenta de las próximas."
            },
        )
        "health" -> {
            val h = p.health
            val z = when { p.healthInRange >= p.healthTotal - 0 -> Zone.GREEN; p.healthInRange >= 3 -> Zone.YELLOW; else -> Zone.RED }
            Info(
                "Salud financiera", "${p.healthInRange}/${p.healthTotal}", z,
                "Cinco indicadores de tu economía: capacidad de ahorro, estabilidad, peso de los gastos fijos, colchón y deudas.",
                h?.indicators?.joinToString("\n") { "• ${it.name}: ${it.value} — " + when (it.status) { Insights.Status.GOOD -> "bien"; Insights.Status.OK -> "mejorable"; Insights.Status.BAD -> "a vigilar" } } ?: "",
                "Los 5 en verde.",
                h?.nextStep ?: "",
            )
        }
        else -> Info(
            "Presión de gasto", String.format(java.util.Locale.forLanguageTag("es-ES"), "%.1f", p.pressure), p.pressureZone,
            "Lo que llevas gastado hoy frente a lo que te toca hoy. 1,0 = justo lo de hoy.",
            "Hoy llevas ${fmt(p.spentToday)} y hoy te tocan ${fmt(p.todayAllowance)}.",
            "1,0 o menos (verde). Hasta 1,3 es amarillo; más, rojo.",
            when (p.pressureZone) {
                Zone.GREEN -> "Sin presión hoy."
                Zone.YELLOW -> "Hoy vas algo por encima: lo de más se reparte entre los días que quedan."
                Zone.RED -> "Hoy vas bastante por encima de lo que te toca."
            },
        )
    }
}
