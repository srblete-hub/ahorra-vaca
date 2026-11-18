// Port de domain/PepeBot.kt: Pepe, el pastor, contesta dentro de la app. SIN IA: palabras clave y cálculos.
import type { LocalDate } from '../data/dates'
import { fixed, fmtDayDeMonthYear, fmtDayMonthShort, groupBy, maxBy, minBy, monthNameFull, sortedByDesc, sumOf, ymMinus, ymOfDate } from './kt'
import { Merchants } from './Merchants'
import { Classifier, SPEND_TAG_INFO, SPEND_TAG_VALUES, spendTag } from './Spend'
import type { SpendTag } from './Spend'
import { Budgeting } from './Budget'
import type { FinanceData } from './Insights'
import type { CashPlan } from './CashPlan'
import type { GoalStatus } from './Goals'
import { TypicalSpend, SubscriptionChecker } from './Typical'
import { TodayLeft, UpcomingBills } from './TodayLeft'
import { WeeklyReader } from './WeeklyRead'

/** Lo que Pepe necesita saber para contestar (PepeBot.Ctx en Kotlin). */
export interface PepeCtx {
  d: FinanceData
  cash: CashPlan | null
  goal: GoalStatus | null
  userName: string
  fmt: (v: number) => string
}

const lowerLabel = (t: SpendTag) => SPEND_TAG_INFO[t].label.toLowerCase()
const ifBlank = (s: string, other: string) => (s.trim() === '' ? other : s)
const short = (d: LocalDate) => fmtDayMonthShort(d).replace('.', '')
const pct = (v: number) => (v >= 0 ? '+' : '−') + `${fixed(Math.abs(v) * 100, 1)} %`

function tagIn(q: string): SpendTag | null {
  for (const t of SPEND_TAG_VALUES.filter((x) => x !== 'OTHER')) if (q.includes(` ${Classifier.normalize(SPEND_TAG_INFO[t].label)}`)) return t
  const words: Array<[SpendTag, string[]]> = [
    ['FOOD', ['comida', 'comer', 'restaurante', 'bares', 'cenas', 'delivery', 'glovo']],
    ['SUPER', ['super', 'mercadona', 'compra']],
    ['LEISURE', ['ocio', 'cine', 'fiesta', 'salir']],
    ['TRANSPORT', ['transporte', 'gasolina', 'uber', 'metro', 'coche']],
    ['SHOPPING', ['ropa', 'compras', 'amazon', 'vinted']],
    ['SPORT', ['deporte', 'gimnasio', 'gym', 'padel', 'futbol']],
  ]
  return words.find(([, ws]) => ws.some((w) => q.includes(` ${w}`)))?.[0] ?? null
}

/**
 * Pepe, el pastor, contesta dentro de la app. SIN IA: entiende la pregunta por palabras clave y responde
 * con cálculos sobre tus datos (los mismos que usa el resto de la app). Nunca recomienda comprar ni vender inversiones.
 */
export const PepeBot = {
  greeting(name: string): string {
    return `¡Buenas${name.trim() === '' ? '' : `, ${name.trim().split(' ')[0]}`}! Soy Pepe. Pregúntame cómo vas este mes, si llegas a la nómina, ` +
      'en qué recortar, cuánto llevas en algo ("¿cuánto gasto en comida?") o si te puedes permitir un capricho ("¿puedo gastarme 40 €?").'
  },

  answer(question: string, c: PepeCtx): string {
    const q = ` ${Classifier.normalize(question)} `
    const has = (...w: string[]) => w.some((x) => q.includes(` ${x}`) || q.includes(x))
    const m = /(\d+(?:[.,]\d{1,2})?)/.exec(question)
    const amount = m ? Number(m[1].replace(',', '.')) : null
    if (q.trim() === '') return PepeBot.greeting(c.userName)
    if (has('hola', 'buenas', 'buenos dias', 'que tal') && q.trim().split(' ').length <= 4) return PepeBot.greeting(c.userName)
    if (has('gracias')) return 'A mandar. Aquí estoy, con el cayado y la libreta.'
    if (amount != null && has('puedo', 'permitir', 'compro', 'comprar', 'gastarme', 'me gasto', 'capricho')) return canAfford(amount, question, c)
    if (has('queda hoy', 'quedan hoy', 'gastar hoy', 'hoy puedo', 'para hoy', 'llevo hoy', 'gastado hoy')) return today(c)
    if (has('cartera', 'cripto', 'bitcoin', 'accion', 'invers', 'bolsa')) return portfolio(c)
    if (has('suscrip', 'netflix', 'spotify', 'plataforma')) return subscriptions(c)
    if (has('llego', 'fin de mes', 'nomina', 'cobro', 'cuando cobro', 'hasta el')) return untilPayday(c)
    if (has('recort', 'ahorrar en', 'gasto mucho', 'gastando mucho', 'donde se me va', 'en que se me va', 'en que gasto')) return cutBack(c)
    if (has('cuanto deberia ahorrar', 'cuanto ahorro', 'cuanto ahorrar', 'ahorrar al mes', 'ahorrar')) return howMuchToSave(c)
    if (has('objetivo', 'meta', 'hucha')) return goal(c)
    if (has('alquiler', 'fijo', 'recibo', 'factura')) return fixedBills(c)
    const t = tagIn(q)
    if (t != null) return tagSpend(t, c)
    if (has('como voy', 'resumen', 'este mes', 'que tal voy', 'como va')) return month(c)
    if (has('banco', 'saldo', 'cuanto tengo', 'dinero tengo')) return bank(c)
    return 'Esa no me la sé, que soy pastor y no adivino. Prueba con: "¿cómo voy este mes?", "¿llego a la nómina?", ' +
      '"¿en qué puedo recortar?", "¿cuánto gasto en ocio?", "¿puedo gastarme 30 €?" o "¿cómo va mi cartera?".'
  },
}

// ---------- Respuestas ----------

function month(c: PepeCtx): string {
  const d = c.d; const f = c.fmt
  const m = ymOfDate(d.today)
  const variable = d.spendingIn(m).filter((e) => !Budgeting.isFixed(e, d.bills))
  const spent = sumOf(variable, (e) => e.amount)
  const top = maxBy([...groupBy(variable, spendTag).entries()].map(([k, l]): [SpendTag, number] => [k, sumOf(l, (e) => e.amount)]), (p) => p[1])
  const t = TypicalSpend.of(d)
  const vs = t == null ? ''
    : Math.abs(t.diff) < 10 ? 'Vas como en un mes normal a estas alturas.'
      : t.diff > 0 ? `Vas ${f(t.diff)} por encima de tu mes típico a día ${t.today}: ojo, que el rebaño se desmanda.`
        : `Vas ${f(-t.diff)} por debajo de tu mes típico a día ${t.today}. Así me gusta.`
  let s = `En ${monthNameFull(m.month)} llevas ${f(spent)} en el día a día (sin alquiler ni fijos)`
  s += ` y te han entrado ${f(d.receivedIn(m))}.`
  if (top) s += ` Donde más: ${lowerLabel(top[0])}, ${f(top[1])}.`
  if (vs !== '') s += ' ' + vs
  return s
}

function today(c: PepeCtx): string {
  const f = c.fmt
  const spent = TodayLeft.spentToday(c.d)
  const tl = TodayLeft.of(c.d, c.cash)
  if (!tl) return `Hoy llevas ${f(spent)}. Apunta tu nómina y te digo cuánto te toca al día.`
  const [perDay, left] = tl
  const bills = UpcomingBills.of(c.d, 0)
  return (left >= 0 ? `Hoy llevas ${f(spent)} y te quedan ${f(left)} de los ${f(perDay)} que te tocan.`
    : `Hoy llevas ${f(spent)}: te has pasado ${f(-left)} de los ${f(perDay)}. No pasa nada, se descuenta de los próximos días.`) +
    (bills.length > 0 ? ` Ojo, hoy pasa ${bills.map((b) => `${b.name} (${f(b.amount)})`).join(' y ')}.` : '')
}

function untilPayday(c: PepeCtx): string {
  const p = c.cash
  if (!p) return 'Necesito saber cuándo cobras para echar la cuenta: apunta tu nómina en Ingresos.'
  const f = c.fmt
  const min = minBy(p.series, (x) => x[1])
  const days = p.days
  if (p.perDayBefore < 0) {
    return `Así no llegas con ${f(p.cushion)} de colchón a la ${p.paydayName.toLowerCase()} (${short(p.payday)}): te faltarían unos ${f(p.shortfall)}. ` +
      'Toca apretarse el cinto o pasar algo del ahorro.'
  }
  return `Llegas. Tienes ${f(p.balance)} en el banco y faltan ${days} ${days === 1 ? 'día' : 'días'} para la ${p.paydayName.toLowerCase()} (${short(p.payday)}). ` +
    `Puedes gastar unos ${f(p.perDayBefore)} al día y acabarías con ${f(p.cushion)} de colchón.` +
    (min ? ` Lo más bajo será ${f(min[1])} el ${short(min[0])}.` : '')
}

function cutBack(c: PepeCtx): string {
  const d = c.d; const f = c.fmt
  const tips: string[] = []
  WeeklyReader.of(d, f)?.insights.filter((i) => i.up).slice(0, 2).forEach((i) => tips.push(`${i.title}. ${i.detail}`))
  if (tips.length === 0) {
    const m = ymOfDate(d.today)
    const groups = [...groupBy(d.spendingIn(m).filter((e) => !Budgeting.isFixed(e, d.bills) && !SPEND_TAG_INFO[spendTag(e)].need), spendTag).entries()]
      .map(([k, l]): [SpendTag, number] => [k, sumOf(l, (e) => e.amount)])
    sortedByDesc(groups, (p) => p[1]).slice(0, 2).forEach(([k, v]) => tips.push(`En ${lowerLabel(k)} llevas ${f(v)} este mes.`))
  }
  const sc = SubscriptionChecker.of(d)
  if (sc && sc.maxSaving >= 1) tips.push(`Revisando suscripciones podrías ahorrar hasta ${f(sc.maxSaving)} al mes.`)
  return tips.length === 0 ? 'No veo nada desmadrado: tus gastos van en su sitio. Sigue así, que oveja que no se aleja no se pierde.'
    : 'Yo miraría esto:\n' + tips.map((t) => `• ${t}`).join('\n')
}

function howMuchToSave(c: PepeCtx): string {
  const d = c.d; const f = c.fmt
  const m = ymMinus(ymOfDate(d.today), 1)
  const last = d.receivedIn(m)
  const inc = last > 0 ? last : d.receivedIn(ymOfDate(d.today))
  if (inc <= 0) return 'Apunta lo que cobras y te digo cuánto apartar.'
  const ten = Math.round(inc * 0.10 / 10) * 10.0
  const twenty = Math.round(inc * 0.20 / 10) * 10.0
  const g = c.goal && c.goal.requiredMonthly > 0 ? c.goal : null
  return `Con lo que te entra (${f(inc)} al mes), lo sano es apartar entre ${f(ten)} y ${f(twenty)} al mes, del 10 al 20 %.` +
    (g ? ` Para llegar a «${ifBlank(g.goal.name, 'tu objetivo')}» a tiempo te harían falta ${f(g.requiredMonthly)} al mes.` : '') +
    ' Mejor nada más cobrar, que lo que no se ve no se gasta.'
}

function goal(c: PepeCtx): string {
  const g = c.goal
  if (!g) return 'Aún no tienes objetivo de ahorro. Créalo en Patrimonio y te digo cuándo llegas.'
  const f = c.fmt
  const p = g.goal.target > 0 ? Math.round(g.current / g.goal.target * 100) : 0
  const left = Math.max(g.goal.target - g.current, 0)
  return `«${ifBlank(g.goal.name, 'Tu objetivo')}»: llevas ${f(g.current)} de ${f(g.goal.target)} (${p} %). Te faltan ${f(left)}.` +
    (g.eta ? ` A tu ritmo llegarías el ${fmtDayDeMonthYear(g.eta)}.` : ' A este ritmo no llegas: mete algo cada semana.')
}

function portfolio(c: PepeCtx): string {
  const d = c.d; const f = c.fmt
  const items: Array<[string, number, number]> = [
    ...d.crypto.map((h): [string, number, number] => [h.name, h.amount * h.price, h.amount * h.avgBuyPrice]),
    ...d.stocks.map((s): [string, number, number] => [ifBlank(s.name, s.ticker), s.shares * s.price, s.shares * s.avgBuyPrice]),
  ]
  if (items.length === 0) return 'No tienes inversiones apuntadas. Si las tienes, añádelas en Inversión y te digo cómo van.'
  const value = sumOf(items, (i) => i[1]); const cost = sumOf(items, (i) => i[2])
  const withCost = items.filter((i) => i[2] > 0)
  const best = maxBy(withCost, (i) => (i[1] - i[2]) / i[2])
  const worst = minBy(withCost, (i) => (i[1] - i[2]) / i[2])
  let s = `Tu cartera vale ${f(value)}`
  if (cost > 0) s += `, ${value >= cost ? '+' : '−'}${f(Math.abs(value - cost))} (${pct((value - cost) / cost)}) desde que compraste`
  s += '.'
  if (best && worst && best !== worst) s += ` Lo que mejor va: ${best[0]} (${pct((best[1] - best[2]) / best[2])}). Lo que peor: ${worst[0]} (${pct((worst[1] - worst[2]) / worst[2])}).`
  s += ' Yo no te digo qué comprar ni vender: eso es cosa tuya.'
  return s
}

function subscriptions(c: PepeCtx): string {
  const s = SubscriptionChecker.of(c.d)
  if (!s) return 'No veo suscripciones que se repitan cada mes.'
  const f = c.fmt
  return `Pagas ${f(s.monthlyTotal)} al mes en ${s.subs.length} suscripciones (${s.subs.map((x) => x.name).join(', ')}).` +
    (s.actions.length === 0 ? ' Todas en orden.' : '\n' + s.actions.slice(0, 3).map((a) => `• ${a.text}`).join('\n'))
}

function fixedBills(c: PepeCtx): string {
  const d = c.d; const f = c.fmt
  if (d.bills.length === 0) return 'No tienes gastos fijos apuntados. Añade el alquiler y los recibos en Movimientos.'
  const total = sumOf(d.bills, (b) => b.amount)
  return `Tus fijos suman ${f(total)} al mes: ` + sortedByDesc(d.bills, (b) => b.amount).slice(0, 6).map((b) => `${b.name} ${f(b.amount)}`).join(', ') +
    '. No cuentan en tu gasto del día a día.'
}

function bank(c: PepeCtx): string {
  // Liquidez = solo la cuenta del banco (tipo BANK); el ahorro no se cuenta.
  const accounts = c.d.accounts.filter((a) => a.type === 'BANK')
  const f = c.fmt
  return `En el banco tienes ${f(sumOf(accounts, (a) => a.balance))}` + (accounts.length > 1 ? ` (${accounts.map((a) => `${a.name} ${f(a.balance)}`).join(', ')})` : '') +
    '. El ahorro no lo cuento: ese no se toca.'
}

function tagSpend(tag: SpendTag, c: PepeCtx): string {
  const d = c.d; const f = c.fmt
  const m = ymOfDate(d.today)
  const now = d.spendingIn(m).filter((e) => spendTag(e) === tag)
  const before = sumOf(d.spendingIn(ymMinus(m, 1)).filter((e) => spendTag(e) === tag), (e) => e.amount)
  const sum = sumOf(now, (e) => e.amount)
  if (now.length === 0) return `Este mes no llevas nada en ${lowerLabel(tag)}.` + (before > 0 ? ` El mes pasado fueron ${f(before)}.` : '')
  const where = maxBy([...groupBy(now, (e) => ifBlank(Merchants.clean(e.note), 'otros')).entries()].map(([k, l]): [string, number] => [k, sumOf(l, (e) => e.amount)]), (p) => p[1])
  return `En ${lowerLabel(tag)} llevas ${f(sum)} este mes (${now.length} ${now.length === 1 ? 'pago' : 'pagos'}).` +
    (before > 0 ? ` El mes pasado, ${f(before)} en total.` : '') +
    (where ? ` Donde más: ${where[0]}, ${f(where[1])}.` : '')
}

function canAfford(price: number, question: string, c: PepeCtx): string {
  const f = c.fmt
  const p = c.cash
  if (p == null) return 'Apunta tu nómina y tu banco y te digo si cabe.'
  void question
  const perDay = p.perDayBefore
  const days = price / Math.max(perDay, 1.0)
  const payName = p.paydayName.toLowerCase()
  if (perDay <= 0) return `Ahora mismo no: ya vas justo para llegar a la ${payName}. Espérate a cobrar.`
  if (price <= perDay) return `Sí, cabe: son ${f(price)} y hoy te tocan unos ${f(perDay)}. Disfrútalo.`
  if (price <= perDay * 3) return `Cabe, pero equivale a ${Math.round(days)} días de gasto. Si lo compras, aprieta un poco los próximos días.`
  return `Uf, ${f(price)} son unos ${Math.round(days)} días de lo que te toca gastar (${f(perDay)}/día). Yo lo dejaría para después de la ${payName}. ` +
    'Puedes mirarlo más a fondo en "¿Lo compro o no?".'
}
