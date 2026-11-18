// Port de domain/Circles.kt: los tres círculos de salud financiera (Estabilidad, Control, Progreso).
import type { LocalDate } from '../data/dates'
import { average, clamp, ep, fixed, maxBy, minusWeeks, monday, range, sumOf, toDoubleOrNull, toLongOrNull } from './kt'
import { Classifier, SPEND_TAG_INFO, spendTag } from './Spend'
import type { FinanceData } from './Insights'
import { Bills } from './Planning'
import type { BankPlan, Forecast } from './Planning'
import { Budgeting } from './Budget'
import type { GoalStatus } from './Goals'

const f1 = (v: number) => fixed(v, 1)
const pc = (v: number) => `${fixed(v * 100, 0)} %`

/**
 * Una variable de un círculo. `score` va de 0 a 100 por una curva continua (nunca un umbral);
 * `score` null = no hay datos y no cuenta (y baja la confianza del círculo). `provisional` = score == null.
 */
export interface CircleFactor {
  name: string
  score: number | null
  weight: number
  detail: string
  advice: string
  /** La fórmula, para que se vea de dónde sale el número. */
  formula: string
  provisional: boolean
}

function cf(name: string, score: number | null, weight: number, detail: string, advice: string, formula = ''): CircleFactor {
  return { name, score, weight, detail, advice, formula, provisional: score == null }
}

/**
 * Un círculo: media geométrica ponderada de sus variables (una muy floja hunde el total y no se
 * compensa con otra muy buena), encogida hacia 50 según la confianza de los datos.
 * `raw`, `score`, `provisional` y `nextStep` (calculadas en Kotlin) se rellenan al crearlo.
 */
export interface FinanceCircle {
  key: string
  title: string
  question: string
  factors: CircleFactor[]
  confidence: number
  /** Puntuación sin corregir por confianza. */
  raw: number
  /** Con pocos datos la nota se acerca a 50: no se puede sacar 100 con una semana de historial. */
  score: number
  provisional: boolean
  /** La acción más útil: la variable que más puntos le quita al círculo (por su peso). */
  nextStep: string
}

export function makeFinanceCircle(key: string, title: string, question: string, factors: CircleFactor[], confidence: number): FinanceCircle {
  const known = factors.filter((f) => f.score != null)
  let raw = 50.0
  if (known.length > 0) {
    const w = sumOf(known, (f) => f.weight)
    raw = Math.exp(sumOf(known, (f) => f.weight / w * Math.log(clamp(f.score!, 1.0, 100.0))))
  }
  const worst = maxBy(known, (f) => (100 - f.score!) * f.weight)
  return {
    key, title, question, factors, confidence, raw,
    score: 50 + (raw - 50) * confidence,
    provisional: confidence < 0.75,
    nextStep: worst?.advice ?? factors[0].advice,
  }
}

function cv(values: number[]): number | null {
  if (values.length < 3) return null
  const m = average(values)
  if (m <= 0) return null
  return Math.sqrt(sumOf(values, (x) => (x - m) * (x - m)) / values.length) / m
}

/**
 * Los tres círculos de tu salud financiera, con curvas continuas que se acercan a 100 sin tocarlo:
 * - satura(x, τ) = 100·(1 − e^(−x/τ)): rendimientos decrecientes (cada mes de colchón suma menos que el anterior).
 * - decae(x, k) = 100·e^(−k·x): para lo que es malo cuanto más hay (volatilidad, deudas, desvíos).
 * - logística(x, x0, k) = 100 / (1 + e^(−k(x−x0))): para ratios con un punto de equilibrio claro.
 */
export const FinancialCircles = {
  saturate(x: number, tau: number): number { return 100 * (1 - Math.exp(-Math.max(0, x) / tau)) },
  decay(x: number, k: number): number { return 100 * Math.exp(-k * Math.max(0, x)) },
  logistic(x: number, x0: number, k: number): number { return 100 / (1 + Math.exp(-k * (x - x0))) },

  of(d: FinanceData, plan: BankPlan | null, forecast: Forecast | null, goals: GoalStatus[], margin: number, fmt: (v: number) => string): FinanceCircle[] {
    void plan
    const { saturate, decay, logistic } = FinancialCircles
    const today = d.today
    const closed3 = d.closedMonths(3)
    const closed6 = d.closedMonths(6)
    const bills = sumOf(d.bills, (b) => Bills.monthly(b))
    // Gasto esencial mensual: fijos + lo necesario variable (súper, transporte, salud…) de los meses cerrados.
    const needsVar = closed3.length === 0 ? null
      : sumOf(closed3, (m) => sumOf(Budgeting.variable(d, m).filter((e) => SPEND_TAG_INFO[spendTag(e)].need), (e) => e.amount)) / closed3.length
    const essentials = bills + (needsVar ?? 0)
    const income = d.monthlyIncome
    const monthsOfData = d.firstMonth ? Math.max((today.year - d.firstMonth.year) * 12 + today.month - d.firstMonth.month, 0) : 0
    const dataConfidence = Math.min(1.0, monthsOfData / 6.0)

    // ================= ESTABILIDAD =================
    const liquid = d.wealth.savings + Math.max(0, d.wealth.available - essentials)
    const months = essentials > 0 ? liquid / essentials : null
    const e1 = cf(
      'Colchón de emergencia', months != null ? saturate(months, 2.5) : null, 0.35,
      months != null ? `Tu ahorro líquido (${fmt(liquid)}) cubre ${f1(months)} meses de gastos esenciales (${fmt(essentials)}/mes). 3 meses ≈ 70; 6 ≈ 91; 12 ≈ 99.` : 'Apunta tus gastos fijos para calcularlo.',
      'Aparta cada mes en la cuenta remunerada hasta cubrir 6 meses de gastos esenciales.', '100·(1 − e^(−meses/2,5))',
    )
    // Próximos 30 días: lo que tienes y entra frente a lo que sale (fijos + gasto esencial previsto).
    const limit30 = ep(today) + 30
    const in30 = forecast ? sumOf(forecast.events.filter((e) => e.isIncome && ep(e.date) <= limit30), (e) => e.amount) : 0
    const out30 = (forecast ? sumOf(forecast.events.filter((e) => !e.isIncome && ep(e.date) <= limit30), (e) => -e.amount) : bills) + (needsVar ?? 0)
    const coverage = out30 > 0 ? (d.wealth.available + in30) / out30 : null
    const e2 = cf(
      'Cobertura de pagos (30 días)', coverage != null ? logistic(coverage, 1.15, 4.0) : null, 0.25,
      coverage != null ? `Disponible + lo que va a entrar (${fmt(d.wealth.available + in30)}) frente a lo que va a salir (${fmt(out30)}): ${f1(coverage)}×. 1× ≈ 35; 1,5× ≈ 80; 2× ≈ 97.` : 'Sin pagos previstos.',
      'Ten en cuenta los recibos del mes antes de gastar: deja en la cuenta al menos 1,5 veces lo que va a salir.', '100 / (1 + e^(−4·(cobertura − 1,15)))',
    )
    const incomes6 = closed6.map((m) => d.receivedIn(m)).filter((v) => v > 0)
    const incomeCv = cv(incomes6)
    const e3 = cf(
      'Estabilidad de ingresos', incomeCv != null ? decay(incomeCv, 3.5) : null, 0.15,
      incomeCv != null ? `Lo que cobras varía un ${pc(incomeCv)} entre meses (coeficiente de variación de ${incomes6.length} meses). 5 % ≈ 84; 20 % ≈ 50.` : 'Hacen falta 3 meses cerrados con cobros.',
      'Si tus ingresos varían, presupuesta con el mes más flojo y trata lo demás como extra.', '100·e^(−3,5·CV)',
    )
    const debtRatio = income > 0 ? d.wealth.debts / (income * 12) : null
    const e4 = cf(
      'Carga de deuda', d.wealth.debts <= 0 ? 100.0 : debtRatio != null ? decay(debtRatio, 2.0) : null, 0.15,
      d.wealth.debts <= 0 ? 'Sin deudas apuntadas.' : `Debes ${fmt(d.wealth.debts)}: ${debtRatio != null ? pc(debtRatio) : '?'} de tus ingresos de un año.`,
      'Paga primero la deuda con más interés.', '100·e^(−2·deuda/ingresos anuales)',
    )
    const rigidity = income > 0 ? bills / income : null
    const e5 = cf(
      'Rigidez de gastos fijos', rigidity != null ? 100 - logistic(rigidity, 0.5, 10.0) : null, 0.10,
      rigidity != null ? `Tus fijos (${fmt(bills)}/mes) son el ${pc(rigidity)} de lo que ganas. 30 % ≈ 88; 50 % ≈ 50; 60 % ≈ 27.` : 'Apunta tus ingresos.',
      'Revisa tarifas y suscripciones: cada euro fijo que bajas te da margen todos los meses.', '100 − 100/(1 + e^(−10·(fijos/ingresos − 0,5)))',
    )

    // ================= CONTROL =================
    const spare = income > 0 ? (income - essentials) / income : null
    const c1 = cf(
      'Margen sostenible', spare != null ? (spare <= 0 ? 0.0 : saturate(spare, 0.15)) : null, 0.30,
      spare != null ? `Tras fijos y necesidades te queda el ${pc(spare)} de lo que ganas (${fmt(income - essentials)}/mes). 10 % ≈ 49; 20 % ≈ 74; 30 % ≈ 86.` : 'Apunta tus ingresos.',
      'Gana margen recortando lo necesario más caro (súper, transporte) o subiendo ingresos.', '100·(1 − e^(−margen/0,15))',
    )
    // Presupuesto: cuánto te pasaste (en %) cada mes cerrado y en lo que va de este, con más peso lo reciente.
    const spPct = sumOf(d.categories.filter((c) => !c.isSavings), (c) => c.percent)
    const spendableShare = spPct > 0 ? spPct / 100 : 0.8
    const overs: Array<[number, number]> = []
    closed3.forEach((m, i) => {
      const received = d.receivedIn(m)
      if (!(received > 0)) return
      const limit = received * spendableShare
      const spent = d.spentIn(m)
      overs.push([Math.max(0, spent / limit - 1), 3.0 - i])
    })
    const pressureNow = (100 - margin) / 100 * 1.2 // margen 100 → 0; 50 → 0,6; 0 → 1,2 (escala del día)
    const overAvg = overs.length === 0 ? null : sumOf(overs, (o) => o[0] * o[1]) / sumOf(overs, (o) => o[1])
    const c2 = cf(
      'Cumplimiento del presupuesto', overAvg != null ? decay(overAvg, 3.0) * (0.7 + 0.3 * decay(Math.max(0, pressureNow - 0.6), 2.0) / 100)
        : decay(Math.max(0, pressureNow - 0.6), 2.0), 0.25,
      (overAvg != null ? `En los últimos ${overs.length} meses te pasaste de media un ${pc(overAvg)} de lo que podías gastar. ` : '') +
        `Este mes vas al ${Math.trunc(margin)} % de margen.`,
      'Usa la cifra de "Puedes gastar" como tope diario el resto del mes.', '100·e^(−3·exceso medio) · ajuste por el ritmo de este mes',
    )
    const first31 = forecast ? forecast.points.slice(0, 31) : []
    const low = first31.length === 0 ? null : Math.min(...first31.map((p) => p[1]))
    const lowRel = low != null && essentials > 0 ? low / essentials : null
    const c3 = cf(
      'Previsibilidad del saldo', lowRel != null ? (lowRel <= 0 ? 0.0 : saturate(lowRel, 0.25)) : null, 0.20,
      low != null ? `Tu saldo más bajo previsto en 30 días es ${fmt(low)}` + (lowRel != null ? ` (${f1(lowRel)} meses de gastos esenciales). 0,25 ≈ 63; 0,5 ≈ 86.` : '.') : 'Sin previsión.',
      'Si el saldo previsto baja mucho antes de cobrar, reserva antes los pagos grandes.', '100·(1 − e^(−(saldo mínimo/gastos esenciales)/0,25))',
    )
    const mon = monday(today)
    const variable = d.spending.filter((e) => !Budgeting.isFixed(e, d.bills))
    const weeks = range(1, 8).map((w) => {
      const s = ep(minusWeeks(mon, w))
      return sumOf(variable.filter((e) => e.epochDay >= s && e.epochDay <= s + 6), (e) => e.amount)
    })
    const firstVar = variable.length === 0 ? null : Math.min(...variable.map((e) => e.epochDay))
    const weeksWithData = firstVar == null ? 0 : weeks.filter((_, i) => ep(minusWeeks(mon, i + 1)) >= firstVar).length
    const weeklyCv = weeksWithData >= 4 ? cv(weeks.slice(0, weeksWithData)) : null
    const c4 = cf(
      'Regularidad del gasto', weeklyCv != null ? decay(weeklyCv, 1.5) : null, 0.15,
      weeklyCv != null ? `Tu gasto semanal varía un ${pc(weeklyCv)} (${weeksWithData} semanas). 30 % ≈ 64; 60 % ≈ 41.` : 'Hacen falta 4 semanas de gastos.',
      'Reparte los gastos grandes: ir semana a semana evita sustos a fin de mes.', '100·e^(−1,5·CV semanal)',
    )
    const last90 = variable.filter((e) => e.epochDay > ep(today) - 90)
    const tot90 = sumOf(last90, (e) => e.amount)
    const wantsShare = tot90 > 0 ? sumOf(last90.filter((e) => !SPEND_TAG_INFO[spendTag(e)].need), (e) => e.amount) / tot90 : null
    const wantsTarget = sumOf(d.categories.filter((c) => !c.isSavings && !Classifier.normalize(c.name).includes('necesidad')), (c) => c.percent) /
      Math.max(sumOf(d.categories.filter((c) => !c.isSavings), (c) => c.percent), 1.0)
    const c5 = cf(
      'Caprichos frente a tu plan', wantsShare != null ? decay(wantsShare - wantsTarget, 3.0) : null, 0.10,
      wantsShare != null ? `El ${pc(wantsShare)} de tu gasto variable es prescindible; tu plan permite ${pc(wantsTarget)}.` : 'Sin gastos en 90 días.',
      'Pon un límite semanal a tus caprichos principales (restaurantes, compras).', '100·e^(−3·exceso sobre tu plan)',
    )

    // ================= PROGRESO =================
    const inc3 = sumOf(closed3, (m) => d.receivedIn(m))
    const rate = inc3 > 0 ? (inc3 - sumOf(closed3, (m) => d.spentIn(m))) / inc3 : null
    const p1 = cf(
      'Tasa de ahorro', rate != null ? (rate <= 0 ? 0.0 : saturate(rate, 0.12)) : null, 0.35,
      rate != null ? `Ahorras el ${pc(rate)} de lo que cobras (${closed3.length} meses). 10 % ≈ 56; 20 % ≈ 81; 30 % ≈ 92.` : 'Hace falta un mes cerrado con cobros.',
      'Automatiza el ahorro el día de cobro: lo que no ves, no lo gastas.', '100·(1 − e^(−tasa/0,12))',
    )
    const nets = closed6.filter((m) => d.receivedIn(m) > 0).map((m) => d.receivedIn(m) - d.spentIn(m))
    const positive = nets.filter((v) => v > 0).length
    const p2 = cf(
      'Constancia', nets.length >= 2 ? 100 * Math.pow(positive / nets.length, 1.5) : null, 0.20,
      nets.length >= 2 ? `${positive} de ${nets.length} meses cerraste ahorrando.` : 'Hacen falta 2 meses cerrados.',
      'Aunque sea poco, cierra cada mes en positivo: la constancia pesa más que un mes bueno.', '100·(meses en positivo/meses)^1,5',
    )
    const avgNet = nets.length > 0 ? average(nets) : null
    const buildYears = avgNet != null && essentials > 0 ? 12 * avgNet / essentials : null
    const p3 = cf(
      'Patrimonio construido por ti', buildYears != null ? (buildYears <= 0 ? 0.0 : saturate(buildYears, 2.0)) : null, 0.25,
      buildYears != null ? `A tu ritmo sumas ${f1(buildYears)} meses de gastos esenciales al año a tu patrimonio, sin contar subidas de bolsa. 2 ≈ 63; 4 ≈ 86.` : 'Hacen falta meses cerrados.',
      'Cada mes que ahorras compra tiempo: apunta a sumar 4 meses de gastos al año.', '100·(1 − e^(−meses de gastos ahorrados al año/2))',
    )
    const g = goals.find((x) => !x.done) ?? goals[0] ?? null
    const goalRatio = g ? (g.done ? 1.0 : g.projected / g.goal.target) : null
    const p4 = cf(
      'Camino a tus objetivos', goalRatio != null ? 100 * Math.pow(Math.min(1.0, Math.max(0, goalRatio)), 1.5) * (goalRatio >= 1 ? 0.97 : 1.0) : null, 0.20,
      g ? `"${g.goal.name}": a tu ritmo tendrías el ${pc(Math.min(1.5, goalRatio ?? 0))} en la fecha.` : 'No tienes objetivos con importe y fecha.',
      'Define un objetivo con importe y fecha y aparta cada mes lo que te dice la app.', '100·(previsto en la fecha/objetivo)^1,5',
    )

    const conf = (factors: CircleFactor[]) => dataConfidence * (sumOf(factors.filter((f) => f.score != null), (f) => f.weight) / sumOf(factors, (f) => f.weight))
    const est = [e1, e2, e3, e4, e5]
    const con = [c1, c2, c3, c4, c5]
    const pro = [p1, p2, p3, p4]
    return [
      makeFinanceCircle('stability', 'Estabilidad', '¿Qué pasaría si mañana tuvieses un imprevisto?', est, conf(est)),
      makeFinanceCircle('control', 'Control', '¿Puedes mantener tu estilo de vida sin comprometer tus pagos?', con, conf(con)),
      makeFinanceCircle('progress', 'Progreso', '¿Estás avanzando hacia la vida financiera que quieres?', pro, conf(pro)),
    ]
  },

  /** "Ha subido 6 puntos desde la semana pasada": compara con la puntuación guardada hace ≥7 días. */
  changeText(now: number, weekAgo: number | null): string {
    if (weekAgo == null) return 'Aún no hay datos de la semana pasada: en unos días verás cómo ha cambiado.'
    if (now - weekAgo >= 3) return `Ha subido ${Math.trunc(now - weekAgo)} puntos desde la semana pasada.`
    if (weekAgo - now >= 3) return `Ha bajado ${Math.trunc(weekAgo - now)} puntos desde la semana pasada.`
    return 'Igual que la semana pasada.'
  },

  /** Historial "díaEpoch:est,con,pro;…" (como mucho 40 días). */
  encodeHistory(map: Map<number, number[]>): string {
    return [...map.entries()].sort((a, b) => a[0] - b[0]).slice(-40).map(([k, v]) => `${k}:${v.map((x) => String(Math.trunc(x))).join(',')}`).join(';')
  },
  decodeHistory(raw: string | null | undefined): Map<number, number[]> {
    const out = new Map<number, number[]>()
    for (const e of (raw ?? '').split(';')) {
      const kv = e.split(':')
      if (kv.length !== 2) continue
      const k = toLongOrNull(kv[0])
      if (k == null) continue
      out.set(k, kv[1].split(',').map((x) => toDoubleOrNull(x)).filter((x): x is number => x != null))
    }
    return out
  },

  weekAgo(history: Map<number, number[]>, today: LocalDate, index: number): number | null {
    const keys = [...history.keys()].filter((k) => k <= ep(today) - 7)
    if (keys.length === 0) return null
    const k = Math.max(...keys)
    return history.get(k)?.[index] ?? null
  },
}
