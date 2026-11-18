package com.caudal.finanzas.domain

import com.caudal.finanzas.data.Expense
import java.util.Locale
import kotlin.math.abs

/**
 * Nombre limpio del comercio a partir de lo que pone el banco o la cartera:
 * "MPVinted*" → "Vinted", "PAYPAL *STEAM GAMES" → "Steam", "AMZN Mktp ES*2K4" → "Amazon",
 * "SUMUP *BAR PEPE" → "Bar Pepe", "MERCADONA 2345 MADRID" → "Mercadona".
 */
object Merchants {
    /** Intermediarios de pago que van delante del comercio de verdad. */
    private val PREFIX = Regex(
        "^(?:mp|pp|paypal|sq|sumup|sp|ztl|iz|izettle|zettle|google|gpay|apple ?pay|stripe|tpv|compra en|compra|pago en|pago|cargo|recibo|bizum|wl|cbk)\\s*[*/:\\-]+\\s*",
        RegexOption.IGNORE_CASE,
    )
    /** "MPVinted": el prefijo pegado al nombre (mayúsculas seguidas de Mayúscula+minúscula). */
    private val GLUED = Regex("^(?:MP|PP|SQ|SP|ZTL|IZ|WL)(?=[A-Z][a-z])")

    /** Marcas conocidas: patrón (sobre el texto normalizado) → nombre bonito. */
    private val KNOWN: List<Pair<Regex, String>> = listOf(
        "amzn|amazon" to "Amazon", "apple com|itunes|apple bill" to "Apple", "youtube" to "YouTube", "uber eats" to "Uber Eats",
        "uber" to "Uber", "netflix" to "Netflix", "spotify" to "Spotify", "vinted" to "Vinted", "wallapop" to "Wallapop",
        "mercadona" to "Mercadona", "lidl" to "Lidl", "carrefour" to "Carrefour", "aldi" to "Aldi", "eroski" to "Eroski",
        "alcampo" to "Alcampo", "dia supermercado|supermercados dia" to "Dia", "consum" to "Consum", "ahorramas" to "Ahorramas",
        "glovo" to "Glovo", "just eat" to "Just Eat", "deliveroo" to "Deliveroo", "telpark" to "Telpark", "repsol" to "Repsol",
        "cepsa|moeve" to "Cepsa", "galp" to "Galp", "openai|chatgpt" to "ChatGPT", "anthropic|claude ai" to "Claude",
        "x corp|twitter" to "X", "steam|valve" to "Steam", "renfe" to "Renfe", "ryanair" to "Ryanair", "vueling" to "Vueling",
        "iberia" to "Iberia", "booking" to "Booking", "airbnb" to "Airbnb", "zara" to "Zara", "primark" to "Primark",
        "decathlon" to "Decathlon", "ikea" to "IKEA", "mcdonald" to "McDonald's", "burger king" to "Burger King",
        "starbucks" to "Starbucks", "bolt" to "Bolt", "cabify" to "Cabify", "free ?now" to "FreeNow", "lime" to "Lime",
        "telepizza" to "Telepizza", "domino" to "Domino's", "aliexpress" to "AliExpress", "shein" to "Shein", "temu" to "Temu",
        "corte ingles" to "El Corte Inglés", "media ?markt" to "MediaMarkt", "pccomponentes" to "PcComponentes",
        "disney" to "Disney+", "hbo|max com" to "Max", "prime video|amazon prime" to "Amazon Prime", "icloud" to "iCloud",
        "playstation|psn" to "PlayStation", "xbox|microsoft" to "Microsoft", "google one|google storage" to "Google One",
        "movistar" to "Movistar", "vodafone" to "Vodafone", "orange" to "Orange", "digi" to "Digi", "iberdrola" to "Iberdrola",
        "endesa" to "Endesa", "naturgy" to "Naturgy", "holaluz" to "Holaluz", "basic ?fit" to "Basic-Fit", "druni" to "Druni",
        "sephora" to "Sephora", "primor" to "Primor", "fnac" to "Fnac", "leroy merlin" to "Leroy Merlin", "cinesa" to "Cinesa",
        "yelmo" to "Yelmo", "ticketmaster" to "Ticketmaster", "fever" to "Fever", "revolut" to "Revolut",
    ).map { (p, n) -> Regex("(^| )(?:$p)( |$)") to n }

    /** Web de cada marca, para pedir su logo (servicio gratuito de iconos de webs). */
    private val DOMAINS = mapOf(
        "Amazon" to "amazon.es", "Apple" to "apple.com", "YouTube" to "youtube.com", "Uber Eats" to "ubereats.com", "Uber" to "uber.com",
        "Netflix" to "netflix.com", "Spotify" to "spotify.com", "Vinted" to "vinted.es", "Wallapop" to "wallapop.com", "Mercadona" to "mercadona.es",
        "Lidl" to "lidl.es", "Carrefour" to "carrefour.es", "Aldi" to "aldi.es", "Eroski" to "eroski.es", "Alcampo" to "alcampo.es", "Dia" to "dia.es",
        "Consum" to "consum.es", "Ahorramas" to "ahorramas.com", "Glovo" to "glovoapp.com", "Just Eat" to "just-eat.es", "Deliveroo" to "deliveroo.es",
        "Telpark" to "telpark.com", "Repsol" to "repsol.es", "Cepsa" to "moeveglobal.com", "Galp" to "galp.com", "ChatGPT" to "openai.com",
        "Claude" to "claude.ai", "X" to "x.com", "Steam" to "steampowered.com", "Renfe" to "renfe.com", "Ryanair" to "ryanair.com",
        "Vueling" to "vueling.com", "Iberia" to "iberia.com", "Booking" to "booking.com", "Airbnb" to "airbnb.es", "Zara" to "zara.com",
        "Primark" to "primark.com", "Decathlon" to "decathlon.es", "IKEA" to "ikea.com", "McDonald's" to "mcdonalds.es", "Burger King" to "burgerking.es",
        "Starbucks" to "starbucks.es", "Bolt" to "bolt.eu", "Cabify" to "cabify.com", "FreeNow" to "free-now.com", "Lime" to "li.me",
        "Telepizza" to "telepizza.es", "Domino's" to "dominospizza.es", "AliExpress" to "aliexpress.com", "Shein" to "shein.com", "Temu" to "temu.com",
        "El Corte Inglés" to "elcorteingles.es", "MediaMarkt" to "mediamarkt.es", "PcComponentes" to "pccomponentes.com", "Disney+" to "disneyplus.com",
        "Max" to "max.com", "Amazon Prime" to "primevideo.com", "iCloud" to "icloud.com", "PlayStation" to "playstation.com", "Microsoft" to "microsoft.com",
        "Google One" to "one.google.com", "Movistar" to "movistar.es", "Vodafone" to "vodafone.es", "Orange" to "orange.es", "Digi" to "digimobil.es",
        "Iberdrola" to "iberdrola.es", "Endesa" to "endesa.com", "Naturgy" to "naturgy.es", "Holaluz" to "holaluz.com", "Basic-Fit" to "basic-fit.com",
        "Druni" to "druni.es", "Sephora" to "sephora.es", "Primor" to "primor.eu", "Fnac" to "fnac.es", "Leroy Merlin" to "leroymerlin.es",
        "Cinesa" to "cinesa.es", "Yelmo" to "yelmocines.es", "Ticketmaster" to "ticketmaster.es", "Fever" to "feverup.com", "Revolut" to "revolut.com",
    )

    /** Logo del comercio (si es conocido), gratis y sin clave. */
    fun logoUrl(note: String): String? = DOMAINS[clean(note)]?.let { "https://www.google.com/s2/favicons?domain=$it&sz=128" }

    /** Palabras que no son una marca: con ellas no se adivina la web. */
    private val GENERIC = setOf("bar", "cafe", "cafeteria", "restaurante", "tienda", "gasolinera", "farmacia", "supermercado", "panaderia", "pago",
        "compra", "transferencia", "bizum", "recibo", "ingreso", "gasto", "comida", "cena", "desayuno", "taxi", "parking", "otros", "efectivo")

    /**
     * Logos a probar, en orden: el de la marca conocida y, si no lo es, su web adivinada por el nombre
     * ("Telpark" → telpark.es / telpark.com). El servicio de iconos devuelve error si la web no existe,
     * y entonces se pone el icono de la categoría.
     */
    fun logoUrls(note: String): List<String> {
        logoUrl(note)?.let { return listOf(it) }
        val name = Classifier.normalize(clean(note))
        val words = name.split(' ').filter { it.isNotBlank() }
        if (words.isEmpty() || words.size > 3 || words.all { it in GENERIC } || words.first() in GENERIC) return emptyList()
        val slug = words.joinToString("")
        if (slug.length < 3 || slug.any { !it.isLetterOrDigit() }) return emptyList()
        return listOf("es", "com").map { "https://icons.duckduckgo.com/ip3/$slug.$it.ico" }
    }

    private val NOISE = setOf("es", "esp", "spain", "sl", "sa", "slu", "s", "l", "a", "madrid", "barcelona", "valencia", "sevilla", "www", "com", "eu", "mktp", "marketplace", "inc", "ltd", "online")

    fun clean(raw: String): String {
        var s = raw.trim().trim('"', '\'')
        if (s.isEmpty()) return s
        s = GLUED.replace(s, "")
        repeat(2) { s = PREFIX.replace(s, "") }
        val n = Classifier.normalize(s)
        KNOWN.firstOrNull { it.first.containsMatchIn(n) }?.let { return it.second }
        // "VINTED*12345": lo de antes del asterisco; si no hay nada, lo de después.
        s = s.split('*').map { it.trim() }.firstOrNull { it.any(Char::isLetter) } ?: s
        val words = s.split(Regex("\\s+")).filter { w -> w.any(Char::isLetter) && w.count(Char::isDigit) <= 1 }
            .dropLastWhile { Classifier.normalize(it) in NOISE }
        val out = words.joinToString(" ").trim(' ', '.', ',', '-', '*').ifBlank { s.trim() }
        return if (out == out.uppercase(Locale.ROOT) && out.any { it.isLetter() }) {
            out.lowercase(Locale.ROOT).split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.titlecase(Locale.ROOT) } }
        } else out
    }
}

/** Gastos repetidos: mismo importe con menos de 1 minuto de diferencia y alguno leído de un aviso. Se queda el primero. */
object Duplicates {
    const val WINDOW_MS = 60_000L

    fun extras(list: List<Expense>): List<Expense> {
        val out = mutableListOf<Expense>()
        list.groupBy { Math.round(it.amount * 100) }.values.forEach { same ->
            if (same.size < 2) return@forEach
            val sorted = same.sortedWith(compareBy({ it.createdAt }, { it.id }))
            var keep = sorted.first()
            sorted.drop(1).forEach { e ->
                // Solo si alguno llegó por un aviso de pago (banco o cartera): lo que apuntas tú a mano no se toca.
                if (abs(e.createdAt - keep.createdAt) < WINDOW_MS && e.epochDay == keep.epochDay && (e.fromCard || keep.fromCard)) out += e else keep = e
            }
        }
        return out
    }
}
