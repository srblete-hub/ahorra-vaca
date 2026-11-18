// Port de domain/Pulse.kt: el "pulso" de tu dinero (colchón, margen, semana, presión de gasto).
import type { LocalDate } from '../data/dates'
import { clamp, dow, ep, fixed, lenOfMonth, minusD, monday, range, sumOf, ymOfDate, ymLen } from './kt'
import { Budgeting } from './Budget'
import type { SpendingBudget } from './Budget'
import { Insights } from './Insights'
import type { FinanceData, HealthReport } from './Insights'
import type { BankPlan } from './Planning'

/** Zonas de color, como la recuperación de WHOOP: verde 67–100, amarillo 34–66, rojo 0–33. */
export type Zone = 'GREEN' | 'YELLOW' | 'RED'
export const ZONE_VALUES: Zone[] = ['GREEN', 'YELLOW', 'RED']

export function zoneOf(pct: number): Zone { return pct >= 67 ? 'GREEN' : pct >= 34 ? 'YELLOW' : 'RED' }

/** Un día: tu margen ese día (%) y lo que gastaste. */
export interface DayPulse { date: LocalDate; margin: number; spent: number }

/** Datos de partida del pulso (el constructor de Kotlin). */
export interface PulseInput {
  cushion: number
  margin: number
  weekSpent: number
  weekOptimal: number
  /** Lo óptimo de la semana hasta hoy (lunes = 1/7, domingo = toda). */
  weekOptimalToDate: number
  todayAllowance: number
  healthInRange: number
  healthTotal: number
  /** Presión de gasto: lo gastado hoy frente a lo que te toca hoy (1 = justo). */
  pressure: number
  days: DayPulse[]
  statement: string
  tip: string
  monthBudget?: number
  spentMonth?: number
  expectedSoFar?: number
  savings?: number
  monthlyCost?: number
  health?: HealthReport | null
  /** Lo gastado hoy (sin recibos). */
  spentToday?: number
}

/**
 * El "pulso" de tu dinero. Todo se compara con lo que tocaba *hasta hoy*, así el color tiene
 * sentido cualquier día del mes o de la semana:
 * - Colchón: tu ahorro frente a 3 meses de tu coste de vida.
 * - Margen: lo gastado este mes frente a lo que tocaba a estas alturas.
 * - Gasto de la semana: lo de desde el lunes frente a lo que tocaba hasta hoy.
 * Las propiedades calculadas (weekZone … optimalScore) se rellenan con makePulse().
 */
export interface Pulse extends Required<Omit<PulseInput, 'health'>> {
  health: HealthReport | null
  /** Color del gasto semanal según lo que tocaba hasta hoy. */
  weekZone: Zone
  pressureZone: Zone
  /** "BAJA", "MEDIA" o "ALTA". */
  pressureLabel: string
  /** Gasto de la semana en una escala cerrada 0–100 (logarítmica, como el Esfuerzo de WHOOP). */
  weekScore: number
  /** Marca gris: dónde deberías estar hoy en esa escala. */
  optimalScore: number
}

export function makePulse(i: PulseInput): Pulse {
  const weekZone: Zone = i.weekOptimalToDate <= 0 ? 'GREEN'
    : i.weekSpent <= i.weekOptimalToDate ? 'GREEN'
      : i.weekSpent <= i.weekOptimalToDate * 1.3 ? 'YELLOW' : 'RED'
  const pressureZone: Zone = i.pressure <= 1.0 ? 'GREEN' : i.pressure <= 1.3 ? 'YELLOW' : 'RED'
  return {
    cushion: i.cushion, margin: i.margin, weekSpent: i.weekSpent, weekOptimal: i.weekOptimal, weekOptimalToDate: i.weekOptimalToDate,
    todayAllowance: i.todayAllowance, healthInRange: i.healthInRange, healthTotal: i.healthTotal, pressure: i.pressure, days: i.days,
    statement: i.statement, tip: i.tip, monthBudget: i.monthBudget ?? 0, spentMonth: i.spentMonth ?? 0, expectedSoFar: i.expectedSoFar ?? 0,
    savings: i.savings ?? 0, monthlyCost: i.monthlyCost ?? 0, health: i.health ?? null, spentToday: i.spentToday ?? 0,
    weekZone, pressureZone,
    pressureLabel: pressureZone === 'GREEN' ? 'BAJA' : pressureZone === 'YELLOW' ? 'MEDIA' : 'ALTA',
    weekScore: PulseCalculator.spendScore(i.weekSpent, i.weekOptimal),
    optimalScore: PulseCalculator.spendScore(i.weekOptimalToDate, i.weekOptimal),
  }
}

/** La ficha de cada anillo o cuadro: qué mide, tu dato, lo óptimo y qué hacer hoy. Textos fijos. */
export interface PulseInfo { title: string; value: string; zone: Zone; what: string; now: string; optimal: string; todo: string }

export const PulseCalculator = {
  /**
   * Escala logarítmica 0–100 para el gasto, inspirada en el Esfuerzo de WHOOP (0–21, cada punto cuesta más):
   * 100 · ln(1 + 3r) / ln(10), con r = gastado / óptimo. Lo óptimo (r = 1) da 60; el doble, 85; el triple o más, 100.
   */
  spendScore(spent: number, optimal: number): number {
    if (optimal <= 0) return spent > 0 ? 100 : 0
    const r = Math.max(spent / optimal, 0)
    return clamp(100 * Math.log(1 + 3 * r) / Math.log(10.0), 0, 100)
  },

  /**
   * Margen de un día: lo gastado en el mes hasta ese día frente a lo que tocaba (con 2 días de cortesía,
   * para que una compra grande a principios de mes no te ponga en rojo).
   * Gastar un 40 % de lo que tocaba = 100 %; justo lo que tocaba = 50 %; un 60 % de más = 0 %.
   */
  marginFor(spentToDate: number, monthBudget: number, day: number, length: number): number {
    const allowance = monthBudget * Math.min(length, day + 2) / length
    if (allowance <= 0) return spentToDate <= 0 ? 100 : 0
    const ratio = spentToDate / allowance
    return clamp((1.6 - ratio) / 1.2, 0, 1) * 100
  },

  of(d: FinanceData, budget: SpendingBudget | null, plan: BankPlan | null, fmt: (v: number) => string): Pulse {
    const today = d.today
    const month = ymOfDate(today)
    const base = budget?.base ?? 0
    const spentUntil = (day: LocalDate) => sumOf(Budgeting.variable(d, ymOfDate(day)).filter((e) => e.epochDay <= ep(day)), (e) => e.amount)
    const days: DayPulse[] = range(0, 6).reverse().map((back) => {
      const day = minusD(today, back)
      const spentDay = sumOf(Budgeting.variable(d, ymOfDate(day)).filter((e) => e.epochDay === ep(day)), (e) => e.amount)
      return { date: day, margin: PulseCalculator.marginFor(spentUntil(day), base, day.day, lenOfMonth(day)), spent: spentDay }
    })
    const margin = days[days.length - 1].margin
    const cost = Insights.costOfLiving(d).monthly
    const cushion = cost > 0 ? Math.min(100, d.wealth.savings / (cost * 3) * 100) : 100
    const mon = ep(monday(today))
    const t = ep(today)
    const weekSpent = sumOf(d.spending.filter((e) => e.epochDay >= mon && e.epochDay <= t && !Budgeting.isFixed(e, d.bills)), (e) => e.amount)
    const len = ymLen(month)
    const weekOptimal = base / len * 7
    const weekToDate = weekOptimal * dow(today) / 7
    const spentMonth = spentUntil(today)
    const expectedSoFar = base * Math.min(len, today.day + 2) / len
    const health = Insights.health(d, fmt)
    const todayAllowance = Math.max(0, plan?.perDay ?? (base / len))
    // Presión de hoy: lo gastado hoy frente a lo que te toca hoy (1,0 = justo).
    const spentToday = days[days.length - 1].spent
    const pressure = todayAllowance > 0 ? spentToday / todayAllowance : spentToday > 0 ? 2.0 : 0

    const pulse = makePulse({
      cushion, margin, weekSpent, weekOptimal, weekOptimalToDate: weekToDate, todayAllowance, healthInRange: health.good,
      healthTotal: health.indicators.length, pressure, days, statement: '', tip: '',
      monthBudget: base, spentMonth, expectedSoFar, savings: d.wealth.savings, monthlyCost: cost, health, spentToday,
    })
    return { ...pulse, statement: statement(pulse, plan), tip: tip(pulse, fmt) }
  },

  info(key: string, p: Pulse, fmt: (v: number) => string, today: LocalDate): PulseInfo {
    switch (key) {
      case 'cushion': {
        const months = p.monthlyCost > 0 ? p.savings / p.monthlyCost : 0
        const z = zoneOf(p.cushion)
        return {
          title: 'Colchón', value: `${Math.trunc(p.cushion)} %`, zone: z,
          what: 'Cuánto te cubre tu ahorro si un mes no entrara dinero. 100 % = 3 meses de tu coste de vida.',
          now: `Tienes ${fmt(p.savings)} ahorrados: ${fixed(months, 1)} meses de tus ${fmt(p.monthlyCost)} de coste al mes.`,
          optimal: `100 % (3 meses: ${fmt(p.monthlyCost * 3)}).`,
          todo: z === 'GREEN' ? 'Bien cubierto. Lo que pase de 6 meses puede ir a inversiones a largo plazo.'
            : z === 'YELLOW' ? 'A medio camino. Aparta un poco cada mes en tu cuenta remunerada hasta llegar a 3 meses.'
              : `Colchón pequeño: tu primera meta debería ser llegar a 1 mes de gastos (${fmt(p.monthlyCost)}).`,
        }
      }
      case 'margin': {
        const z = zoneOf(p.margin)
        return {
          title: 'Margen', value: `${Math.trunc(p.margin)} %`, zone: z,
          what: 'Si vas por delante o por detrás del ritmo de gasto que te permite el mes. Se mide con lo que tocaba hasta hoy, así que es justo cualquier día del mes.',
          now: `A día ${today.day} llevas ${fmt(p.spentMonth)} de gasto del día a día; a estas alturas tocaban unos ${fmt(p.expectedSoFar)} (de ${fmt(p.monthBudget)} para todo el mes).`,
          optimal: '67 % o más (verde): gastar lo que toca da 50 %; gastar menos te sube.',
          todo: z === 'GREEN' ? `Vas por delante. Puedes gastar hasta ${fmt(p.todayAllowance)} hoy y seguir en verde.`
            : z === 'YELLOW' ? `Vas justo. Si hoy gastas menos de ${fmt(p.todayAllowance)}, mañana subirá.`
              : 'Vas por detrás. Un par de días gastando solo lo imprescindible te devuelve al amarillo.',
        }
      }
      case 'week':
        return {
          title: 'Gasto de la semana', value: `${Math.trunc(p.weekScore)}`, zone: p.weekZone,
          what: 'Lo que llevas gastado desde el lunes, en una escala de 0 a 100 que cuesta más subir cuanto más alto estás (como el Esfuerzo de WHOOP). El color compara con lo que tocaba hasta hoy.',
          now: `Llevas ${fmt(p.weekSpent)}. Hasta hoy (${dow(today)} de 7 días) tocaban ${fmt(p.weekOptimalToDate)}; para toda la semana, ${fmt(p.weekOptimal)}.`,
          optimal: `Quedarte en la marca gris: ${Math.trunc(p.optimalScore)} hoy, 60 al acabar la semana.`,
          todo: p.weekZone === 'GREEN' ? `Vas bien esta semana. Margen de gasto: ${fmt(Math.max(0, p.weekOptimal - p.weekSpent))} hasta el domingo.`
            : p.weekZone === 'YELLOW' ? `Un poco por encima de lo que tocaba. Deja el resto de la semana en ~${fmt(Math.max(0, p.weekOptimal - p.weekSpent) / Math.max(1, 8 - dow(today)))} al día.`
              : 'Bastante por encima. Lo que gastes de más esta semana se descuenta de las próximas.',
        }
      case 'health': {
        const h = p.health
        const z: Zone = p.healthInRange >= p.healthTotal - 0 ? 'GREEN' : p.healthInRange >= 3 ? 'YELLOW' : 'RED'
        return {
          title: 'Salud financiera', value: `${p.healthInRange}/${p.healthTotal}`, zone: z,
          what: 'Cinco indicadores de tu economía: capacidad de ahorro, estabilidad, peso de los gastos fijos, colchón y deudas.',
          now: h ? h.indicators.map((i) => `• ${i.name}: ${i.value} — ` + (i.status === 'GOOD' ? 'bien' : i.status === 'OK' ? 'mejorable' : 'a vigilar')).join('\n') : '',
          optimal: 'Los 5 en verde.',
          todo: h?.nextStep ?? '',
        }
      }
      default:
        return {
          title: 'Presión de gasto', value: fixed(p.pressure, 1), zone: p.pressureZone,
          what: 'Lo que llevas gastado hoy frente a lo que te toca hoy. 1,0 = justo lo de hoy.',
          now: `Hoy llevas ${fmt(p.spentToday)} y hoy te tocan ${fmt(p.todayAllowance)}.`,
          optimal: '1,0 o menos (verde). Hasta 1,3 es amarillo; más, rojo.',
          todo: p.pressureZone === 'GREEN' ? 'Sin presión hoy.'
            : p.pressureZone === 'YELLOW' ? 'Hoy vas algo por encima: lo de más se reparte entre los días que quedan.'
              : 'Hoy vas bastante por encima de lo que te toca.',
        }
    }
  },
}

/** Frases fijas según la situación (sin IA): se elige la que encaja. */
function statement(p: Pulse, plan: BankPlan | null): string {
  const redDays = p.days.slice(-3).filter((x) => zoneOf(x.margin) === 'RED').length
  if (redDays >= 3) return 'Llevas varios días seguidos en rojo: estás gastando más rápido de lo que te permite el mes. Hasta tu próximo cobro, prioriza lo imprescindible.'
  if (zoneOf(p.margin) === 'RED') return 'Margen bajo: vas por encima del ritmo que te permite el mes. Unos días tranquilos te devuelven al amarillo.'
  if (zoneOf(p.margin) === 'YELLOW' && p.weekZone !== 'GREEN') return 'Margen justo y semana por encima de lo que tocaba. Afloja unos días para no comerte el resto del mes.'
  if (zoneOf(p.margin) === 'YELLOW') return 'Margen moderado: vas en línea con lo que te permite el mes.'
  if (p.weekZone === 'RED') return 'Vas bien en el mes, pero esta semana gastas bastante más de lo que tocaba hasta hoy.'
  if (p.cushion < 34) return 'Tu día a día va bien, pero tu colchón aún es pequeño. Lo que te sobre, mejor al ahorro.'
  return 'Todo en verde: gastas por detrás de lo que te permite el mes' + (plan?.target ? `; buen momento para mandar algo extra a ${plan.target.name}.` : '.')
}

function tip(p: Pulse, fmt: (v: number) => string): string {
  return zoneOf(p.margin) === 'RED' ? `Hoy, mejor no gastar más de ${fmt(p.todayAllowance)}: así recuperas margen.`
    : `Hoy puedes gastar hasta ${fmt(p.todayAllowance)} y seguir en lo óptimo.`
}
