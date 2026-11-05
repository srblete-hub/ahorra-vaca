package com.caudal.finanzas.domain

import com.caudal.finanzas.data.Category
import com.caudal.finanzas.data.Expense
import java.text.Normalizer
import java.time.LocalDate

/**
 * En qué se va el dinero. [need] separa lo necesario (va a "Necesidades") de lo prescindible
 * (va a "Ocio y caprichos"). Las palabras clave se buscan como palabras enteras, sin tildes.
 */
enum class SpendTag(val label: String, val need: Boolean, val keywords: List<String>) {
    SUPER(
        "Supermercado", true,
        listOf(
            "mercadona", "lidl", "carrefour", "aldi", "alcampo", "eroski", "consum", "hipercor", "supercor", "bonpreu",
            "condis", "ahorramas", "froiz", "gadis", "caprabo", "spar", "coviran", "dia", "supermercado", "super",
            "la compra", "compra semanal", "fruteria", "carniceria", "panaderia", "pescaderia", "mercado",
        ),
    ),
    FOOD(
        "Restaurantes y bares", false,
        listOf(
            "restaurante", "bar", "cafeteria", "cafe", "cena", "comida", "almuerzo", "desayuno", "brunch", "tapas",
            "cervezas", "copas", "glovo", "just eat", "uber eats", "deliveroo", "mcdonald", "mcdonalds", "burger king",
            "telepizza", "dominos", "domino s", "kfc", "starbucks", "foster", "vips", "goiko", "montaditos", "five guys",
            "pizzeria", "pizza", "sushi", "kebab", "tagliatella", "rodilla", "pans company", "tim hortons",
        ),
    ),
    TRANSPORT(
        "Transporte", true,
        listOf(
            "uber", "cabify", "bolt", "freenow", "free now", "taxi", "renfe", "metro", "emt", "tmb", "bus", "autobus",
            "alsa", "blablacar", "gasolina", "gasoil", "diesel", "gasolinera", "repsol", "cepsa", "bp", "shell", "galp",
            "moeve", "ballenoil", "plenoil", "petronor", "parking", "aparcamiento", "peaje", "itv", "taller",
            "seguro coche", "seguro del coche", "abono transporte", "bicing", "lime",
        ),
    ),
    HOME(
        "Casa y facturas", true,
        listOf(
            "alquiler", "hipoteca", "comunidad", "luz", "agua", "gas", "iberdrola", "endesa", "naturgy", "holaluz",
            "totalenergies", "repsol luz", "movistar", "vodafone", "orange", "digi", "yoigo", "masmovil", "pepephone",
            "lowi", "simyo", "jazztel", "o2", "internet", "fibra", "telefono", "movil", "seguro hogar", "ikea",
            "leroy merlin", "bricomart", "brico", "limpieza", "ibi", "basura",
        ),
    ),
    SUBSCRIPTIONS(
        "Suscripciones", false,
        listOf(
            "netflix", "spotify", "hbo", "hbo max", "disney", "prime video", "amazon prime", "icloud", "apple one",
            "apple music", "youtube premium", "chatgpt", "openai", "claude", "google one", "xbox", "playstation", "ps plus",
            "nintendo", "twitch", "patreon", "dazn", "movistar plus", "filmin", "audible", "kindle unlimited",
            "suscripcion", "crunchyroll", "skyshowtime", "duolingo",
        ),
    ),
    SHOPPING(
        "Compras", false,
        listOf(
            "amazon", "zara", "primark", "el corte ingles", "corte ingles", "shein", "aliexpress", "temu", "mediamarkt",
            "media markt", "decathlon", "fnac", "pull bear", "bershka", "mango", "h m", "stradivarius", "massimo dutti",
            "nike", "adidas", "apple store", "pccomponentes", "worten", "sephora", "druni", "primor", "ropa", "zapatillas",
            "zapatos", "tienda", "compras", "vinted", "wallapop", "tiger",
        ),
    ),
    HEALTH(
        "Salud y deporte", true,
        listOf(
            "farmacia", "medico", "dentista", "clinica", "hospital", "sanitas", "adeslas", "asisa", "dkv", "optica",
            "fisio", "fisioterapia", "psicologo", "gimnasio", "gym", "basic fit", "mcfit", "altafit", "vivagym",
            "holmes place", "crossfit", "padel", "piscina",
        ),
    ),
    LEISURE(
        "Ocio", false,
        listOf(
            "cine", "cinesa", "yelmo", "kinepolis", "ocine", "concierto", "entradas", "ticketmaster", "fever", "teatro",
            "museo", "steam", "epic games", "bolera", "karaoke", "discoteca", "fiesta", "festival", "loteria", "futbol",
            "videojuego", "juego", "escape room", "parque",
        ),
    ),
    TRAVEL(
        "Viajes", false,
        listOf(
            "ryanair", "vueling", "iberia", "easyjet", "air europa", "volotea", "booking", "airbnb", "hotel", "hostal",
            "expedia", "edreams", "trainline", "ouigo", "iryo", "viaje", "vuelo", "vacaciones", "apartamento",
        ),
    ),
    GIFTS("Regalos", false, listOf("regalo", "regalos", "cumpleanos", "boda", "flores", "floristeria", "detalle")),
    EDUCATION("Formación", true, listOf("curso", "academia", "udemy", "libro", "libros", "libreria", "universidad", "matricula", "coursera", "platzi")),
    PETS("Mascotas", true, listOf("veterinario", "tiendanimal", "kiwoko", "mascota", "pienso", "perro", "gato")),
    OTHER("Otros", false, emptyList());

    companion object {
        fun of(name: String?): SpendTag? = entries.firstOrNull { it.name == name }
    }
}

object Classifier {
    /** Minúsculas, sin tildes y solo letras y números separados por un espacio. */
    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

    // Las frases más largas primero: "uber eats" gana a "uber" y "seguro coche" a "seguro".
    private val index: List<Pair<String, SpendTag>> =
        SpendTag.entries.flatMap { t -> t.keywords.map { normalize(it) to t } }.sortedByDescending { it.first.length }

    /** Tipo de gasto por palabras clave, o null si no se reconoce. */
    fun guess(note: String): SpendTag? {
        val n = " ${normalize(note)} "
        if (n.isBlank()) return null
        return index.firstOrNull { (kw, _) -> n.contains(" $kw ") }?.second
    }

    /**
     * Primero aprende de ti: si ya clasificaste un gasto con la misma descripción, usa ese tipo.
     * Si no, por palabras clave; y si nada encaja, "Otros".
     */
    fun classify(note: String, history: List<Expense> = emptyList()): SpendTag {
        val n = normalize(note)
        if (n.isNotEmpty()) {
            history.lastOrNull { it.tag.isNotBlank() && normalize(it.note) == n }
                ?.let { SpendTag.of(it.tag) }
                ?.let { return it }
        }
        return guess(note) ?: SpendTag.OTHER
    }

    /** Categoría del reparto que le toca a un tipo de gasto: necesidades u ocio. Null = sin preferencia. */
    fun bucketFor(tag: SpendTag, categories: List<Category>): Long? {
        if (tag == SpendTag.OTHER) return null
        val spending = categories.filter { !it.isSavings }
        if (spending.isEmpty()) return null
        return if (tag.need) {
            (spending.firstOrNull { normalize(it.name).contains("necesidad") } ?: spending.first()).id
        } else {
            (spending.firstOrNull { normalize(it.name).let { n -> n.contains("ocio") || n.contains("capricho") } }
                ?: spending.getOrNull(1) ?: spending.first()).id
        }
    }
}

/** Tipo de un gasto: el elegido, o el que se deduce de su descripción. */
fun Expense.spendTag(): SpendTag = SpendTag.of(tag) ?: Classifier.guess(note) ?: SpendTag.OTHER

/** Un gasto leído de una frase: "18 € en una cena con amigos". [daysAgo] = 1 si dice "ayer". */
data class ParsedExpense(val amount: Double, val note: String, val tag: SpendTag, val daysAgo: Int = 0) {
    fun date(today: LocalDate): LocalDate = today.minusDays(daysAgo.toLong())
}

/**
 * Apuntar gastos escribiendo como hablas:
 * "He pagado 18 € en una cena con amigos y 12 € en gasolina" → dos gastos ya clasificados.
 */
object NaturalEntry {
    private val AMOUNT = Regex("(\\d+(?:[.,]\\d{1,2})?)\\s*(€|euros?|eur|pavos)?", RegexOption.IGNORE_CASE)
    private val SEPARATORS = Regex("\\s+y\\s+|\\s+e\\s+|,(?!\\d)|;|\\+|\\n|\\s+m[aá]s\\s+|\\s+tambi[eé]n\\s+", RegexOption.IGNORE_CASE)
    private val LEADING = Regex(
        "^(?:(?:hoy|ayer|esta (?:ma[ñn]ana|tarde|noche)|anoche)\\s+)?(?:me\\s+)?(?:he\\s+)?" +
            "(?:pagado|gastado|gast[eé]|pagu[eé]|comprado|compr[eé]|apunta|apuntar|a[ñn]ade)?\\s*",
        RegexOption.IGNORE_CASE,
    )
    private val FILLER = Regex("^(?:(?:en|de|por|para|un|una|unos|unas|el|la|los|las|al|del|me|he|euros?|€)\\s+)+", RegexOption.IGNORE_CASE)

    fun parse(text: String, history: List<Expense> = emptyList()): List<ParsedExpense> {
        val yesterday = Regex("\\b(ayer|anoche)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
        val clauses = text.split(SEPARATORS).map { it.trim() }.filter { it.isNotEmpty() }
        // Las partes sin importe se pegan a la siguiente que lo tenga ("pan y leche 5 €").
        val merged = mutableListOf<String>()
        var pending = ""
        for (c in clauses) {
            if (AMOUNT.find(c)?.groups?.get(1) != null && hasNumber(c)) {
                merged += (if (pending.isNotEmpty()) "$pending y $c" else c); pending = ""
            } else if (merged.isEmpty()) pending = if (pending.isEmpty()) c else "$pending y $c"
            else merged[merged.lastIndex] = merged.last() + " y " + c
        }
        return merged.mapNotNull { clause ->
            val m = AMOUNT.find(clause) ?: return@mapNotNull null
            val amount = m.groupValues[1].replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
            var note = clause.removeRange(m.range).replace(Regex("\\s+"), " ").trim()
            note = LEADING.replaceFirst(note, "").trim()
            note = FILLER.replace(note, "").trim().trimEnd('.', '!', ',')
            note = note.replaceFirstChar { it.titlecase() }
            ParsedExpense(amount, note.ifBlank { "Gasto" }, Classifier.classify(note, history), if (yesterday) 1 else 0)
        }
    }

    private fun hasNumber(s: String) = s.any { it.isDigit() }
}
