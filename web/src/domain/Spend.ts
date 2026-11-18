// Port de domain/Spend.kt: tipos de gasto, clasificador por palabras clave y gastos escritos como frase.
import type { Category, Expense } from '../data/models'
import type { LocalDate } from '../data/dates'
import { capFirst, minusD } from './kt'
import { Merchants } from './Merchants'

/**
 * En qué se va el dinero. `need` separa lo necesario (va a "Necesidades") de lo prescindible
 * (va a "Ocio y caprichos"). Las palabras clave se buscan como palabras enteras, sin tildes.
 */
export type SpendTag =
  | 'SUPER' | 'FOOD' | 'TRANSPORT' | 'HOME' | 'SUBSCRIPTIONS' | 'SHOPPING' | 'HEALTH' | 'LEISURE' | 'TRAVEL' | 'SPORT'
  | 'GIFTS' | 'EDUCATION' | 'PETS' | 'OTHER'

export const SPEND_TAG_VALUES: SpendTag[] = [
  'SUPER', 'FOOD', 'TRANSPORT', 'HOME', 'SUBSCRIPTIONS', 'SHOPPING', 'HEALTH', 'LEISURE', 'TRAVEL', 'SPORT', 'GIFTS', 'EDUCATION', 'PETS', 'OTHER',
]

export const SPEND_TAG_INFO: Record<SpendTag, { label: string; need: boolean; keywords: string[] }> = {
  SUPER: {
    label: 'Supermercado', need: true,
    keywords: [
      'mercadona', 'lidl', 'carrefour', 'aldi', 'alcampo', 'eroski', 'consum', 'hipercor', 'supercor', 'bonpreu',
      'condis', 'ahorramas', 'froiz', 'gadis', 'caprabo', 'spar', 'coviran', 'dia', 'supermercado', 'super',
      'la compra', 'compra semanal', 'fruteria', 'carniceria', 'panaderia', 'pescaderia', 'mercado',
    ],
  },
  FOOD: {
    label: 'Restaurantes y bares', need: false,
    keywords: [
      'restaurante', 'bar', 'cafeteria', 'cafe', 'cena', 'comida', 'almuerzo', 'desayuno', 'brunch', 'tapas',
      'cervezas', 'copas', 'glovo', 'just eat', 'uber eats', 'deliveroo', 'mcdonald', 'mcdonalds', 'burger king',
      'telepizza', 'dominos', 'domino s', 'kfc', 'starbucks', 'foster', 'vips', 'goiko', 'montaditos', 'five guys',
      'pizzeria', 'pizza', 'sushi', 'kebab', 'popeyes', 'llaollao', '100 montaditos', 'taco bell', 'tgb', 'vips smart',
      'panaderia cafeteria', 'heladeria', 'cerveceria', 'taberna', 'meson', 'asador', 'bocateria', 'churreria', 'pasteleria', 'brasa', 'tagliatella', 'rodilla', 'pans company', 'tim hortons',
    ],
  },
  TRANSPORT: {
    label: 'Transporte', need: true,
    keywords: [
      'uber', 'cabify', 'bolt', 'freenow', 'free now', 'taxi', 'renfe', 'metro', 'emt', 'tmb', 'bus', 'autobus',
      'alsa', 'blablacar', 'telpark', 'parking', 'aparcamiento', 'parquimetro', 'zona azul', 'ora', 'peaje', 'autopista', 'via t', 'itv',
      'cooltra', 'acciona', 'yego', 'bicing', 'lime', 'moeve', 'ballenoil', 'plenoil', 'petroprix', 'bonarea gasolinera', 'cargador', 'gasolina', 'gasoil', 'diesel', 'gasolinera', 'repsol', 'cepsa', 'bp', 'shell', 'galp',
      'moeve', 'ballenoil', 'plenoil', 'petronor', 'parking', 'aparcamiento', 'peaje', 'itv', 'taller',
      'seguro coche', 'seguro del coche', 'abono transporte', 'bicing', 'lime',
    ],
  },
  HOME: {
    label: 'Casa y facturas', need: true,
    keywords: [
      'alquiler', 'hipoteca', 'comunidad', 'luz', 'agua', 'gas', 'iberdrola', 'endesa', 'naturgy', 'holaluz',
      'totalenergies', 'repsol luz', 'movistar', 'vodafone', 'orange', 'digi', 'yoigo', 'masmovil', 'pepephone',
      'lowi', 'simyo', 'jazztel', 'o2', 'internet', 'fibra', 'telefono', 'movil', 'seguro hogar', 'ikea',
      'leroy merlin', 'bricomart', 'brico', 'limpieza', 'ibi', 'basura',
    ],
  },
  SUBSCRIPTIONS: {
    label: 'Suscripciones', need: false,
    keywords: [
      'netflix', 'spotify', 'hbo', 'hbo max', 'disney', 'prime video', 'amazon prime', 'icloud', 'apple one',
      'apple music', 'youtube premium', 'chatgpt', 'openai', 'claude', 'google one', 'xbox', 'playstation', 'ps plus',
      'nintendo', 'twitch', 'patreon', 'dazn', 'movistar plus', 'filmin', 'audible', 'kindle unlimited',
      'suscripcion', 'crunchyroll', 'skyshowtime', 'duolingo', 'x premium', 'x corp', 'twitter', 'github', 'notion', 'canva',
      'adobe', 'microsoft 365', 'office 365', 'dropbox', '1password', 'nordvpn', 'expressvpn', 'tinder', 'bumble', 'strava', 'midjourney',
      'anthropic', 'max', 'atresplayer', 'tidal', 'deezer', 'storytel',
    ],
  },
  SHOPPING: {
    label: 'Compras', need: false,
    keywords: [
      'amazon', 'zara', 'primark', 'el corte ingles', 'corte ingles', 'shein', 'aliexpress', 'temu', 'mediamarkt',
      'media markt', 'decathlon', 'fnac', 'pull bear', 'bershka', 'mango', 'h m', 'stradivarius', 'massimo dutti',
      'nike', 'adidas', 'apple store', 'pccomponentes', 'worten', 'sephora', 'druni', 'primor', 'ropa', 'zapatillas',
      'zapatos', 'tienda', 'compras', 'camiseta', 'camisetas', 'pantalon', 'pantalones', 'sudadera', 'chaqueta', 'abrigo', 'vestido',
      'falda', 'jersey', 'gorra', 'bolso', 'mochila', 'auriculares', 'movil', 'portatil', 'consola', 'vinted', 'wallapop', 'tiger', 'action', 'normal', 'lefties', 'springfield', 'pull and bear',
      'oysho', 'uniqlo', 'jd sports', 'foot locker', 'game', 'el corte', 'carrefour online', 'miravia', 'zalando', 'asos', 'apple com',
    ],
  },
  HEALTH: {
    label: 'Salud y deporte', need: true,
    keywords: [
      'farmacia', 'medico', 'dentista', 'clinica', 'hospital', 'sanitas', 'adeslas', 'asisa', 'dkv', 'optica',
      'fisio', 'fisioterapia', 'psicologo', 'peluqueria', 'barberia', 'estetica', 'manicura', 'depilacion', 'masaje', 'podologo',
      'parafarmacia', 'mifarma', 'atida', 'promofarma', 'analisis', 'laboratorio', 'seguro medico', 'oculista', 'lentillas',
    ],
  },
  LEISURE: {
    label: 'Ocio', need: false,
    keywords: [
      'cine', 'cines', 'cinesa', 'yelmo', 'kinepolis', 'ocine', 'odeon', 'multicines', 'filmoteca', 'concierto', 'conciertos',
      'entradas', 'entrada', 'ticketmaster', 'fever', 'eventbrite', 'dice', 'taquilla', 'teatro', 'musical', 'opera', 'museo',
      'exposicion', 'zoo', 'acuario', 'parque de atracciones', 'port aventura', 'portaventura', 'warner', 'parque',
      'steam', 'epic games', 'nintendo eshop', 'videojuego', 'videojuegos', 'juego', 'juegos', 'game', 'bolera', 'karaoke',
      'karting', 'paintball', 'laser tag', 'escape room', 'escape', 'discoteca', 'pub', 'copas', 'cocktail', 'coctel',
      'fiesta', 'festival', 'loteria', 'apuestas', 'casino', 'billar', 'dardos', 'recreativos', 'salon de juegos',
    ],
  },
  TRAVEL: {
    label: 'Viajes', need: false,
    keywords: [
      'ryanair', 'vueling', 'iberia', 'easyjet', 'air europa', 'volotea', 'booking', 'airbnb', 'hotel', 'hostal',
      'expedia', 'edreams', 'trainline', 'ouigo', 'iryo', 'viaje', 'vuelo', 'vacaciones', 'apartamento',
    ],
  },
  SPORT: {
    label: 'Deporte', need: false,
    keywords: [
      'futbol', 'futbito', 'futbol 7', 'futbol sala', 'padel', 'tenis', 'squash', 'baloncesto', 'voley', 'rugby', 'golf',
      'gimnasio', 'gym', 'basic fit', 'mcfit', 'altafit', 'vivagym', 'holmes place', 'go fit', 'dir', 'anytime fitness',
      'crossfit', 'piscina', 'natacion', 'yoga', 'pilates', 'boxeo', 'kickboxing', 'artes marciales', 'escalada', 'rocodromo',
      'ski', 'esqui', 'forfait', 'surf', 'kitesurf', 'running', 'maraton', 'carrera popular', 'dorsal', 'triatlon', 'ciclismo',
      'bici', 'spinning', 'playtomic', 'polideportivo', 'club deportivo', 'alquiler de pista', 'pista', 'liga', 'inscripcion',
      'decathlon', 'sprinter', 'deporte', 'deportes', 'entrenador', 'entrenamiento',
    ],
  },
  GIFTS: { label: 'Regalos', need: false, keywords: ['regalo', 'regalos', 'cumpleanos', 'boda', 'flores', 'floristeria', 'detalle'] },
  EDUCATION: { label: 'Formación', need: true, keywords: ['curso', 'academia', 'udemy', 'libro', 'libros', 'libreria', 'universidad', 'matricula', 'coursera', 'platzi'] },
  PETS: { label: 'Mascotas', need: true, keywords: ['veterinario', 'tiendanimal', 'kiwoko', 'mascota', 'pienso', 'perro', 'gato'] },
  OTHER: { label: 'Otros', need: false, keywords: [] },
}

/** SpendTag.of(name): el tipo por su nombre, o null. */
export function spendTagOf(name: string | null | undefined): SpendTag | null {
  return name != null && (SPEND_TAG_VALUES as string[]).includes(name) ? (name as SpendTag) : null
}
export const tagLabel = (t: SpendTag): string => SPEND_TAG_INFO[t].label
export const tagNeed = (t: SpendTag): boolean => SPEND_TAG_INFO[t].need

/** Minúsculas, sin tildes y solo letras y números separados por un espacio. */
function normalize(text: string): string {
  return text.toLowerCase().normalize('NFD').replace(/\p{M}+/gu, '').replace(/[^a-z0-9]+/g, ' ').trim()
}

// Las frases más largas primero: "uber eats" gana a "uber" y "seguro coche" a "seguro".
let indexCache: Array<[string, SpendTag]> | null = null
function index(): Array<[string, SpendTag]> {
  if (!indexCache) {
    const list: Array<[string, SpendTag]> = []
    for (const t of SPEND_TAG_VALUES) for (const k of SPEND_TAG_INFO[t].keywords) list.push([normalize(k), t])
    indexCache = list.sort((a, b) => b[0].length - a[0].length)
  }
  return indexCache
}

const guessCache = new Map<string, SpendTag | null>()

function guessUncached(note: string): SpendTag | null {
  if (note.trim() === '') return null
  // Primero con el nombre limpio ("MPVinted*" → "Vinted"), luego con el texto tal cual.
  for (const t of [Merchants.clean(note), note]) {
    const n = ` ${normalize(t)} `
    if (n.trim() === '') continue
    const hit = index().find(([kw]) => n.includes(` ${kw} `))
    if (hit) return hit[1]
  }
  const words = normalize(note).split(' ').filter((w) => w.length >= 4)
  const hit = index().find(([kw]) => kw.length >= 4 && !kw.includes(' ') && words.some((w) => w.startsWith(kw) || (kw.startsWith(w) && w.length >= 5)))
  return hit ? hit[1] : null
}

export const Classifier = {
  normalize,

  /** Tipo de gasto por palabras clave, o null si no se reconoce. */
  guess(note: string): SpendTag | null {
    // El resultado solo depende del texto: se guarda para no repetir la búsqueda en cada análisis.
    const cached = guessCache.get(note)
    if (cached !== undefined) return cached
    const t = guessUncached(note)
    if (guessCache.size > 5000) guessCache.clear()
    guessCache.set(note, t)
    return t
  },

  /**
   * Primero aprende de ti: si ya clasificaste un gasto con la misma descripción, usa ese tipo.
   * Si no, por palabras clave; y si nada encaja, "Otros".
   */
  classify(note: string, history: Expense[] = []): SpendTag {
    const n = normalize(note)
    if (n !== '') {
      for (let i = history.length - 1; i >= 0; i--) {
        const e = history[i]
        if (e.tag.trim() !== '' && normalize(e.note) === n) {
          const t = spendTagOf(e.tag)
          if (t) return t
          break
        }
      }
    }
    return Classifier.guess(note) ?? 'OTHER'
  },

  /** Categoría del reparto que le toca a un tipo de gasto: necesidades u ocio. Null = sin preferencia. */
  bucketFor(tag: SpendTag, categories: Category[]): number | null {
    if (tag === 'OTHER') return null
    const spending = categories.filter((c) => !c.isSavings)
    if (spending.length === 0) return null
    if (SPEND_TAG_INFO[tag].need) {
      return (spending.find((c) => normalize(c.name).includes('necesidad')) ?? spending[0]).id
    }
    return (spending.find((c) => { const n = normalize(c.name); return n.includes('ocio') || n.includes('capricho') }) ?? spending[1] ?? spending[0]).id
  },
}

/** Tipo de un gasto: el elegido, o el que se deduce de su descripción (Expense.spendTag() en Kotlin). */
export function spendTag(e: Expense): SpendTag {
  return spendTagOf(e.tag) ?? Classifier.guess(e.note) ?? 'OTHER'
}

/** Un gasto leído de una frase: "18 € en una cena con amigos". daysAgo = 1 si dice "ayer". */
export interface ParsedExpense { amount: number; note: string; tag: SpendTag; daysAgo: number }
/** ParsedExpense.date(today). */
export function parsedExpenseDate(p: ParsedExpense, today: LocalDate): LocalDate { return minusD(today, p.daysAgo) }

const AMOUNT = /(\d+(?:[.,]\d{1,2})?)\s*(€|euros?|eur|pavos)?/i
const SEPARATORS = /\s+y\s+|\s+e\s+|,(?!\d)|;|\+|\n|\s+m[aá]s\s+|\s+tambi[eé]n\s+/i
const LEADING = new RegExp(
  '^(?:(?:hoy|ayer|esta (?:ma[ñn]ana|tarde|noche)|anoche)\\s+)?(?:me\\s+)?(?:he\\s+)?' +
    '(?:pagado|gastado|gast[eé]|pagu[eé]|comprado|compr[eé]|apunta|apuntar|a[ñn]ade)?\\s*',
  'i',
)
const FILLER = /^(?:(?:en|de|por|para|un|una|unos|unas|el|la|los|las|al|del|me|he|euros?|€)\s+)+/gi

function trimEndChars(s: string, chars: string): string {
  let i = s.length
  while (i > 0 && chars.includes(s[i - 1])) i--
  return s.slice(0, i)
}

/**
 * Apuntar gastos escribiendo como hablas:
 * "He pagado 18 € en una cena con amigos y 12 € en gasolina" → dos gastos ya clasificados.
 */
export const NaturalEntry = {
  parse(text: string, history: Expense[] = []): ParsedExpense[] {
    const yesterday = /\b(ayer|anoche)\b/i.test(text)
    const clauses = text.split(new RegExp(SEPARATORS.source, 'gi')).map((c) => c.trim()).filter((c) => c !== '')
    // Las partes sin importe se pegan a la siguiente que lo tenga ("pan y leche 5 €").
    const merged: string[] = []
    let pending = ''
    for (const c of clauses) {
      if (AMOUNT.exec(c)?.[1] != null && /\d/.test(c)) {
        merged.push(pending !== '' ? `${pending} y ${c}` : c); pending = ''
      } else if (merged.length === 0) pending = pending === '' ? c : `${pending} y ${c}`
      else merged[merged.length - 1] = merged[merged.length - 1] + ' y ' + c
    }
    const out: ParsedExpense[] = []
    for (const clause of merged) {
      const m = AMOUNT.exec(clause)
      if (!m) continue
      const amount = Number(m[1].replace(',', '.'))
      if (!Number.isFinite(amount) || !(amount > 0)) continue
      let note = (clause.slice(0, m.index) + clause.slice(m.index + m[0].length)).replace(/\s+/g, ' ').trim()
      note = note.replace(LEADING, '').trim()
      note = trimEndChars(note.replace(FILLER, '').trim(), '.!,')
      note = capFirst(note)
      out.push({ amount, note: note.trim() === '' ? 'Gasto' : note, tag: Classifier.classify(note, history), daysAgo: yesterday ? 1 : 0 })
    }
    return out
  },
}
