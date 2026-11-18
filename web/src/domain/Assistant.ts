// Port de domain/Assistant.kt: chat con tus finanzas, sin enviar nada a internet (reglas fijas).
import { capFirst, distinctBy, ep, fmtWeekdayDayMonth, sortedByDesc, sumOf } from './kt'
import { Classifier, NaturalEntry, SPEND_TAG_INFO, SPEND_TAG_VALUES, spendTag } from './Spend'
import type { ParsedExpense, SpendTag } from './Spend'
import { byTag, Insights } from './Insights'
import type { FinanceData } from './Insights'
import { Bills } from './Planning'
import type { FlowEvent, SafeToSpend } from './Planning'

/** Respuesta del asistente: el texto, de dónde sale cada dato y, si escribiste gastos, cuáles. */
export interface Answer { text: string; sources: string[]; expenses: ParsedExpense[] }

const answer = (text: string, sources: string[] = [], expenses: ParsedExpense[] = []): Answer => ({ text, sources, expenses })
const lowerLabel = (t: SpendTag) => SPEND_TAG_INFO[t].label.toLowerCase()

function amountIn(q: string): number | null {
  const m = /(\d+(?:[.,]\d{1,2})?)/.exec(q.replace(/\./g, ''))
  return m ? Number(m[1].replace(',', '.')) : null
}

function replaceLast(s: string, old: string, rep: string): string {
  const i = s.lastIndexOf(old)
  return i < 0 ? s : s.slice(0, i) + rep + s.slice(i + old.length)
}

function substringAfter(s: string, delim: string, missing: string): string {
  const i = s.indexOf(delim)
  return i < 0 ? missing : s.slice(i + delim.length)
}

/**
 * Chat con tus finanzas. Funciona en el móvil, sin enviar nada a internet: entiende las preguntas
 * más habituales y responde con tus movimientos, tus gastos fijos y lo que viene.
 */
export const Assistant = {
  SUGGESTIONS: [
    '¿Puedo gastarme 250 € este fin de semana?',
    '¿Por qué he ahorrado menos este mes?',
    '¿Qué tres cambios me permitirían ahorrar 100 € más?',
    '¿Cuánto gasto en restaurantes?',
    '¿Cuándo cobro?',
    'He pagado 18 € en una cena y 12 € en gasolina',
  ],

  answer(question: string, d: FinanceData, safe: SafeToSpend, events: FlowEvent[], fmt: (v: number) => string): Answer {
    const q = Classifier.normalize(question)
    const has = (...w: string[]) => w.some((x) => q.includes(x))

    // Gastos escritos como frase: "He pagado 18 € en…".
    if (/^(hoy |ayer )?(me )?(he )?(pagado|gastado|gaste|pague|apunta|anade|compre)/.test(q)) {
      const parsed = NaturalEntry.parse(question, d.expenses)
      if (parsed.length > 0) {
        return answer(
          'Lo apunto así:\n' + parsed.map((p) => `• ${p.note}: ${fmt(p.amount)} · ${SPEND_TAG_INFO[p.tag].label}` + (p.daysAgo === 1 ? ' · ayer' : '')).join('\n'),
          ['Tipo de gasto deducido de la descripción y de cómo clasificaste gastos parecidos.'],
          parsed,
        )
      }
    }

    if (has('objetivo', 'meta')) return answer('Tu objetivo lo ves en Inicio: te digo cuánto apartar al mes, si vas a tiempo y qué cambiar para llegar antes.')
    if (has('puedo gastar', 'me puedo permitir', 'puedo permitirme', 'puedo comprar', 'me lo puedo', 'puedo pagar')) return canSpend(amountIn(question), q, d, safe, fmt)
    if (has('ahorrado menos', 'ahorro menos', 'ahorre menos', 'por que he ahorrado', 'por que tengo menos', 'por que he gastado')) return whySavedLess(d, fmt)
    if (has('ahorrar') && (amountIn(question) != null || has('cambios', 'recortar', 'como puedo'))) return howToSave(amountIn(question) ?? 100.0, d, fmt)
    if (has('suscripcion')) return subscriptions(d, fmt)
    if (has('cuando cobro', 'proximo cobro', 'cuando me pagan', 'nomina', 'cuando entra')) return nextIncome(events, fmt)
    if (has('salud', 'como voy', 'como estoy', 'estoy bien', 'voy bien')) return healthSummary(d, fmt)
    if (has('cuanto tengo', 'patrimonio', 'dinero tengo', 'cuanto dinero')) return wealth(d, fmt)
    if (has('gasto en', 'gastado en', 'gasto de', 'gastando en', 'gasto al mes en')) return spendOn(q, d, fmt)
    if (has('cuanto he gastado', 'cuanto llevo', 'gastado este mes', 'cuanto gasto')) return spentThisMonth(d, fmt)
    return answer(
      'Puedo ayudarte con preguntas como estas:\n' + Assistant.SUGGESTIONS.map((s) => `• ${s}`).join('\n') +
        '\n\nTambién puedes escribir tus gastos tal cual y los apunto.',
    )
  },
}

function canSpend(amount: number | null, q: string, d: FinanceData, s: SafeToSpend, fmt: (v: number) => string): Answer {
  const sources = [
    `Disponible en tu cuenta principal: ${fmt(s.available)}`,
    `Gastos fijos que faltan: −${fmt(s.pendingBills)}`,
    `Reservado para ahorrar este mes: −${fmt(s.savingsReserve)}`,
  ]
  if (s.plans > 0) sources.push(`Apartado para tus planes: −${fmt(s.plans)}`)
  sources.push(s.nextPayday ? `Tu próximo cobro: ${fmtWeekdayDayMonth(s.nextPayday)} (faltan ${s.daysToPayday} días)` : `Cuento hasta final de mes (quedan ${s.daysToPayday} días)`)
  if (amount == null) return answer(`Ahora mismo puedes gastar ${fmt(Math.max(0, s.safe))} sin tocar lo comprometido, unos ${fmt(s.perDay)} al día. Dime una cantidad y te digo si te lo puedes permitir.`, sources)

  const habits = q.includes('fin de semana') ? Insights.habits(d, fmt) : null
  const weekend = habits ? 2 * habits.weekendAvg : null
  if (weekend != null && weekend > 0) sources.push(`Un fin de semana normal gastas unos ${fmt(weekend)}`)
  const left = s.safe - amount
  let text: string
  if (s.safe <= 0) {
    text = 'Mejor no: todo lo que tienes disponible ya está comprometido hasta tu próximo cobro.' +
      (d.wealth.savings >= amount ? ` Podrías hacerlo tirando de tu ahorro (${fmt(d.wealth.savings)}), pero estarías gastando lo que habías guardado.` : '')
  } else if (left >= 0) {
    text = `Sí. Después de gastar ${fmt(amount)} te quedarían ${fmt(left)} para gastar` +
      (s.daysToPayday > 1 ? `, unos ${fmt(left / s.daysToPayday)} al día durante ${s.daysToPayday} días.` : '.') +
      (left / Math.max(1, s.daysToPayday) < 10 ? ' Vas a ir justo: ojo con los imprevistos.' : '')
  } else if (d.wealth.savings >= -left) {
    text = `Te pasarías ${fmt(-left)} de lo que puedes gastar. Solo podrías tirando de tu ahorro; si es algo que quieres de verdad, plantéate apartarlo poco a poco.`
  } else {
    text = `No: te pasarías ${fmt(-left)} y no tienes margen ni en el ahorro. Mejor espera a cobrar.`
  }
  return answer(text, sources)
}

function whySavedLess(d: FinanceData, fmt: (v: number) => string): Answer {
  const s = Insights.monthStory(d)
  const savedNow = s.incomeNow - s.spentNow
  const savedPrev = s.incomePrev - s.spentPrev
  const diff = savedNow - savedPrev
  const reasons: string[] = []
  if (s.incomePrev - s.incomeNow > 10) reasons.push(`has cobrado ${fmt(s.incomePrev - s.incomeNow)} menos`)
  s.changes.filter((p) => p[1] > 5).slice(0, 3).forEach((p) => reasons.push(`has gastado ${fmt(p[1])} más en ${lowerLabel(p[0])}`))
  const text = diff >= 0 ? `En realidad ${s.label} has ahorrado ${fmt(diff)} más que en el mismo tramo del mes anterior (${fmt(savedNow)} frente a ${fmt(savedPrev)}). ¡Bien!`
    : reasons.length === 0 ? `${capFirst(s.label)} has ahorrado ${fmt(-diff)} menos, repartido en muchos gastos pequeños.`
      : `${capFirst(s.label)} has ahorrado ${fmt(-diff)} menos porque ` + replaceLast(reasons.join(', '), ', ', ' y ') + '.'
  return answer(text, [
    `Cobrado: ${fmt(s.incomeNow)} ahora frente a ${fmt(s.incomePrev)} antes`,
    `Gastado: ${fmt(s.spentNow)} ahora frente a ${fmt(s.spentPrev)} antes`,
    'Comparo el mismo número de días de cada mes',
  ])
}

function monthlyByTag(d: FinanceData): [Array<[SpendTag, number]>, string] {
  const closed = d.closedMonths(3)
  if (closed.length === 0) return [byTag(d.spendingIn(d.thisMonth)), 'lo que llevas este mes']
  const totals = byTag(closed.flatMap((m) => d.spendingIn(m))).map((p): [SpendTag, number] => [p[0], p[1] / closed.length])
  return [totals, `tu media de ${closed.length} ${closed.length === 1 ? 'mes' : 'meses'}`]
}

function howToSave(target: number, d: FinanceData, fmt: (v: number) => string): Answer {
  const [tags, basis] = monthlyByTag(d)
  const changes: Array<[string, number]> = []
  let left = target
  // 1) Suscripciones (gastos fijos): quitar una es ahorro seguro todos los meses.
  const subs = sortedByDesc(d.bills.filter((b) => !b.yearly && Classifier.guess(b.name) === 'SUBSCRIPTIONS'), (b) => b.amount)
  if (subs[0]) { changes.push([`Cancela o comparte ${subs[0].name}`, subs[0].amount]); left -= subs[0].amount }
  // 2) Recortar un 25 % lo prescindible que más pesa.
  for (const [tag, avg] of tags.filter((p) => !SPEND_TAG_INFO[p[0]].need && p[0] !== 'OTHER' && p[0] !== 'SUBSCRIPTIONS')) {
    if (changes.length >= 3 || left <= 0) break
    const cut = Math.min(left, Math.ceil(avg * 0.25 / 5) * 5)
    if (cut >= 5) { changes.push([`Gasta un ${Math.trunc(cut / avg * 100)} % menos en ${lowerLabel(tag)} (de ${fmt(avg)} a ${fmt(avg - cut)})`, cut]); left -= cut }
  }
  // 3) Si aún falta, lo necesario más flexible: supermercado y transporte.
  for (const [tag, avg] of tags.filter((p) => p[0] === 'SUPER' || p[0] === 'TRANSPORT')) {
    if (changes.length >= 3 || left <= 0) break
    const cut = Math.min(left, Math.ceil(avg * 0.1 / 5) * 5)
    if (cut >= 5) { changes.push([`Ajusta ${lowerLabel(tag)} un 10 % (marca blanca, lista de la compra, compartir coche)`, cut]); left -= cut }
  }
  if (changes.length === 0) return answer('Aún no tengo suficientes gastos tuyos para proponerte cambios. Apunta unas semanas de gastos (o activa los pagos con tarjeta) y vuelve a preguntarme.')
  const total = sumOf(changes, (c) => c[1])
  return answer(
    changes.map(([t, v], i) => `${i + 1}. ${t}: +${fmt(v)} al mes`).join('\n') +
      '\n\n' + (total >= target ? `Con eso ahorrarías ${fmt(total)} más al mes (${fmt(total * 12)} al año).` : `Con eso llegarías a ${fmt(total)} al mes; para el resto haría falta tocar gastos fijos.`),
    [`Basado en ${basis} por tipo de gasto`, 'Suscripciones sacadas de tus gastos fijos'],
  )
}

function subscriptions(d: FinanceData, fmt: (v: number) => string): Answer {
  const subs = d.bills.filter((b) => Classifier.guess(b.name) === 'SUBSCRIPTIONS')
  const since = ep(d.today) - 40
  const seenList = distinctBy(d.spending.filter((e) => e.billId == null && spendTag(e) === 'SUBSCRIPTIONS' && e.epochDay >= since), (e) => Classifier.normalize(e.note))
    .filter((e) => !subs.some((b) => Classifier.normalize(b.name) === Classifier.normalize(e.note)))
  if (subs.length === 0 && seenList.length === 0) return answer('No veo suscripciones entre tus gastos fijos ni en tus pagos recientes.')
  const monthly = sumOf(subs, (b) => Bills.monthly(b)) + sumOf(seenList, (e) => e.amount)
  return answer(
    [...subs.map((b) => `• ${b.name}: ${fmt(b.amount)}${b.yearly ? ' al año' : ' al mes'}`), ...seenList.map((e) => `• ${e.note}: ${fmt(e.amount)} (visto en tus pagos)`)].join('\n') +
      `\n\nEn total ${fmt(monthly)} al mes, ${fmt(monthly * 12)} al año.`,
    ['Gastos fijos y pagos de los últimos 40 días clasificados como suscripción'],
  )
}

function nextIncome(events: FlowEvent[], fmt: (v: number) => string): Answer {
  const next = events.filter((e) => e.isIncome).slice(0, 3)
  if (next.length === 0) return answer('No tengo cobros previstos. Añade tus ingresos (nómina, autónomo…) en la pestaña Gastos.')
  return answer(
    next.map((e) => `• ${e.label}: ${fmt(e.amount)} · ${fmtWeekdayDayMonth(e.date)}` + (e.overdue ? ' (debería haber llegado ya)' : '')).join('\n'),
    ['Según tus fuentes de ingreso; lo variable, con la media de tus últimos 3 meses'],
  )
}

function healthSummary(d: FinanceData, fmt: (v: number) => string): Answer {
  const h = Insights.health(d, fmt)
  return answer(
    `Tienes ${h.good} de ${h.indicators.length} indicadores en buen estado.\n` +
      h.indicators.map((i) => `• ${i.name}: ${i.value}`).join('\n') + `\n\nLo más útil ahora: ${h.nextStep}`,
    ['Informe de salud financiera (pestaña Gastos → Análisis)'],
  )
}

function wealth(d: FinanceData, fmt: (v: number) => string): Answer {
  const w = d.wealth
  const src = [`Disponible: ${fmt(w.available)}`, `Ahorro: ${fmt(w.savings)}`, `Invertido: ${fmt(w.invested)}`]
  if (w.debts > 0) src.push(`Deudas: −${fmt(w.debts)}`)
  return answer(`Tu patrimonio neto es ${fmt(w.total)}.`, src)
}

function spendOn(q: string, d: FinanceData, fmt: (v: number) => string): Answer {
  const rest = substringAfter(q, ' en ', substringAfter(q, ' de ', q))
  const tag = SPEND_TAG_VALUES.find((t) => Classifier.normalize(SPEND_TAG_INFO[t].label).split(' ').some((w) => w.length > 3 && rest.includes(w))) ?? Classifier.guess(rest)
  if (tag == null || tag === 'OTHER') return spentThisMonth(d, fmt)
  const now = sumOf(d.spendingIn(d.thisMonth).filter((e) => spendTag(e) === tag), (e) => e.amount)
  const closed = d.closedMonths(3)
  const avg = closed.length === 0 ? null : sumOf(closed, (m) => sumOf(d.spendingIn(m).filter((e) => spendTag(e) === tag), (e) => e.amount)) / closed.length
  return answer(
    `En ${lowerLabel(tag)} llevas ${fmt(now)} este mes` + (avg != null ? `. Tu media es de ${fmt(avg)} al mes (${fmt(avg * 12)} al año).` : '.'),
    [`Gastos clasificados como "${SPEND_TAG_INFO[tag].label}"`, ...(avg != null ? [`Media de los últimos ${closed.length} meses cerrados`] : [])],
  )
}

function spentThisMonth(d: FinanceData, fmt: (v: number) => string): Answer {
  const list = d.spendingIn(d.thisMonth)
  const tags = byTag(list).slice(0, 3)
  return answer(
    `Este mes llevas ${fmt(sumOf(list, (e) => e.amount))} gastados` +
      (tags.length > 0 ? ', sobre todo en ' + tags.map(([t, v]) => `${lowerLabel(t)} (${fmt(v)})`).join(', ') + '.' : '.'),
    [`${list.length} pagos este mes, sin contar lo que apartas para ahorrar`],
  )
}
