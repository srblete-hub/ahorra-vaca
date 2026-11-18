// Port de ui/screens/InsightsScreen.kt: todos los análisis en una pantalla; cada tarjeta responde a una pregunta.
import { useMemo, useState } from 'react'
import { useSettings } from '../../../data/store'
import { MONTHS, MONTHS_SHORT, WEEKDAYS, dayOfWeek, plusDays, toEpochDay, today as todayDate, type LocalDate } from '../../../data/dates'
import {
  Anomalies, Insights, PaycheckDistributor, PaymentOrganizer, RecoverableMoney, RecurringDetector, SPEND_TAG_INFO, TrendDetector,
  radarMonthlyReserve, spendTag, usePlanning, type FinanceData,
} from '../../../domain'
import { AnimatedAmount, Card, IconTile, Overlay, SERIES_COLORS, Segmented, useMoney } from '../../components'
import { useNav, type OverlayRoute } from '../../nav'
import {
  AnomaliesCard, BillCalendarCard, FinancialAgeMini, ORANGE, PaycheckSplitCard, RecoverableCard, RecurringCard, ShortcutCard, TrendsCard,
} from './cards'
import './insights.css'

/** "Hoy", "Mañana" o "mié. 18 nov." (prettyDate de FutureScreen.kt). */
export function prettyDate(d: LocalDate): string {
  const t = todayDate()
  const e = toEpochDay(d), te = toEpochDay(t)
  if (e === te) return 'Hoy'
  if (e === toEpochDay(plusDays(t, 1))) return 'Mañana'
  return `${WEEKDAYS[dayOfWeek(d) - 1].slice(0, 3)}. ${d.day} ${MONTHS_SHORT[d.month - 1]}.`
}

const Over = ({ children }: { children: string }) => <div className="in-over">{children}</div>

export function InsightsScreen({ route: _route }: { route: OverlayRoute }) {
  const p = usePlanning()
  const s = useSettings()
  const nav = useNav()
  const m = useMoney()
  const d = p.data
  const fmt = m.fmt

  const health = useMemo(() => Insights.health(d, fmt), [d, m.hidden, m.currency]) // eslint-disable-line react-hooks/exhaustive-deps
  const cost = useMemo(() => Insights.costOfLiving(d), [d])
  const radar = useMemo(() => Insights.radar(d), [d])
  const habits = useMemo(() => Insights.habits(d, fmt), [d, m.hidden, m.currency]) // eslint-disable-line react-hooks/exhaustive-deps
  const share = useMemo(() => Insights.shareable(d), [d])
  const owed = Insights.owed(d.splits, d.expenses)
  const story = useMemo(() => Insights.monthStory(d), [d])
  const rec = useMemo(() => RecoverableMoney.find(d, s.refundIds, fmt), [d, s.refundIds, m.hidden, m.currency]) // eslint-disable-line react-hooks/exhaustive-deps
  const recurring = useMemo(() => RecurringDetector.find(d), [d])
  const trends = useMemo(() => TrendDetector.find(d, fmt), [d, m.hidden, m.currency]) // eslint-disable-line react-hooks/exhaustive-deps
  const bills = useMemo(() => PaymentOrganizer.analyze(d, p.bankPlan, fmt), [d, p.bankPlan, m.hidden, m.currency]) // eslint-disable-line react-hooks/exhaustive-deps
  const paycheck = useMemo(() => PaycheckDistributor.split(d, p.goal, fmt), [d, p.goal, m.hidden, m.currency]) // eslint-disable-line react-hooks/exhaustive-deps
  const anomalies = useMemo(() => Anomalies.find(d, fmt), [d, m.hidden, m.currency]) // eslint-disable-line react-hooks/exhaustive-deps
  const safe = p.safe
  const spentToday = d.spending.filter((e) => e.epochDay === toEpochDay(d.today)).reduce((a, e) => a + e.amount, 0)
  const statusColor = (st: string) => (st === 'GOOD' ? 'var(--gain)' : st === 'OK' ? ORANGE : 'var(--loss)')

  return (
    <Overlay title="Análisis" onClose={nav.back}>
      <div className="in-sub">Tus números, explicados</div>

      <FinancialAgeMini age={p.age} onClick={() => nav.open({ name: 'age' })} />
      <RecoverableCard list={rec} onOpen={(r) => { if (r.expense) nav.edit({ name: 'expense', expense: r.expense }) }} />
      <RecurringCard list={recurring} />
      <TrendsCard list={trends} />
      {bills && <BillCalendarCard c={bills} />}
      {paycheck && <PaycheckSplitCard key={`${paycheck.label}${paycheck.income}`} s={paycheck} />}
      <AnomaliesCard list={anomalies} onOpen={(a) => { if (a.expense) nav.edit({ name: 'expense', expense: a.expense }) }} />
      <ShortcutCard title="¿Me lo compro?" sub="Antes de comprar algo, mira cómo afecta a tu mes y a tus objetivos" icon="cart" color={SERIES_COLORS[4]} onClick={() => nav.open({ name: 'purchase' })} />

      {/* ---------- Salud financiera ---------- */}
      <Card className="in-mint">
        <Over>Salud financiera</Over>
        <div style={{ fontSize: 28, fontWeight: 600, marginTop: 4, lineHeight: 1.2 }}>{health.good} de {health.indicators.length} en buen estado</div>
        {health.trend && <div className="small muted">{health.trend}</div>}
        <div style={{ marginTop: 12 }}>
          {health.indicators.map((i, k) => (
            <div key={k} className="in-line">
              <span className="in-dot" style={{ background: statusColor(i.status) }} />
              <div className="grow">
                <div className="row" style={{ gap: 8, alignItems: 'flex-start' }}>
                  <span className="grow" style={{ fontWeight: 500 }}>{i.name}</span>
                  <span style={{ fontWeight: 600, color: statusColor(i.status), flexShrink: 0 }}>{i.value}</span>
                </div>
                <div className="s">{i.explanation}</div>
              </div>
            </div>
          ))}
        </div>
        <div className="in-next">
          <div className="small muted">El siguiente paso más útil</div>
          <div style={{ fontWeight: 500 }}>{health.nextStep}</div>
        </div>
      </Card>

      {/* ---------- Hasta la próxima nómina ---------- */}
      <Card>
        <Over>Hasta la próxima nómina</Over>
        <div style={{ marginTop: 4 }}><AnimatedAmount value={Math.max(0, safe.perDay)} size={34} color={safe.safe > 0 ? 'var(--gain)' : 'var(--loss)'} /></div>
        <div className="small muted">
          al día durante {safe.daysToPayday} {safe.daysToPayday === 1 ? 'día' : 'días'}
          {safe.nextPayday ? ` · cobras el ${WEEKDAYS[dayOfWeek(safe.nextPayday) - 1]} ${safe.nextPayday.day}` : ' · hasta fin de mes'}
        </div>
        {spentToday > 0 && (
          <div className="small" style={{ marginTop: 8, color: spentToday > safe.perDay ? 'var(--loss)' : undefined }}>
            Hoy llevas {fmt(spentToday)}{spentToday > safe.perDay && safe.perDay > 0 ? `: te has pasado ${fmt(spentToday - safe.perDay)}, así que los próximos días tocan a menos.` : '.'}
          </div>
        )}
        {safe.plans > 0 && <div className="small" style={{ marginTop: 8, color: SERIES_COLORS[1] }}>Apartado para {s.planName.trim() || 'tu plan'}: {fmt(safe.plans)} (no cuenta como gastable)</div>}
        <button className="in-link" onClick={() => nav.edit({ name: 'plan' })}>{safe.plans > 0 ? 'Cambiar lo apartado' : 'Apartar dinero para un plan'}</button>
      </Card>

      {/* ---------- ¿Dónde se ha ido mi dinero? ---------- */}
      {(story.spentNow > 0 || story.spentPrev > 0) && (
        <Card>
          <Over>¿Dónde se ha ido tu dinero?</Over>
          <div style={{ marginTop: 8, lineHeight: 1.5 }}>{Insights.describe(story, fmt)}</div>
        </Card>
      )}

      {/* ---------- Coste de vida real ---------- */}
      <Card>
        <Over>Tu coste de vida real</Over>
        <div className="row" style={{ alignItems: 'baseline', flexWrap: 'wrap', gap: 4, marginTop: 4 }}>
          <span className="num" style={{ fontSize: 32, fontWeight: 600 }}>{fmt(cost.monthly)}</span>
          <span className="small muted">al mes · {fmt(cost.yearly)} al año</span>
        </div>
        <div style={{ marginTop: 8 }}>
          <CostLine label="Gastos fijos cada mes" value={cost.fixed} />
          <CostLine label="Gastos anuales repartidos (seguros…)" value={cost.yearlySpread} />
          <CostLine label="Tu gasto variable habitual" value={cost.variable} />
          {cost.forgotten > 0 && <CostLine label="Pagos de una vez al año que sueles olvidar" value={cost.forgotten} />}
        </div>
        {cost.thisMonthPace != null && (
          <div className="small" style={{ marginTop: 8 }}>
            {cost.thisMonthPace < cost.monthly * 0.9 ? `Este mes vas camino de ${fmt(cost.thisMonthPace)}: es un mes barato, pero tu estilo de vida real cuesta ${fmt(cost.monthly)}.`
              : cost.thisMonthPace > cost.monthly * 1.1 ? `Este mes vas camino de ${fmt(cost.thisMonthPace)}: por encima de lo que cuesta tu vida normal.`
                : 'Este mes vas en línea con tu coste de vida normal.'}
          </div>
        )}
      </Card>

      {/* ---------- Radar de gastos olvidados ---------- */}
      <Card>
        <Over>Radar de gastos olvidados</Over>
        {radar.length === 0 && <div className="small muted" style={{ marginTop: 8 }}>Nada a la vista en los próximos 4 meses. Apunta tus gastos anuales (seguros, ITV…) como gasto fijo y te avisaré.</div>}
        <div style={{ marginTop: 8 }}>
          {radar.map((r, i) => (
            <div key={i} className="in-line" style={{ alignItems: 'center', gap: 12 }}>
              <IconTile icon="autoAwesome" color={r.fromHistory ? SERIES_COLORS[3] : SERIES_COLORS[4]} size={40} />
              <div className="grow">
                <div style={{ fontWeight: 500 }} className="ellipsis">{r.name}</div>
                <div className="s">{prettyDate(r.due) + (r.fromHistory ? ' · lo pagaste hace un año' : ' · gasto anual')}</div>
              </div>
              <div className="r">
                <div className="num" style={{ fontWeight: 500 }}>{fmt(r.amount)}</div>
                <div className="tiny num" style={{ color: SERIES_COLORS[1] }}>aparta {fmt(radarMonthlyReserve(r, d.today))}/mes</div>
              </div>
            </div>
          ))}
        </div>
      </Card>

      {/* ---------- Mapa de hábitos ---------- */}
      {habits && (
        <Card>
          <Over>Tus hábitos de gasto</Over>
          <div className="in-bars" style={{ height: 112, marginTop: 8 }}>
            {['L', 'M', 'X', 'J', 'V', 'S', 'D'].map((label, i) => {
              const max = Math.max(...habits.byWeekday) || 1
              const f = Math.min(1, Math.max(0.04, habits.byWeekday[i] / max))
              return (
                <div key={label}>
                  <i style={{ height: `${f * 82}%`, background: i >= 5 ? SERIES_COLORS[2] : SERIES_COLORS[1] }} />
                  <span style={{ fontSize: 12 }}>{label}</span>
                </div>
              )
            })}
          </div>
          <div style={{ marginTop: 8 }}>{habits.notes.map((n, i) => <div key={i} className="small" style={{ padding: '2px 0' }}>• {n}</div>)}</div>
          <div className="tiny muted" style={{ marginTop: 8 }}>Últimos {habits.days} días, sin contar gastos fijos.</div>
        </Card>
      )}

      {/* ---------- Máquina del tiempo ---------- */}
      <TimeMachineCard d={d} fmt={fmt} />

      {/* ---------- Gastos compartibles ---------- */}
      <Card>
        <div className="row" style={{ gap: 8, flexWrap: 'wrap' }}>
          <div className="grow"><Over>Gastos que puedes compartir</Over></div>
          {owed > 0 && <span className="small" style={{ fontWeight: 600, color: SERIES_COLORS[1] }}>Te deben {fmt(owed)}</span>}
        </div>
        <div style={{ marginTop: 8 }}>
          {d.splits.map((sp) => {
            const e = d.expenses.find((x) => x.id === sp.expenseId)
            if (!e) return null
            const pending = Math.max(0, sp.people - 1 - sp.settled)
            return <SplitRow key={sp.expenseId} title={e.note} sub={`Entre ${sp.people}` + (sp.note.trim() ? ` · ${sp.note}` : '') + (pending === 0 ? ' · saldado' : ` · faltan ${pending}`)}
              value={fmt(e.amount / sp.people * pending)} color={pending === 0 ? 'var(--gain)' : SERIES_COLORS[1]} onClick={() => nav.edit({ name: 'split', expenseId: e.id })} />
          })}
          {share.length === 0 && d.splits.length === 0 && <div className="small muted">No veo gastos recientes que suelan compartirse (facturas de casa, plataformas, viajes, cenas en grupo).</div>}
          {share.map((c) => (
            <SplitRow key={c.expense.id} title={c.expense.note.trim() || SPEND_TAG_INFO[spendTag(c.expense)].label} sub={c.reason} value={fmt(c.expense.amount)}
              onClick={() => nav.edit({ name: 'split', expenseId: c.expense.id })} />
          ))}
        </div>
      </Card>

      {/* ---------- ¿Y si…? ---------- */}
      <WhatIfCard d={d} fmt={fmt} />

      <ShortcutCard title="Simulador de decisiones" sub="Mudarte, cambiar de trabajo, comprar un coche…" icon="psychology" color={SERIES_COLORS[4]} onClick={() => nav.open({ name: 'simulator' })} />
    </Overlay>
  )
}

function CostLine({ label, value }: { label: string; value: number }) {
  const m = useMoney()
  return (
    <div className="row small" style={{ gap: 8, padding: '4px 0', alignItems: 'flex-start' }}>
      <span className="grow muted">{label}</span>
      <span className="num" style={{ fontWeight: 500 }}>{m.fmt(value)}</span>
    </div>
  )
}

function SplitRow({ title, sub, value, color, onClick }: { title: string; sub: string; value: string; color?: string; onClick: () => void }) {
  return (
    <button className="in-click row" style={{ gap: 12, padding: '8px 0' }} onClick={onClick}>
      <IconTile icon="groups" color={SERIES_COLORS[5]} size={40} />
      <div className="grow">
        <div className="ellipsis" style={{ fontWeight: 500 }}>{title}</div>
        <div className="small muted">{sub}</div>
      </div>
      <span className="num" style={{ fontWeight: 500, color, flexShrink: 0 }}>{value}</span>
    </button>
  )
}

function TimeMachineCard({ d, fmt }: { d: FinanceData; fmt: (v: number) => string }) {
  const earliest = Insights.earliestMonthsBack(d)
  const STD = [1, 3, 6, 12]
  const options = [...STD.filter((o) => o <= Math.max(1, earliest)), ...(earliest > 1 && !STD.includes(earliest) ? [earliest] : [])]
  const [back, setBack] = useState(() => [...options].reverse().find((o) => o <= 6) ?? 1)
  const sel = options.includes(back) ? back : options[options.length - 1]
  const t = useMemo(() => Insights.timeTravel(d, sel), [d, sel])
  const series = useMemo(() => Insights.netWorthByMonth(d), [d])
  const max = Math.max(...series.map((x) => x[1]), 0) || 1
  return (
    <Card>
      <Over>Máquina del tiempo</Over>
      <div className="in-seg" style={{ marginTop: 8 }}>
        <Segmented options={options} value={sel} onChange={setBack} label={(o) => (o === earliest && !STD.includes(o) ? 'Inicio' : o === 12 ? '1 año' : `${o} m`)} />
      </div>
      <div style={{ marginTop: 12 }}>
        {Insights.describeTimeTravel(t, sel, fmt).map((line, i) => <div key={i} style={{ padding: '4px 0' }}>{line}</div>)}
      </div>
      {series.length >= 2 && <>
        <div className="small muted" style={{ marginTop: 12 }}>Tu patrimonio neto, mes a mes</div>
        <div className="in-bars" style={{ height: 96, marginTop: 8, gap: 4 }}>
          {series.map(([mo, v], i) => (
            <div key={i}>
              <i style={{ height: `${Math.min(1, Math.max(0.05, v / max)) * 80}%`, background: i === series.length - 1 ? 'var(--gain)' : 'color-mix(in srgb, var(--gain) 35%, transparent)' }} />
              <span>{MONTHS[mo.month - 1][0].toUpperCase()}</span>
            </div>
          ))}
        </div>
        <div className="small muted" style={{ marginTop: 8 }}>
          Liquidez (lo del día a día): {fmt(d.wealth.available)} · Riqueza construida (ahorro + inversión − deudas): {fmt(d.wealth.built)}
        </div>
      </>}
    </Card>
  )
}

function WhatIfCard({ d, fmt }: { d: FinanceData; fmt: (v: number) => string }) {
  const [monthly, setMonthly] = useState(25)
  const list = useMemo(() => Insights.whatIfs(d, monthly, fmt), [d, monthly, fmt])
  if (list.length === 0) return null
  return (
    <Card>
      <Over>¿Y si…?</Over>
      <div className="small muted">Simulaciones: no es dinero que hayas perdido, es lo que podrías conseguir.</div>
      <div className="row" style={{ marginTop: 8 }}>
        <span className="grow small">Reservar al mes</span>
        <span className="num" style={{ fontWeight: 600, color: SERIES_COLORS[1] }}>{fmt(monthly)}</span>
      </div>
      <input type="range" min={5} max={300} step={5} value={monthly} aria-label="Reservar al mes" style={{ accentColor: SERIES_COLORS[1], margin: '8px 0' }}
        onChange={(e) => setMonthly(Math.max(5, Math.round(+e.target.value / 5) * 5))} />
      {list.map((w, i) => (
        <div key={i} style={{ borderTop: i > 0 ? '1px solid var(--line)' : undefined, padding: '8px 0' }}>
          <div style={{ fontWeight: 500 }}>{w.title}</div>
          <div className="small gain">{w.detail}</div>
        </div>
      ))}
    </Card>
  )
}
