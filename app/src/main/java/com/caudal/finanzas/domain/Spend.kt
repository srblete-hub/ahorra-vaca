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
            // Más comercios y palabras (España).
            "alcampo", "hipercor", "bm supermercados", "gadis", "simply", "supersol", "masymas",
            "mas y mas", "hiperdino", "dia market", "la sirena", "aldi supermercados", "lidl supermercados", "eroski center", "caprabo",
            "bonarea", "bon area", "consum cooperativa", "mercadona sa", "carrefour express", "carrefour market", "supercor exprés", "spar",
            "covirán", "condis", "el jamon", "family cash", "e leclerc", "leclerc", "makro", "costco",
            "hiperber", "ahorramas", "plusfresc", "tu super", "cash fresh", "froiz", "vegalsa", "uvesco",
            "bm urban", "alimerka", "claudio", "maskom", "mercado", "fruteria", "frutas",
            "verduleria", "carniceria", "charcuteria", "polleria", "pescaderia", "panaderia", "horno", "pasteleria",
            "ultramarinos", "colmado", "badulaque", "alimentacion", "supermercado", "hipermercado", "glovo super", "getir",
            "gorillas",
        ),
    ),
    FOOD(
        "Restaurantes y bares", false,
        listOf(
            "restaurante", "bar", "cafeteria", "cafe", "cena", "comida", "almuerzo", "desayuno", "brunch", "tapas",
            "cervezas", "copas", "glovo", "just eat", "uber eats", "deliveroo", "mcdonald", "mcdonalds", "burger king",
            "telepizza", "dominos", "domino s", "kfc", "starbucks", "foster", "vips", "goiko", "montaditos", "five guys",
            "pizzeria", "pizza", "sushi", "kebab", "popeyes", "llaollao", "100 montaditos", "taco bell", "tgb", "vips smart",
            "panaderia cafeteria", "heladeria", "cerveceria", "taberna", "meson", "asador", "bocateria", "churreria", "pasteleria", "brasa", "tagliatella", "rodilla", "pans company", "tim hortons",
            // Más comercios y palabras (España).
            "foodhall", "telepizza", "papa johns", "pizza hut", "domino s pizza", "dominos pizza", "goiko grill", "lizarran",
            "cervecerias", "vips club", "ginos", "la tagliatella", "foster s hollywood", "fosters", "tgi fridays", "subway",
            "taco bell", "popeyes", "five guys", "carls jr", "tommy mel s", "100 montaditos", "cien montaditos", "la sureña",
            "honest greens", "sibuya", "udon", "wok", "ramen", "poke", "burrito",
            "taqueria", "tacos", "brasa", "asador", "marisqueria", "arroceria", "creperia", "gofres",
            "churros", "heladeria", "llaollao", "smoy", "yogurteria", "starbucks", "tim hortons", "costa coffee",
            "café", "cafeteria", "cafe bar", "granja", "chocolateria", "tasca", "bodega", "vermuteria",
            "terraza", "chiringuito", "glovo", "just eat", "uber eats", "deliveroo", "rodilla", "pans",
            "panaria", "granier", "365 obrador", "manolo bakes", "la mafia", "vicio", "goiko", "burger",
            "hamburgueseria", "kebab", "durum", "pizzeria", "sushi", "mcdonalds", "burger king", "kfc",
            "restaurante", "bar ",
        ),
    ),
    TRANSPORT(
        "Transporte", true,
        listOf(
            "uber", "cabify", "bolt", "freenow", "free now", "taxi", "renfe", "metro", "emt", "tmb", "bus", "autobus",
            "alsa", "blablacar", "telpark", "parking", "aparcamiento", "parquimetro", "zona azul", "ora", "peaje", "autopista", "via t", "itv",
            "cooltra", "acciona", "yego", "bicing", "lime", "moeve", "ballenoil", "plenoil", "petroprix", "bonarea gasolinera", "cargador", "gasolina", "gasoil", "diesel", "gasolinera", "repsol", "cepsa", "bp", "shell", "galp",
            "moeve", "ballenoil", "plenoil", "petronor", "parking", "aparcamiento", "peaje", "itv", "taller",
            "seguro coche", "seguro del coche", "abono transporte", "bicing", "lime",
            // Más comercios y palabras (España).
            "gasolinera", "estacion de servicio", "carburante", "combustible", "galp", "cepsa", "moeve",
            "repsol", "petronor", "ballenoil", "plenoil", "petroprix", "q8", "esso",
            "meroil", "tamoil", "bonarea energia", "cabify", "uber", "bolt", "free now",
            "freenow", "taxi", "radio taxi", "blablacar", "renfe", "cercanias", "rodalies", "metro de madrid",
            "tmb", "emt", "tussam", "bizkaibus", "alsa", "avanza", "monbus", "flixbus",
            "ouigo", "iryo", "trainline", "parking", "empark", "saba", "telpark", "parclick",
            "elparking", "ora", "ser madrid", "peaje", "autopista", "via t", "abertis", "itv",
            "taller", "neumaticos", "norauto", "midas", "feu vert", "lavado", "lavacoches", "cooltra",
            "acciona", "yego", "zity", "share now", "wible", "bicimad", "bicing", "abono",
            "tarjeta transporte", "multa trafico", "dgt",
        ),
    ),
    HOME(
        "Casa y facturas", true,
        listOf(
            "alquiler", "hipoteca", "comunidad", "luz", "agua", "gas", "iberdrola", "endesa", "naturgy", "holaluz",
            "totalenergies", "repsol luz", "movistar", "vodafone", "orange", "digi", "yoigo", "masmovil", "pepephone",
            "lowi", "simyo", "jazztel", "o2", "internet", "fibra", "telefono", "movil", "seguro hogar", "ikea",
            "leroy merlin", "bricomart", "brico", "limpieza", "ibi", "basura",
            // Más comercios y palabras (España).
            "iberdrola", "endesa", "naturgy", "holaluz", "repsol electricidad", "totalenergies", "octopus energy", "lucera",
            "som energia", "factor energia", "aldro", "canal de isabel ii", "aguas de", "emasesa", "aigues de barcelona",
            "movistar", "vodafone", "orange", "o2", "digi", "yoigo", "masmovil", "pepephone",
            "lowi", "simyo", "finetwork", "jazztel", "adamo", "avatel", "ikea", "leroy merlin",
            "bricomart", "bricodepot", "bauhaus", "conforama", "maisons du monde", "zara home", "h m home",
            "jysk", "kave home", "sklum", "el corte ingles hogar", "comunidad propietarios", "administrador de fincas", "seguro hogar", "mapfre hogar",
            "linea directa hogar", "securitas direct", "prosegur",
        ),
    ),
    SUBSCRIPTIONS(
        "Suscripciones", false,
        listOf(
            "netflix", "spotify", "hbo", "hbo max", "disney", "prime video", "amazon prime", "icloud", "apple one",
            "apple music", "youtube premium", "chatgpt", "openai", "claude", "google one", "xbox", "playstation", "ps plus",
            "nintendo", "twitch", "patreon", "dazn", "movistar plus", "filmin", "audible", "kindle unlimited",
            "suscripcion", "crunchyroll", "skyshowtime", "duolingo", "x premium", "x corp", "twitter", "github", "notion", "canva",
            "adobe", "microsoft 365", "office 365", "dropbox", "1password", "nordvpn", "expressvpn", "tinder", "bumble", "strava", "midjourney",
            "anthropic", "max", "atresplayer", "tidal", "deezer", "storytel",
        ),
    ),
    CLOTHES(
        "Ropa", false,
        listOf(
            "zara", "pull bear", "pull and bear", "bershka", "stradivarius", "massimo dutti", "oysho", "lefties",
            "mango", "primark", "h m", "springfield", "cortefiel", "pedro del hierro", "women secret",
            "sfera", "kiabi", "uniqlo", "arket", "weekday", "monki",
            "and other stories", "levis", "levi s", "tommy hilfiger", "calvin klein", "ralph lauren", "lacoste", "hugo boss",
            "guess", "pepe jeans", "desigual", "el ganso", "silbon", "scalpers", "hackett", "polo club",
            "ecoalf", "sepiia", "hoff", "bimba y lola", "bimba lola", "uterque", "parfois", "misako",
            "pdpaola", "singularu", "tous", "eme studios", "eme studio", "scuffers", "twojeys", "two jeys",
            "nude project", "nudeproject", "bluebanana", "blue banana", "sidiver", "bdk", "ellesse", "carhartt",
            "stussy", "supreme", "palace", "kith", "bape", "corteiz", "trapstar", "fear of god",
            "essentials", "represent", "cole buxton", "axel arigato", "golden goose", "veja", "new balance", "nike",
            "adidas", "puma", "reebok", "asics", "vans", "converse", "dr martens", "timberland",
            "clarks", "skechers", "jd sports", "foot locker", "snipes", "courir", "zalando",
            "asos", "about you", "shein", "vinted", "lounge underwear", "intimissimi", "calzedonia", "tezenis",
            "victoria s secret", "pull&bear", "ropa", "camiseta", "camisetas", "pantalon", "pantalones", "vaqueros",
            "sudadera", "chaqueta", "abrigo", "vestido", "falda", "jersey", "camisa", "zapatillas",
            "zapatos", "botas", "calcetines", "gorra", "bolso", "cinturon", "bañador", "zapateria",
            "textil",
        ),
    ),
    SHOPPING(
        "Compras", false,
        listOf(
            "amazon", "el corte ingles", "corte ingles", "aliexpress", "temu", "mediamarkt",
            "media markt", "decathlon", "fnac", 
            "apple store", "pccomponentes", "worten", "sephora", "druni", "primor", 
            "tienda", "compras", 
            "mochila", "auriculares", "movil", "portatil", "consola", "wallapop", "tiger", "action", "normal", 
            "game", "el corte", "carrefour online", "miravia", "apple com",
        ),
    ),
    HEALTH(
        "Salud y deporte", true,
        listOf(
            "farmacia", "medico", "dentista", "clinica", "hospital", "sanitas", "adeslas", "asisa", "dkv", "optica",
            "fisio", "fisioterapia", "psicologo", "peluqueria", "barberia", "estetica", "manicura", "depilacion", "masaje", "podologo",
            "parafarmacia", "mifarma", "atida", "promofarma", "analisis", "laboratorio", "seguro medico", "oculista", "lentillas",
            // Más comercios y palabras (España).
            "farmacia", "farmacias", "botica", "dosfarma", "farmaciasdirect", "primor", "clinica dental", "vitaldent",
            "dentix", "sanitas dental", "quiron", "quironsalud", "hm hospitales", "vithas", "ribera", "mapfre salud",
            "cigna", "fiatc", "general optica", "multiopticas", "alain afflelou", "afflelou", "opticalia", "visionlab",
            "federopticos", "fisioterapeuta", "osteopata", "nutricionista", "dietista", "psicologia", "terapia", "logopeda",
            "podologia", "peluqueria", "barber", "barbershop", "llongueras", "marco aldany", "jean louis david", "nails",
            "spa",
        ),
    ),
    LEISURE(
        "Ocio", false,
        listOf(
            "cine", "cines", "cinesa", "yelmo", "kinepolis", "ocine", "odeon", "multicines", "filmoteca", "concierto", "conciertos",
            "entradas", "entrada", "ticketmaster", "fever", "eventbrite", "dice", "taquilla", "teatro", "musical", "opera", "museo",
            "exposicion", "zoo", "acuario", "parque de atracciones", "port aventura", "portaventura", "warner", "parque",
            "steam", "epic games", "nintendo eshop", "videojuego", "videojuegos", "juego", "juegos", "game", "bolera", "karaoke",
            "karting", "paintball", "laser tag", "escape room", "escape", "discoteca", "pub", "copas", "cocktail", "coctel",
            "fiesta", "festival", "loteria", "apuestas", "casino", "billar", "dardos", "recreativos", "salon de juegos",
            // Más comercios y palabras (España).
            "neocine", "neocines", "cinesa", "yelmo cines", "kinepolis", "ocine", "odeon", "artesiete",
            "cines la dehesa", "cinebox", "mk2", "cines verdi", "golem", "renoir", "multicines", "cines",
            "entradas com", "atrapalo", "ticketea", "taquilla com", "fnac espectaculos", "el corte ingles entradas", "giglon", "wegow",
            "fever", "dice fm", "songkick", "festival", "mad cool", "primavera sound", "sonar", "bbk live",
            "arenal sound", "medusa", "viña rock", "resurrection fest", "discoteca", "pub",
            "copas", "cubata", "botellon", "chupito", "gin tonic", "bowling", "bolera",
            "karting", "laser", "escape room", "parque warner", "port aventura", "terra mitica", "isla magica", "aqualand",
            "aquopolis", "parque acuatico", "faunia", "zoo aquarium", "bioparc", "loro parque", "museo del prado", "reina sofia",
            "thyssen", "museo", "exposicion", "teatro", "musical", "monologo", "comedia", "steam",
            "epic games", "playstation store", "nintendo eshop", "xbox store", "game stop", "game", "twitch bits", "loterias",
            "bet365", "codere", "sportium", "betfair", "pokerstars", "casino",
        ),
    ),
    TRAVEL(
        "Viajes", false,
        listOf(
            "ryanair", "vueling", "iberia", "easyjet", "air europa", "volotea", "booking", "airbnb", "hotel", "hostal",
            "expedia", "edreams", "trainline", "ouigo", "iryo", "viaje", "vuelo", "vacaciones", "apartamento",
            // Más comercios y palabras (España).
            "booking com", "airbnb", "vrbo", "hotels com", "trivago", "rumbo", "destinia", "logitravel",
            "viajes el corte ingles", "halcon viajes", "barcelo", "nh hotel", "melia", "riu", "ibis", "hilton",
            "marriott", "hostel", "albergue", "camping", "parador", "ryanair", "vueling", "iberia express",
            "binter", "wizz air", "transavia", "lufthansa", "air france", "klm", "aena",
            "aeropuerto", "duty free", "maleta", "seguro de viaje", "hertz", "europcar", "sixt", "avis",
            "goldcar", "centauro", "record go", "okmobility", "alquiler coche", "rent a car",
        ),
    ),
    SPORT(
        "Deporte", false,
        listOf(
            "futbol", "futbito", "futbol 7", "futbol sala", "padel", "tenis", "squash", "baloncesto", "voley", "rugby", "golf",
            "gimnasio", "gym", "basic fit", "mcfit", "altafit", "vivagym", "holmes place", "go fit", "dir", "anytime fitness",
            "crossfit", "piscina", "natacion", "yoga", "pilates", "boxeo", "kickboxing", "artes marciales", "escalada", "rocodromo",
            "ski", "esqui", "forfait", "surf", "kitesurf", "running", "maraton", "carrera popular", "dorsal", "triatlon", "ciclismo",
            "bici", "spinning", "playtomic", "polideportivo", "club deportivo", "alquiler de pista", "pista", "liga", "inscripcion",
            "decathlon", "sprinter", "deporte", "deportes", "entrenador", "entrenamiento",
            // Más comercios y palabras (España).
            "basic-fit", "basicfit", "mc fit", "anytime", "altafit", "vivagym", "synergym", "go fit",
            "dreamfit", "o2 centro wellness", "metropolitan", "fitness park", "smartfit", "duet fit", "ifitness", "gym",
            "fitness", "crossfit box", "box crossfit", "playtomic", "padel", "padelnuestro", "ocio deportivo", "rocodromo",
            "sputnik climbing", "boulder", "forfait", "sierra nevada", "baqueira", "formigal", "grandvalira", "dorsal",
            "inscripcion carrera", "runnea", "fitbit",
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
    /** Trozos que, dentro de cualquier palabra, ya dicen el tipo de gasto. */
    private val CONTAINS = listOf(
        "cine" to SpendTag.LEISURE, "teatr" to SpendTag.LEISURE, "concert" to SpendTag.LEISURE, "ticket" to SpendTag.LEISURE,
        "burger" to SpendTag.FOOD, "pizz" to SpendTag.FOOD, "sushi" to SpendTag.FOOD, "kebab" to SpendTag.FOOD, "taqueri" to SpendTag.FOOD,
        "restaur" to SpendTag.FOOD, "cafeter" to SpendTag.FOOD, "cervecer" to SpendTag.FOOD, "bocater" to SpendTag.FOOD, "churrer" to SpendTag.FOOD,
        "gasolin" to SpendTag.TRANSPORT, "parking" to SpendTag.TRANSPORT, "aparcam" to SpendTag.TRANSPORT, "taxi" to SpendTag.TRANSPORT,
        "farmac" to SpendTag.HEALTH, "dental" to SpendTag.HEALTH, "optica" to SpendTag.HEALTH, "peluquer" to SpendTag.HEALTH, "barber" to SpendTag.HEALTH,
        "fitness" to SpendTag.SPORT, "padel" to SpendTag.SPORT, "gimnas" to SpendTag.SPORT,
        "supermerc" to SpendTag.SUPER, "hipermerc" to SpendTag.SUPER, "fruter" to SpendTag.SUPER, "carnicer" to SpendTag.SUPER, "panader" to SpendTag.SUPER,
        "zapater" to SpendTag.CLOTHES, "textil" to SpendTag.CLOTHES,
        "hotel" to SpendTag.TRAVEL, "hostal" to SpendTag.TRAVEL, "airline" to SpendTag.TRAVEL,
        "veterin" to SpendTag.PETS, "libreri" to SpendTag.EDUCATION, "floris" to SpendTag.GIFTS,
    )

    /** Minúsculas, sin tildes y solo letras y números separados por un espacio. */
    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

    // Las frases más largas primero: "uber eats" gana a "uber" y "seguro coche" a "seguro".
    private val index: List<Pair<String, SpendTag>> by lazy {
        val own = SpendTag.entries.flatMap { t -> t.keywords.map { normalize(it) to t } }
        val known = own.map { it.first }.toSet()
        // Diccionario amplio de comercios (importado): suma lo que la app no tenía; las reglas propias ganan.
        val extra = MerchantDictionary.WORDS.flatMap { (t, ws) -> ws.map { normalize(it) to t } }.filter { it.first !in known }
        (own + extra).sortedByDescending { it.first.length }
    }

    /** Tipo de gasto por palabras clave, o null si no se reconoce. */
    fun guess(note: String): SpendTag? {
        if (note.isBlank()) return null
        // Primero con el nombre limpio ("MPVinted*" → "Vinted"), luego con el texto tal cual.
        val exact = listOf(Merchants.clean(note), note).firstNotNullOfOrNull { t ->
            val n = " ${normalize(t)} "
            if (n.isBlank()) null else index.firstOrNull { (kw, _) -> n.contains(" $kw ") }?.second
        }
        if (exact != null) return exact
        // Trozos de palabra que delatan el tipo aunque vayan pegados a otra cosa: "Neocine", "Multicines", "BurgerLab"…
        val joined = normalize(Merchants.clean(note))
        (CONTAINS + MerchantDictionary.FRAGMENTS).firstOrNull { (frag, _) -> joined.split(' ').any { w -> frag in w } }?.let { return it.second }
        val words = normalize(note).split(' ').filter { it.length >= 4 }
        return index.firstOrNull { (kw, _) -> kw.length >= 4 && ' ' !in kw && words.any { w -> w.startsWith(kw) || (kw.startsWith(w) && w.length >= 5) } }?.second
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
