package com.caudal.finanzas.domain

import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pepe, el pastor, contesta dentro de la app. SIN IA: entiende la pregunta por palabras clave y responde
 * con cálculos sobre tus datos (los mismos que usa el resto de la app). Nunca recomienda comprar ni vender inversiones.
 */
object PepeBot {
    private val ES = Locale.forLanguageTag("es-ES")

    /** Lo que Pepe necesita saber para contestar. */
    data class Ctx(
        val d: FinanceData,
        val cash: CashPlan?,
        val goal: GoalStatus?,
        val userName: String,
        val fmt: (Double) -> String,
        /** Para variar las frases (en las pruebas se fija). */
        val rnd: kotlin.random.Random = kotlin.random.Random.Default,
    )

    private fun first(name: String) = name.trim().substringBefore(' ')

    fun greeting(name: String, rnd: kotlin.random.Random = kotlin.random.Random.Default): String {
        val n = first(name)
        val who = if (n.isBlank()) "" else ", $n"
        val hi = listOf("¡Buenas$who!", "¡Hombre$who! ¿Qué tal?", "¡Muy buenas$who!", "¡Aquí estoy$who!").random(rnd)
        val ask = listOf(
            "Pregúntame cómo vas este mes, si llegas a la nómina o en qué recortar.",
            "¿Miramos cuánto te queda hoy, tus recibos o tu objetivo?",
            "Puedes preguntarme cuánto llevas en algo (\"¿cuánto gasto en comida?\") o si te puedes permitir un capricho (\"¿puedo gastarme 40 €?\").",
        ).random(rnd)
        return "$hi Soy Pepe. $ask"
    }

    // Refranes de pastor para cerrar algunas respuestas (no siempre).
    private val SAYINGS = listOf(
        "De grano en grano llena la gallina el buche.", "Quien guarda, halla.", "Poco a poco se va lejos.",
        "Oveja que bala, bocado que pierde.", "A buen pastor, buen rebaño.", "Más vale prevenir que curar.",
        "No por mucho madrugar amanece más temprano.", "Cuentas claras, vaca contenta.",
    )
    private val OPENERS = mapOf(
        "month" to listOf("", "Vamos a ver… ", "Te cuento: ", "Echando la cuenta, "),
        "payday" to listOf("", "He mirado el calendario. ", "A ver, hasta la nómina… "),
        "cut" to listOf("", "Ahí va lo que veo. ", "Con la libreta en la mano: "),
        "today" to listOf("", "Para hoy: ", "Mirando el día: "),
        "save" to listOf("", "Mi consejo: ", "Lo que yo haría: "),
        "tag" to listOf("", "Mirando tus pagos: ", "Lo tengo apuntado: "),
        "bank" to listOf("", "Mirando el banco: "),
        "goal" to listOf("", "Tu hucha va así: ", "Sobre tu objetivo: "),
    )

    /** Pone una entrada y, a veces, un refrán, para que no conteste siempre igual. */
    private fun vary(c: Ctx, kind: String, body: String): String {
        val open = OPENERS[kind]?.random(c.rnd).orEmpty()
        val lower = open.endsWith(": ") || open.endsWith(", ")
        val text = if (open.isEmpty()) body else open + if (lower) body.replaceFirstChar { it.lowercaseChar() } else body
        return if (c.rnd.nextInt(100) < 35) "$text ${SAYINGS.random(c.rnd)}" else text
    }

    private val UNKNOWN = listOf(
        "Esa no me la sé, que soy pastor y no adivino.", "Uy, eso se me escapa del rebaño.", "Eso no lo llevo apuntado en la libreta.",
    )

    fun answer(question: String, c: Ctx): String {
        val q = " ${Classifier.normalize(question)} "
        fun has(vararg w: String) = w.any { " $it" in q || q.contains(it) }
        val amount = Regex("(\\d+(?:[.,]\\d{1,2})?)").find(question)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
        return when {
            q.isBlank() -> greeting(c.userName, c.rnd)
            has("hola", "buenas", "buenos dias", "que tal", "hey") && q.trim().split(' ').size <= 4 -> greeting(c.userName, c.rnd)
            has("gracias") -> listOf("A mandar. Aquí estoy, con el cayado y la libreta.", "¡De nada! Para eso está el pastor.", "No hay de qué. Cuida el rebaño.").random(c.rnd)
            has("quien eres", "que eres", "eres una ia", "eres un robot", "como funcionas") ->
                "Soy Pepe, pastor de toda la vida. No soy una IA: echo las cuentas con tus datos de la app, con reglas fijas, y nada sale de tu móvil."
            has("chiste", "broma", "algo gracioso") -> listOf(
                "¿Sabes por qué las vacas no usan tarjeta? Porque prefieren pagar a pezuña.",
                "Una oveja le dice a otra: \"¿ahorras?\". Y la otra: \"Beeee… lo intento\".",
                "Mi perro también ahorra: entierra los huesos para el invierno.",
            ).random(c.rnd)
            has("consejo", "recomienda", "recomiendas", "que me dices", "alguna idea", "sorprendeme") -> tips(c).randomOrNull(c.rnd) ?: vary(c, "save", howMuchToSave(c))
            amount != null && has("puedo", "permitir", "compro", "comprar", "gastarme", "me gasto", "capricho") -> canAfford(amount, question, c)
            has("queda hoy", "quedan hoy", "gastar hoy", "hoy puedo", "para hoy", "llevo hoy", "gastado hoy") -> vary(c, "today", today(c))
            has("ayer") -> vary(c, "today", yesterday(c))
            has("esta semana", "la semana", "semanal") -> vary(c, "month", week(c))
            has("mas caro", "mayor gasto", "gasto mas grande", "mas grande") -> vary(c, "tag", biggest(c))
            has("cartera", "cripto", "bitcoin", "accion", "invers", "bolsa") -> portfolio(c)
            has("suscrip", "netflix", "spotify", "plataforma") -> subscriptions(c)
            has("interes", "robinhood", "rentabilidad", "mis ahorros", "tengo ahorrado") -> vary(c, "save", savings(c))
            has("ingreso", "me ha entrado", "cuanto he cobrado", "youtube") -> vary(c, "month", incomes(c))
            has("llego", "fin de mes", "nomina", "cobro", "cuando cobro", "hasta el") -> vary(c, "payday", untilPayday(c))
            has("recort", "ahorrar en", "gasto mucho", "gastando mucho", "donde se me va", "en que se me va", "en que gasto") -> vary(c, "cut", cutBack(c))
            has("cuanto deberia ahorrar", "cuanto ahorro", "cuanto ahorrar", "ahorrar al mes", "ahorrar") -> vary(c, "save", howMuchToSave(c))
            has("objetivo", "meta", "hucha") -> vary(c, "goal", goal(c))
            has("alquiler", "fijo", "recibo", "factura") -> fixed(c)
            tagIn(q) != null -> vary(c, "tag", tagSpend(tagIn(q)!!, c))
            has("como voy", "resumen", "este mes", "que tal voy", "como va") -> vary(c, "month", month(c))
            has("banco", "saldo", "cuanto tengo", "dinero tengo") -> vary(c, "bank", bank(c))
            else -> UNKNOWN.random(c.rnd) + " Prueba con: \"¿cómo voy este mes?\", \"¿llego a la nómina?\", " +
                "\"¿en qué puedo recortar?\", \"¿cuánto gasto en ocio?\", \"¿puedo gastarme 30 €?\" o \"dame un consejo\"."
        }
    }

    // ---------- Pepe por su cuenta: cosas que le interesa contarte ----------

    /** Recomendaciones que Pepe puede mandar sin que le preguntes, de más a menos urgente. */
    fun tips(c: Ctx): List<String> {
        val d = c.d; val f = c.fmt
        val out = mutableListOf<String>()
        UpcomingBills.of(d, 2).firstOrNull()?.let { b ->
            out += "Te aviso: ${b.whenText(d.today)} pasa ${b.name} (${f(b.amount)}). Deja ese dinero quieto en el banco."
        }
        TodayLeft.of(d, c.cash)?.let { (perDay, left) ->
            if (left < 0) out += "Hoy ya te has pasado ${f(-left)} de los ${f(perDay)} del día. Mañana, un poco más tranquilo y se compensa."
        }
        WeeklyReader.of(d, f)?.insights?.firstOrNull { it.up }?.let {
            out += "Esta semana me ha llamado la atención: ${it.title.replaceFirstChar { ch -> ch.lowercaseChar() }}. ${it.detail}"
        }
        SubscriptionChecker.of(d)?.takeIf { it.maxSaving >= 3 }?.let { out += "Revisando tus suscripciones podrías ahorrar hasta ${f(it.maxSaving)} al mes. ¿Les echas un ojo?" }
        TypicalSpend.of(d)?.takeIf { it.diff < -20 }?.let { out += "Vas ${f(-it.diff)} por debajo de tu mes típico. Buen momento para pasar algo a la hucha." }
        c.goal?.let { g ->
            val pct = if (g.goal.target > 0) (g.current / g.goal.target * 100).roundToInt() else 0
            if (pct in 1..99) out += "Tu objetivo «${g.goal.name.ifBlank { "de ahorro" }}» va por el $pct %. Te faltan ${f((g.goal.target - g.current).coerceAtLeast(0.0))}."
        }
        d.accounts.filter { it.isSaving() && it.interestRate > 0 }.maxByOrNull { it.balance }?.let { a ->
            val m = InterestMath.monthlyInterest(a.balance, a.interestRate)
            if (m >= 1) out += "Tu ${a.name} te da unos ${f(m)} al mes en intereses. El dinero quieto también trabaja."
        }
        return out
    }

    // ---------- Respuestas ----------

    private fun yesterday(c: Ctx): String {
        val d = c.d; val f = c.fmt
        val y = d.today.minusDays(1).toEpochDay()
        val list = d.spending.filter { it.epochDay == y && !Budgeting.isFixed(it, d.bills) }
        if (list.isEmpty()) return "Ayer no gastaste nada. ¡Así da gusto!"
        val top = list.maxByOrNull { it.amount }!!
        return "Ayer gastaste ${f(list.sumOf { it.amount })} en ${list.size} ${if (list.size == 1) "pago" else "pagos"}. El más grande: ${Merchants.clean(top.note).ifBlank { "sin nombre" }}, ${f(top.amount)}."
    }

    private fun week(c: Ctx): String {
        val d = c.d; val f = c.fmt
        val monday = d.today.minusDays((d.today.dayOfWeek.value - 1).toLong())
        val spent = d.spending.filter { it.epochDay >= monday.toEpochDay() && it.epochDay <= d.today.toEpochDay() && !Budgeting.isFixed(it, d.bills) }.sumOf { it.amount }
        val ins = WeeklyReader.of(d, f)?.insights?.firstOrNull()
        return "Esta semana llevas ${f(spent)} en el día a día." + (ins?.let { " Lo que más destaca: ${it.title.replaceFirstChar { ch -> ch.lowercaseChar() }}." } ?: " Nada fuera de lo normal.")
    }

    private fun biggest(c: Ctx): String {
        val d = c.d; val f = c.fmt
        val e = d.spendingIn(YearMonth.from(d.today)).filter { !Budgeting.isFixed(it, d.bills) }.maxByOrNull { it.amount }
            ?: return "Este mes aún no tienes gastos del día a día."
        return "Tu gasto más grande del mes (sin fijos) es ${Merchants.clean(e.note).ifBlank { "sin nombre" }}: ${f(e.amount)}."
    }

    private fun savings(c: Ctx): String {
        val f = c.fmt
        val acc = c.d.accounts.filter { it.isSaving() && it.balance > 0 }
        if (acc.isEmpty()) return "No tienes cuentas de ahorro apuntadas. Añade la tuya en Patrimonio y te cuento lo que te da."
        return "Tienes ${f(acc.sumOf { it.balance })} ahorrados" + acc.filter { it.interestRate > 0 }.joinToString("") { a ->
            ". ${a.name} al ${String.format(ES, "%.1f", a.interestRate)} % te da unos ${f(InterestMath.monthlyInterest(a.balance, a.interestRate))} al mes"
        } + ". Ese no se toca."
    }

    private fun incomes(c: Ctx): String {
        val d = c.d; val f = c.fmt
        val m = YearMonth.from(d.today)
        val list = d.incomes.filter { YearMonth.from(java.time.LocalDate.ofEpochDay(it.epochDay)) == m }
        if (list.isEmpty()) return "Este mes aún no te ha entrado nada apuntado."
        return "Este mes te han entrado ${f(list.sumOf { it.amount })}: " +
            list.sortedByDescending { it.amount }.take(3).joinToString(", ") { "${it.note.ifBlank { "ingreso" }} ${f(it.amount)}" } + "."
    }

    private fun month(c: Ctx): String {
        val d = c.d; val f = c.fmt
        val m = YearMonth.from(d.today)
        val variable = d.spendingIn(m).filter { !Budgeting.isFixed(it, d.bills) }
        val spent = variable.sumOf { it.amount }
        val top = variable.groupBy { it.spendTag() }.mapValues { (_, l) -> l.sumOf { it.amount } }.maxByOrNull { it.value }
        val t = TypicalSpend.of(d)
        val vs = t?.let {
            when {
                abs(it.diff) < 10 -> "Vas como en un mes normal a estas alturas."
                it.diff > 0 -> "Vas ${f(it.diff)} por encima de tu mes típico a día ${it.today}: ojo, que el rebaño se desmanda."
                else -> "Vas ${f(-it.diff)} por debajo de tu mes típico a día ${it.today}. Así me gusta."
            }
        } ?: ""
        return buildString {
            append("En ${m.month.getDisplayName(TextStyle.FULL, ES)} llevas ${f(spent)} en el día a día (sin alquiler ni fijos)")
            append(" y te han entrado ${f(d.receivedIn(m))}.")
            top?.let { append(" Donde más: ${it.key.label.lowercase(ES)}, ${f(it.value)}.") }
            if (vs.isNotEmpty()) append(" ").append(vs)
        }
    }

    private fun today(c: Ctx): String {
        val f = c.fmt
        val spent = TodayLeft.spentToday(c.d)
        val (perDay, left) = TodayLeft.of(c.d, c.cash) ?: return "Hoy llevas ${f(spent)}. Apunta tu nómina y te digo cuánto te toca al día."
        val bills = UpcomingBills.of(c.d, 0)
        return when {
            left >= 0 -> "Hoy llevas ${f(spent)} y te quedan ${f(left)} de los ${f(perDay)} que te tocan."
            else -> "Hoy llevas ${f(spent)}: te has pasado ${f(-left)} de los ${f(perDay)}. No pasa nada, se descuenta de los próximos días."
        } + if (bills.isNotEmpty()) " Ojo, hoy pasa ${bills.joinToString(" y ") { "${it.name} (${f(it.amount)})" }}." else ""
    }

    private fun untilPayday(c: Ctx): String {
        val p = c.cash ?: return "Necesito saber cuándo cobras para echar la cuenta: apunta tu nómina en Ingresos."
        val f = c.fmt
        val min = p.series.minByOrNull { it.second }
        val days = p.days
        return when {
            p.perDayBefore < 0 -> "Así no llegas con ${f(p.cushion)} de colchón a la ${p.paydayName.lowercase(ES)} (${short(p.payday)}): te faltarían unos ${f(p.shortfall)}. " +
                "Toca apretarse el cinto o pasar algo del ahorro."
            else -> "Llegas. Tienes ${f(p.balance)} en el banco y faltan $days ${if (days == 1) "día" else "días"} para la ${p.paydayName.lowercase(ES)} (${short(p.payday)}). " +
                "Puedes gastar unos ${f(p.perDayBefore)} al día y acabarías con ${f(p.cushion)} de colchón." +
                (min?.let { " Lo más bajo será ${f(it.second)} el ${short(it.first)}." } ?: "")
        }
    }

    private fun cutBack(c: Ctx): String {
        val d = c.d; val f = c.fmt
        val tips = mutableListOf<String>()
        WeeklyReader.of(d, f)?.insights?.filter { it.up }?.take(2)?.forEach { tips += "${it.title}. ${it.detail}" }
        if (tips.isEmpty()) {
            val m = YearMonth.from(d.today)
            d.spendingIn(m).filter { !Budgeting.isFixed(it, d.bills) && !it.spendTag().need }
                .groupBy { it.spendTag() }.mapValues { (_, l) -> l.sumOf { it.amount } }
                .entries.sortedByDescending { it.value }.take(2)
                .forEach { tips += "En ${it.key.label.lowercase(ES)} llevas ${f(it.value)} este mes." }
        }
        SubscriptionChecker.of(d)?.takeIf { it.maxSaving >= 1 }?.let { tips += "Revisando suscripciones podrías ahorrar hasta ${f(it.maxSaving)} al mes." }
        return if (tips.isEmpty()) "No veo nada desmadrado: tus gastos van en su sitio. Sigue así, que oveja que no se aleja no se pierde."
        else "Yo miraría esto:\n" + tips.joinToString("\n") { "• $it" }
    }

    private fun howMuchToSave(c: Ctx): String {
        val d = c.d; val f = c.fmt
        val m = YearMonth.from(d.today).minusMonths(1)
        val inc = d.receivedIn(m).takeIf { it > 0 } ?: d.receivedIn(YearMonth.from(d.today))
        if (inc <= 0) return "Apunta lo que cobras y te digo cuánto apartar."
        val ten = (inc * 0.10 / 10).roundToInt() * 10.0
        val twenty = (inc * 0.20 / 10).roundToInt() * 10.0
        val g = c.goal?.takeIf { it.requiredMonthly > 0 }
        return "Con lo que te entra (${f(inc)} al mes), lo sano es apartar entre ${f(ten)} y ${f(twenty)} al mes, del 10 al 20 %." +
            (g?.let { " Para llegar a «${it.goal.name.ifBlank { "tu objetivo" }}» a tiempo te harían falta ${f(it.requiredMonthly)} al mes." } ?: "") +
            " Mejor nada más cobrar, que lo que no se ve no se gasta."
    }

    private fun goal(c: Ctx): String {
        val g = c.goal ?: return "Aún no tienes objetivo de ahorro. Créalo en Patrimonio y te digo cuándo llegas."
        val f = c.fmt
        val pct = if (g.goal.target > 0) (g.current / g.goal.target * 100).roundToInt() else 0
        val left = (g.goal.target - g.current).coerceAtLeast(0.0)
        return "«${g.goal.name.ifBlank { "Tu objetivo" }}»: llevas ${f(g.current)} de ${f(g.goal.target)} ($pct %). Te faltan ${f(left)}." +
            (g.eta?.let { " A tu ritmo llegarías el ${it.format(java.time.format.DateTimeFormatter.ofPattern("d 'de' MMMM yyyy", ES))}." } ?: " A este ritmo no llegas: mete algo cada semana.")
    }

    private fun portfolio(c: Ctx): String {
        val d = c.d; val f = c.fmt
        val items = d.crypto.map { Triple(it.name, it.amount * it.price, it.amount * it.avgBuyPrice) } +
            d.stocks.map { Triple(it.name.ifBlank { it.ticker }, it.shares * it.price, it.shares * it.avgBuyPrice) }
        if (items.isEmpty()) return "No tienes inversiones apuntadas. Si las tienes, añádelas en Inversión y te digo cómo van."
        val value = items.sumOf { it.second }; val cost = items.sumOf { it.third }
        val best = items.filter { it.third > 0 }.maxByOrNull { (it.second - it.third) / it.third }
        val worst = items.filter { it.third > 0 }.minByOrNull { (it.second - it.third) / it.third }
        return buildString {
            append("Tu cartera vale ${f(value)}")
            if (cost > 0) append(", ${if (value >= cost) "+" else "−"}${f(abs(value - cost))} (${pct((value - cost) / cost)}) desde que compraste")
            append(".")
            if (best != null && worst != null && best != worst) append(" Lo que mejor va: ${best.first} (${pct((best.second - best.third) / best.third)}). Lo que peor: ${worst.first} (${pct((worst.second - worst.third) / worst.third)}).")
            append(" Yo no te digo qué comprar ni vender: eso es cosa tuya.")
        }
    }

    private fun subscriptions(c: Ctx): String {
        val s = SubscriptionChecker.of(c.d) ?: return "No veo suscripciones que se repitan cada mes."
        val f = c.fmt
        return "Pagas ${f(s.monthlyTotal)} al mes en ${s.subs.size} suscripciones (${s.subs.joinToString(", ") { it.name }})." +
            if (s.actions.isEmpty()) " Todas en orden." else "\n" + s.actions.take(3).joinToString("\n") { "• ${it.text}" }
    }

    private fun fixed(c: Ctx): String {
        val d = c.d; val f = c.fmt
        if (d.bills.isEmpty()) return "No tienes gastos fijos apuntados. Añade el alquiler y los recibos en Movimientos."
        val total = d.bills.sumOf { it.amount }
        return "Tus fijos suman ${f(total)} al mes: " + d.bills.sortedByDescending { it.amount }.take(6).joinToString(", ") { "${it.name} ${f(it.amount)}" } +
            ". No cuentan en tu gasto del día a día."
    }

    private fun bank(c: Ctx): String {
        val bank = c.d.accounts.filter { it.type == com.caudal.finanzas.data.AccountType.BANK }
        val f = c.fmt
        return "En el banco tienes ${f(bank.sumOf { it.balance })}" + (if (bank.size > 1) " (${bank.joinToString(", ") { "${it.name} ${f(it.balance)}" }})" else "") +
            ". El ahorro no lo cuento: ese no se toca."
    }

    private fun tagSpend(tag: SpendTag, c: Ctx): String {
        val d = c.d; val f = c.fmt
        val m = YearMonth.from(d.today)
        val now = d.spendingIn(m).filter { it.spendTag() == tag }
        val before = d.spendingIn(m.minusMonths(1)).filter { it.spendTag() == tag }.sumOf { it.amount }
        val sum = now.sumOf { it.amount }
        if (now.isEmpty()) return "Este mes no llevas nada en ${tag.label.lowercase(ES)}." + if (before > 0) " El mes pasado fueron ${f(before)}." else ""
        val where = now.groupBy { Merchants.clean(it.note).ifBlank { "otros" } }.mapValues { (_, l) -> l.sumOf { it.amount } }.maxByOrNull { it.value }
        return "En ${tag.label.lowercase(ES)} llevas ${f(sum)} este mes (${now.size} ${if (now.size == 1) "pago" else "pagos"})." +
            (if (before > 0) " El mes pasado, ${f(before)} en total." else "") +
            (where?.let { " Donde más: ${it.key}, ${f(it.value)}." } ?: "")
    }

    private fun canAfford(price: Double, question: String, c: Ctx): String {
        val f = c.fmt
        val p = c.cash
        val name = question.replace(Regex("[¿?]"), "").trim()
        if (p == null) return "Apunta tu nómina y tu banco y te digo si cabe."
        val perDay = p.perDayBefore
        val days = (price / perDay.coerceAtLeast(1.0))
        return when {
            perDay <= 0 -> "Ahora mismo no: ya vas justo para llegar a la ${p.paydayName.lowercase(ES)}. Espérate a cobrar."
            price <= perDay -> "Sí, cabe: son ${f(price)} y hoy te tocan unos ${f(perDay)}. Disfrútalo."
            price <= perDay * 3 -> "Cabe, pero equivale a ${days.roundToInt()} días de gasto. Si lo compras, aprieta un poco los próximos días."
            else -> "Uf, ${f(price)} son unos ${days.roundToInt()} días de lo que te toca gastar (${f(perDay)}/día). Yo lo dejaría para después de la ${p.paydayName.lowercase(ES)}. " +
                "Puedes mirarlo más a fondo en \"¿Lo compro o no?\"."
        }.let { if (name.length > 60) it else it }
    }

    // ---------- Ayudas ----------

    private fun tagIn(q: String): SpendTag? {
        SpendTag.values().filter { it != SpendTag.OTHER }.forEach { t -> if (" ${Classifier.normalize(t.label)}" in q) return t }
        val words = mapOf(
            SpendTag.FOOD to listOf("comida", "comer", "restaurante", "bares", "cenas", "delivery", "glovo"),
            SpendTag.SUPER to listOf("super", "mercadona", "compra"),
            SpendTag.LEISURE to listOf("ocio", "cine", "fiesta", "salir"),
            SpendTag.TRANSPORT to listOf("transporte", "gasolina", "uber", "metro", "coche"),
            SpendTag.CLOTHES to listOf("ropa", "zapatillas", "vinted", "zara"),
            SpendTag.SHOPPING to listOf("compras", "amazon"),
            SpendTag.SPORT to listOf("deporte", "gimnasio", "gym", "padel", "futbol"),
        )
        return words.entries.firstOrNull { (_, ws) -> ws.any { " $it" in q } }?.key
    }

    private fun short(d: java.time.LocalDate) = d.format(java.time.format.DateTimeFormatter.ofPattern("d MMM", ES)).replace(".", "")
    private fun pct(v: Double) = (if (v >= 0) "+" else "−") + String.format(ES, "%.1f %%", abs(v) * 100)
}
