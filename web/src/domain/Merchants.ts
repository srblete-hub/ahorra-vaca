// Port de domain/Merchants.kt: nombre limpio del comercio, su logo y gastos duplicados.
import type { Expense } from '../data/models'
import { groupBy } from './kt'
import { Classifier } from './Spend'

/** Intermediarios de pago que van delante del comercio de verdad. */
const PREFIX = /^(?:mp|pp|paypal|sq|sumup|sp|ztl|iz|izettle|zettle|google|gpay|apple ?pay|stripe|tpv|compra en|compra|pago en|pago|cargo|recibo|bizum|wl|cbk)\s*[*/:\-]+\s*/i
/** "MPVinted": el prefijo pegado al nombre (mayúsculas seguidas de Mayúscula+minúscula). */
const GLUED = /^(?:MP|PP|SQ|SP|ZTL|IZ|WL)(?=[A-Z][a-z])/

/** Marcas conocidas: patrón (sobre el texto normalizado) → nombre bonito. */
const KNOWN: Array<[RegExp, string]> = ([
  ['amzn|amazon', 'Amazon'], ['apple com|itunes|apple bill', 'Apple'], ['youtube', 'YouTube'], ['uber eats', 'Uber Eats'],
  ['uber', 'Uber'], ['netflix', 'Netflix'], ['spotify', 'Spotify'], ['vinted', 'Vinted'], ['wallapop', 'Wallapop'],
  ['mercadona', 'Mercadona'], ['lidl', 'Lidl'], ['carrefour', 'Carrefour'], ['aldi', 'Aldi'], ['eroski', 'Eroski'],
  ['alcampo', 'Alcampo'], ['dia supermercado|supermercados dia', 'Dia'], ['consum', 'Consum'], ['ahorramas', 'Ahorramas'],
  ['glovo', 'Glovo'], ['just eat', 'Just Eat'], ['deliveroo', 'Deliveroo'], ['telpark', 'Telpark'], ['repsol', 'Repsol'],
  ['cepsa|moeve', 'Cepsa'], ['galp', 'Galp'], ['openai|chatgpt', 'ChatGPT'], ['anthropic|claude ai', 'Claude'],
  ['x corp|twitter', 'X'], ['steam|valve', 'Steam'], ['renfe', 'Renfe'], ['ryanair', 'Ryanair'], ['vueling', 'Vueling'],
  ['iberia', 'Iberia'], ['booking', 'Booking'], ['airbnb', 'Airbnb'], ['zara', 'Zara'], ['primark', 'Primark'],
  ['decathlon', 'Decathlon'], ['ikea', 'IKEA'], ['mcdonald', "McDonald's"], ['burger king', 'Burger King'],
  ['starbucks', 'Starbucks'], ['bolt', 'Bolt'], ['cabify', 'Cabify'], ['free ?now', 'FreeNow'], ['lime', 'Lime'],
  ['telepizza', 'Telepizza'], ['domino', "Domino's"], ['aliexpress', 'AliExpress'], ['shein', 'Shein'], ['temu', 'Temu'],
  ['corte ingles', 'El Corte Inglés'], ['media ?markt', 'MediaMarkt'], ['pccomponentes', 'PcComponentes'],
  ['disney', 'Disney+'], ['hbo|max com', 'Max'], ['prime video|amazon prime', 'Amazon Prime'], ['icloud', 'iCloud'],
  ['playstation|psn', 'PlayStation'], ['xbox|microsoft', 'Microsoft'], ['google one|google storage', 'Google One'],
  ['movistar', 'Movistar'], ['vodafone', 'Vodafone'], ['orange', 'Orange'], ['digi', 'Digi'], ['iberdrola', 'Iberdrola'],
  ['endesa', 'Endesa'], ['naturgy', 'Naturgy'], ['holaluz', 'Holaluz'], ['basic ?fit', 'Basic-Fit'], ['druni', 'Druni'],
  ['sephora', 'Sephora'], ['primor', 'Primor'], ['fnac', 'Fnac'], ['leroy merlin', 'Leroy Merlin'], ['cinesa', 'Cinesa'],
  ['yelmo', 'Yelmo'], ['ticketmaster', 'Ticketmaster'], ['fever', 'Fever'], ['revolut', 'Revolut'],
] as Array<[string, string]>).map(([p, n]) => [new RegExp(`(^| )(?:${p})( |$)`), n])

/** Web de cada marca, para pedir su logo (servicio gratuito de iconos de webs). */
const DOMAINS: Record<string, string> = {
  'Amazon': 'amazon.es', 'Apple': 'apple.com', 'YouTube': 'youtube.com', 'Uber Eats': 'ubereats.com', 'Uber': 'uber.com',
  'Netflix': 'netflix.com', 'Spotify': 'spotify.com', 'Vinted': 'vinted.es', 'Wallapop': 'wallapop.com', 'Mercadona': 'mercadona.es',
  'Lidl': 'lidl.es', 'Carrefour': 'carrefour.es', 'Aldi': 'aldi.es', 'Eroski': 'eroski.es', 'Alcampo': 'alcampo.es', 'Dia': 'dia.es',
  'Consum': 'consum.es', 'Ahorramas': 'ahorramas.com', 'Glovo': 'glovoapp.com', 'Just Eat': 'just-eat.es', 'Deliveroo': 'deliveroo.es',
  'Telpark': 'telpark.com', 'Repsol': 'repsol.es', 'Cepsa': 'moeveglobal.com', 'Galp': 'galp.com', 'ChatGPT': 'openai.com',
  'Claude': 'claude.ai', 'X': 'x.com', 'Steam': 'steampowered.com', 'Renfe': 'renfe.com', 'Ryanair': 'ryanair.com',
  'Vueling': 'vueling.com', 'Iberia': 'iberia.com', 'Booking': 'booking.com', 'Airbnb': 'airbnb.es', 'Zara': 'zara.com',
  'Primark': 'primark.com', 'Decathlon': 'decathlon.es', 'IKEA': 'ikea.com', "McDonald's": 'mcdonalds.es', 'Burger King': 'burgerking.es',
  'Starbucks': 'starbucks.es', 'Bolt': 'bolt.eu', 'Cabify': 'cabify.com', 'FreeNow': 'free-now.com', 'Lime': 'li.me',
  'Telepizza': 'telepizza.es', "Domino's": 'dominospizza.es', 'AliExpress': 'aliexpress.com', 'Shein': 'shein.com', 'Temu': 'temu.com',
  'El Corte Inglés': 'elcorteingles.es', 'MediaMarkt': 'mediamarkt.es', 'PcComponentes': 'pccomponentes.com', 'Disney+': 'disneyplus.com',
  'Max': 'max.com', 'Amazon Prime': 'primevideo.com', 'iCloud': 'icloud.com', 'PlayStation': 'playstation.com', 'Microsoft': 'microsoft.com',
  'Google One': 'one.google.com', 'Movistar': 'movistar.es', 'Vodafone': 'vodafone.es', 'Orange': 'orange.es', 'Digi': 'digimobil.es',
  'Iberdrola': 'iberdrola.es', 'Endesa': 'endesa.com', 'Naturgy': 'naturgy.es', 'Holaluz': 'holaluz.com', 'Basic-Fit': 'basic-fit.com',
  'Druni': 'druni.es', 'Sephora': 'sephora.es', 'Primor': 'primor.eu', 'Fnac': 'fnac.es', 'Leroy Merlin': 'leroymerlin.es',
  'Cinesa': 'cinesa.es', 'Yelmo': 'yelmocines.es', 'Ticketmaster': 'ticketmaster.es', 'Fever': 'feverup.com', 'Revolut': 'revolut.com',
}

/** Palabras que no son una marca: con ellas no se adivina la web. */
const GENERIC = new Set(['bar', 'cafe', 'cafeteria', 'restaurante', 'tienda', 'gasolinera', 'farmacia', 'supermercado', 'panaderia', 'pago',
  'compra', 'transferencia', 'bizum', 'recibo', 'ingreso', 'gasto', 'comida', 'cena', 'desayuno', 'taxi', 'parking', 'otros', 'efectivo'])

const NOISE = new Set(['es', 'esp', 'spain', 'sl', 'sa', 'slu', 's', 'l', 'a', 'madrid', 'barcelona', 'valencia', 'sevilla', 'www', 'com', 'eu', 'mktp', 'marketplace', 'inc', 'ltd', 'online'])

const isLetter = (c: string) => /\p{L}/u.test(c)
const isDigit = (c: string) => /\p{Nd}/u.test(c)
const isLetterOrDigit = (c: string) => isLetter(c) || isDigit(c)

function trimChars(s: string, chars: string): string {
  let a = 0
  let b = s.length
  while (a < b && chars.includes(s[a])) a++
  while (b > a && chars.includes(s[b - 1])) b--
  return s.slice(a, b)
}

/**
 * Nombre limpio del comercio a partir de lo que pone el banco o la cartera:
 * "MPVinted*" → "Vinted", "PAYPAL *STEAM GAMES" → "Steam", "AMZN Mktp ES*2K4" → "Amazon",
 * "SUMUP *BAR PEPE" → "Bar Pepe", "MERCADONA 2345 MADRID" → "Mercadona".
 */
export const Merchants = {
  /** Logo del comercio (si es conocido), gratis y sin clave. */
  logoUrl(note: string): string | null {
    const d = DOMAINS[Merchants.clean(note)]
    return d ? `https://www.google.com/s2/favicons?domain=${d}&sz=128` : null
  },

  /**
   * Logos a probar, en orden: el de la marca conocida y, si no lo es, su web adivinada por el nombre
   * ("Telpark" → telpark.es / telpark.com). El servicio de iconos devuelve error si la web no existe,
   * y entonces se pone el icono de la categoría.
   */
  logoUrls(note: string): string[] {
    const known = Merchants.logoUrl(note)
    if (known) return [known]
    const name = Classifier.normalize(Merchants.clean(note))
    const words = name.split(' ').filter((w) => w.trim() !== '')
    if (words.length === 0 || words.length > 3 || words.every((w) => GENERIC.has(w)) || GENERIC.has(words[0])) return []
    const slug = words.join('')
    if (slug.length < 3 || [...slug].some((c) => !isLetterOrDigit(c))) return []
    return ['es', 'com'].map((tld) => `https://icons.duckduckgo.com/ip3/${slug}.${tld}.ico`)
  },

  clean(raw: string): string {
    let s = trimChars(raw.trim(), '"\'')
    if (s === '') return s
    s = s.replace(GLUED, '')
    for (let i = 0; i < 2; i++) s = s.replace(PREFIX, '')
    const n = Classifier.normalize(s)
    const known = KNOWN.find(([re]) => re.test(n))
    if (known) return known[1]
    // "VINTED*12345": lo de antes del asterisco; si no hay nada, lo de después.
    s = s.split('*').map((p) => p.trim()).find((p) => [...p].some(isLetter)) ?? s
    const words = s.split(/\s+/).filter((w) => [...w].some(isLetter) && [...w].filter(isDigit).length <= 1)
    while (words.length > 0 && NOISE.has(Classifier.normalize(words[words.length - 1]))) words.pop()
    let out = trimChars(words.join(' ').trim(), ' .,-*')
    if (out.trim() === '') out = s.trim()
    if (out === out.toUpperCase() && [...out].some(isLetter)) {
      return out.toLowerCase().split(' ').map((w) => (w ? w[0].toUpperCase() + w.slice(1) : w)).join(' ')
    }
    return out
  },
}

/** Gastos repetidos: mismo importe con menos de 1 minuto de diferencia y alguno leído de un aviso. Se queda el primero. */
export const Duplicates = {
  WINDOW_MS: 60_000,

  extras(list: Expense[]): Expense[] {
    const out: Expense[] = []
    groupBy(list, (e) => Math.round(e.amount * 100)).forEach((same) => {
      if (same.length < 2) return
      const sorted = [...same].sort((a, b) => a.createdAt - b.createdAt || a.id - b.id)
      let keep = sorted[0]
      for (const e of sorted.slice(1)) {
        // Solo si alguno llegó por un aviso de pago (banco o cartera): lo que apuntas tú a mano no se toca.
        if (Math.abs(e.createdAt - keep.createdAt) < Duplicates.WINDOW_MS && e.epochDay === keep.epochDay && (e.fromCard || keep.fromCard)) out.push(e)
        else keep = e
      }
    })
    return out
  },
}
