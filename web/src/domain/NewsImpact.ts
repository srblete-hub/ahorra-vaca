// Port de domain/NewsImpact.kt: "¿Cómo puede afectarme?" una noticia, con reglas fijas (sin IA).
import { ACCOUNT_TYPES } from '../data/models'
import { distinct, ep, fixed, maxBy, sumOf } from './kt'
import { spendTag } from './Spend'
import type { SpendTag } from './Spend'
import type { FinanceData } from './Insights'

/** Cómo te toca una noticia: un titular corto y las cuentas hechas con tus datos. */
export interface NewsImpact { relevant: boolean; headline: string; lines: string[]; tags: string[] }

const DOWN = ['cae', 'caen', 'caida', 'baja', 'bajan', 'bajada', 'desploma', 'desplome', 'hunde', 'hundimiento', 'pierde', 'pierden', 'recorta', 'retrocede', 'crash', 'plunge', 'drop', 'falls', 'se deja', 'minimos', 'abarata', 'recorte', 'rebaja']
const UP = ['sube', 'suben', 'subida', 'dispara', 'se dispara', 'gana', 'ganan', 'record', 'maximos', 'rally', 'repunta', 'avanza', 'escala', 'surge', 'rises', 'encarece', 'alza', 'revaloriza']
const CRYPTO_NAMES: Array<[string, string[]]> = [
  ['BTC', ['bitcoin', 'btc']], ['ETH', ['ethereum', 'ether', 'eth']], ['SOL', ['solana']],
  ['XRP', ['xrp', 'ripple']], ['BNB', ['bnb', 'binance coin']], ['DOGE', ['dogecoin', 'doge']],
  ['ADA', ['cardano']], ['DOT', ['polkadot']], ['LTC', ['litecoin']], ['AVAX', ['avalanche']],
  ['LINK', ['chainlink']], ['SHIB', ['shiba']], ['PEPE', ['pepe']], ['SUI', ['sui']], ['TRX', ['tron']],
]
const cryptoNamesOf = (sym: string) => CRYPTO_NAMES.find(([s]) => s === sym)?.[1] ?? []

const escapeRe = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
function has(text: string, word: string): boolean { return new RegExp(`(^|[^a-z0-9])${escapeRe(word)}([^a-z0-9]|$)`).test(text) }

/**
 * "¿Cómo puede afectarme?": relaciona una noticia con lo que tienes en la app con reglas fijas (sin IA).
 * Busca en el titular tus criptos y acciones, tus bancos, los tipos de interés, la inflación, la gasolina,
 * la luz y la vivienda, y hace la cuenta con tus saldos y tus gastos reales. Nunca dice qué comprar o vender.
 */
export const NewsImpactAnalyzer = {
  /** Símbolos de las criptos que nombra un texto (para ponerle su gráfico a la noticia). */
  coinsIn(text: string): string[] {
    const t = NewsImpactAnalyzer.norm(text)
    return CRYPTO_NAMES.filter(([, names]) => names.some((n) => has(t, n))).map(([s]) => s)
  },

  norm(s: string): string { return s.toLowerCase().normalize('NFD').replace(/\p{M}/gu, '') },

  /** Primer porcentaje del titular ("cae un 7,5 %" → 7,5). */
  percentIn(text: string): number | null {
    const m = /(\d{1,3}(?:[.,]\d+)?)\s?%/.exec(text)
    return m ? Number(m[1].replace(',', '.')) : null
  },

  /** −1 si la noticia habla de bajadas, +1 de subidas, 0 si no está claro. */
  direction(text: string): number {
    const d = DOWN.filter((w) => has(text, w)).length; const u = UP.filter((w) => has(text, w)).length
    return d > u ? -1 : u > d ? 1 : 0
  },

  analyze(title: string, summary: string, d: FinanceData, fmt: (v: number) => string): NewsImpact {
    const norm = NewsImpactAnalyzer.norm
    const text = norm(`${title} ${summary}`)
    const dir = NewsImpactAnalyzer.direction(text)
    const pct = NewsImpactAnalyzer.percentIn(text)
    const lines: string[] = []
    const tags: string[] = []
    const total = d.wealth.total
    const share = (v: number) => (total > 0 ? ` (el ${Math.trunc(v / total * 100)} % de tu patrimonio)` : '')
    const pc = (v: number) => `${fixed(v, 1)} %`.replace(',0 %', ' %')

    // ---------- Tus criptos ----------
    const cryptoHits = d.crypto.filter((h) => {
      const names = [...cryptoNamesOf(h.symbol.toUpperCase()), h.name.toLowerCase(), h.symbol.toLowerCase()]
      return names.some((n) => n.length >= 3 && has(text, norm(n)))
    })
    for (const h of cryptoHits) {
      const v = h.amount * h.price
      tags.push(h.symbol)
      const scen = pct ?? 10.0
      lines.push(`Tienes ${fmt(v)} en ${h.name}${share(v)}.`)
      lines.push(dir === -1 ? `Si baja un ${pc(scen)}${pct == null ? ' (ejemplo)' : ''}, perderías unos ${fmt(v * scen / 100)}: tu patrimonio pasaría de ${fmt(total)} a ${fmt(total - v * scen / 100)}.`
        : dir === 1 ? `Si sube un ${pc(scen)}${pct == null ? ' (ejemplo)' : ''}, ganarías unos ${fmt(v * scen / 100)}: tu patrimonio pasaría de ${fmt(total)} a ${fmt(total + v * scen / 100)}.`
          : `Cada 10 % que se mueva son unos ${fmt(v * 0.1)} arriba o abajo en tu patrimonio.`)
      if (h.avgBuyPrice > 0) {
        const pnl = (h.price - h.avgBuyPrice) * h.amount
        lines.push(pnl >= 0 ? `Ahora mismo vas ganando ${fmt(pnl)} con ${h.symbol} desde que compraste.` : `Ahora mismo vas perdiendo ${fmt(-pnl)} con ${h.symbol} desde que compraste.`)
      }
    }
    const mentionedCoins = CRYPTO_NAMES.filter(([sym, names]) => names.some((n) => has(text, n)) && !cryptoHits.some((h) => h.symbol.toLowerCase() === sym.toLowerCase())).map(([s]) => s)
    if (mentionedCoins.length > 0) {
      tags.push(...mentionedCoins)
      lines.push(`Habla de ${mentionedCoins.join(', ')}, que no tienes: no te afecta directamente` +
        (d.crypto.length > 0 && (has(text, 'cripto') || has(text, 'criptomonedas') || has(text, 'bitcoin')) ? `, aunque cuando el mercado cripto se mueve, tus criptos (${fmt(sumOf(d.crypto, (c) => c.amount * c.price))}) suelen moverse con él.` : '.'))
    }

    // ---------- Tus acciones ----------
    d.stocks.filter((s) => {
      const first = s.name.split(' ')[0]
      return has(text, norm(s.ticker.split('.')[0])) || (s.name.trim() !== '' && has(text, norm(first)) && first.length >= 4)
    }).forEach((s) => {
      const v = s.shares * s.price
      tags.push(s.ticker)
      const scen = pct ?? 5.0
      lines.push(`Tienes ${fmt(v)} en acciones de ${s.name.trim() === '' ? s.ticker : s.name}${share(v)}.`)
      lines.push(dir === -1 ? `Una bajada del ${pc(scen)}${pct == null ? ' (ejemplo)' : ''} serían unos ${fmt(v * scen / 100)} menos.`
        : dir === 1 ? `Una subida del ${pc(scen)}${pct == null ? ' (ejemplo)' : ''} serían unos ${fmt(v * scen / 100)} más.`
          : `Cada 5 % que se mueva son unos ${fmt(v * 0.05)}.`)
    })

    // ---------- Tus bancos ----------
    d.accounts.filter((a) => a.name.length >= 4 && has(text, norm(a.name.split(' ')[0]))).forEach((a) => {
      tags.push(a.name)
      lines.push(`Tienes ${fmt(a.balance)} en ${a.name}.` + (ACCOUNT_TYPES[a.type].inBank ? ' En bancos de la zona euro, los depósitos están cubiertos por el fondo de garantía hasta 100.000 € por persona y banco.' : ''))
    })

    // ---------- Tipos de interés ----------
    if (['tipos de interes', 'bce', 'euribor', 'reserva federal', 'fed', 'lagarde', 'powell', 'tipos'].some((w) => has(text, w))) {
      tags.push('Tipos de interés')
      const paying = d.accounts.filter((a) => a.interestRate > 0 && a.balance > 0)
      if (paying.length === 0) lines.push('Los tipos de interés mueven lo que pagan las cuentas remuneradas y las hipotecas. No tienes ninguna cuenta con intereses apuntada en la app.')
      else paying.forEach((a) => {
        const now = a.balance * a.interestRate / 100 / 12
        const delta = a.balance * 0.25 / 100 / 12
        lines.push(`${a.name} te da un ${pc(a.interestRate)} TAE: unos ${fmt(now)} al mes con ${fmt(a.balance)}.` +
          (dir === -1 ? ` Si los tipos bajan, esa TAE suele bajar detrás: con 0,25 puntos menos ganarías ~${fmt(delta)} menos al mes.`
            : dir === 1 ? ` Si los tipos suben, podría pagar algo más: 0,25 puntos más serían ~${fmt(delta)} más al mes.`
              : ` Cada 0,25 puntos de TAE son ~${fmt(delta)} al mes.`))
      })
    }

    // ---------- Inflación ----------
    const monthly = NewsImpactAnalyzer.monthlySpend(d)
    if (['inflacion', 'ipc', 'precios', 'carestia'].some((w) => has(text, w))) {
      tags.push('Inflación')
      const inf = pct != null && pct < 20 ? pct : 3.0
      if (monthly > 0) lines.push(`Gastas unos ${fmt(monthly)} al mes en el día a día. Con una inflación del ${pc(inf)}${pct == null ? ' (ejemplo)' : ''}, lo mismo te costaría ~${fmt(monthly * inf / 100)} más al mes (${fmt(monthly * 12 * inf / 100)} al año).`)
      const a = maxBy(d.accounts.filter((x) => x.interestRate > 0), (x) => x.balance)
      if (a) {
        const real = a.interestRate - inf
        lines.push(`Tu ${a.name} da un ${pc(a.interestRate)}: ` + (real >= 0 ? `por encima de esa inflación (ganas ~${pc(real)} de verdad).` : 'por debajo de esa inflación, así que tu ahorro pierde algo de poder de compra.'))
      }
    }

    // ---------- Gasolina, luz y vivienda ----------
    const tagMonthly = (t: SpendTag) => sumOf(d.expenses.filter((e) => e.epochDay >= ep(d.today) - 90 && !d.savingIds.has(e.categoryId) && spendTag(e) === t), (e) => e.amount) / 3
    if (['gasolina', 'gasoleo', 'diesel', 'carburantes', 'petroleo', 'crudo', 'brent', 'combustible'].some((w) => has(text, w))) {
      tags.push('Gasolina')
      const t = tagMonthly('TRANSPORT')
      const scen = pct != null && pct < 50 ? pct : 5.0
      lines.push(t > 0 ? `Te gastas unos ${fmt(t)} al mes en transporte. Un ${pc(scen)} ${dir < 0 ? 'menos' : 'más'} serían ~${fmt(t * scen / 100)} ${dir < 0 ? 'de ahorro' : 'de más'} al mes.` : 'No tienes gastos de transporte apuntados últimamente: apenas te afecta.')
    }
    if (['luz', 'electricidad', 'factura electrica', 'gas natural', 'tarifa'].some((w) => has(text, w))) {
      tags.push('Luz y gas')
      const h = tagMonthly('HOME')
      const p = pct != null && pct < 50 ? pct : 10.0
      if (h > 0) lines.push(`En casa y facturas gastas unos ${fmt(h)} al mes. Un ${pc(p)} ${dir < 0 ? 'menos' : 'más'} serían ~${fmt(h * p / 100)} al mes.`)
    }
    if (['alquiler', 'alquileres', 'vivienda', 'hipoteca', 'hipotecas', 'pisos'].some((w) => has(text, w))) {
      tags.push('Vivienda')
      const rent = sumOf(d.bills.filter((b) => { const n = norm(b.name); return n.includes('alquiler') || n.includes('hipoteca') }), (b) => b.amount)
      lines.push(rent > 0 ? `Tu alquiler/hipoteca es de ${fmt(rent)} al mes. Las subidas suelen llegar al renovar el contrato; un 5 % serían ${fmt(rent * 0.05)} más al mes.` : 'No tienes un alquiler o hipoteca apuntado como gasto fijo.')
    }
    if (['paro', 'despidos', 'ere', 'empleo', 'recesion'].some((w) => has(text, w)) && monthly > 0) {
      tags.push('Empleo')
      const cushion = d.wealth.savings + d.wealth.available
      lines.push(`Si un mes no entrara dinero, tu banco y tus ahorros (${fmt(cushion)}) cubrirían unos ${fixed(cushion / monthly, 1)} meses de tu gasto actual.`)
    }

    const relevant = lines.length > 0 && (cryptoHits.length > 0 || tags.some((t) => !mentionedCoins.includes(t)))
    const headline = !relevant && lines.length === 0 ? 'No toca nada de lo que tienes'
      : !relevant ? 'Te afecta poco'
        : cryptoHits.length > 0 || d.stocks.some((s) => tags.includes(s.ticker)) ? (dir < 0 ? 'Puede restarte dinero' : dir > 0 ? 'Puede sumarte dinero' : 'Toca tus inversiones')
          : 'Te afecta en el día a día'
    if (lines.length === 0) lines.push('Esta noticia no habla de tus cuentas, tus inversiones ni de tus gastos principales. Bueno para estar al día, pero no cambia tus números.')
    return { relevant, headline, lines: distinct(lines), tags: distinct(tags) }
  },

  /** Gasto mensual del día a día (sin ahorro), media de los últimos 90 días. */
  monthlySpend(d: FinanceData): number {
    const since = ep(d.today) - 90
    if (d.expenses.length === 0) return 0
    const first = Math.min(...d.expenses.map((e) => e.epochDay))
    const days = Math.max(ep(d.today) - Math.max(since, first) + 1, 30)
    return sumOf(d.spending.filter((e) => e.epochDay >= since), (e) => e.amount) / days * 30.4
  },
}
