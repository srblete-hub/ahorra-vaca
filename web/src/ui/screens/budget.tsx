// Pestaña Movimientos (BudgetScreen.kt) y la pantalla de "Cuentas con amigos".
import { useEffect, useMemo, useRef, useState } from 'react'
import type { Category } from '../../data/models'
import { plusMonths, sameMonth, thisMonth, todayEpoch, toEpochDay, monthDays, ymOfEpoch, type YearMonth } from '../../data/dates'
import { db, useDB, useSettings } from '../../data/store'
import {
  ALLOCATION_PRESET_INFO, ALLOCATION_PRESET_VALUES, Allocation, Budgeting, Insights, Stats, byTag, useMonthIncome, usePlanning,
  type SpendTag,
} from '../../domain'
import { ScreenHeader } from '../header'
import { useNav } from '../nav'
import { Button, EmptyState, IconTile, Overlay, SERIES_COLORS, Segmented, StackedBar, useMoney } from '../components'
import { buildMoves, MoveSelectionBar, MovesSection, StatsSection, type MoveItem, type Selection } from './budget/moves'
import {
  AddButton, BillsList, BudgetWheelCard, CategoryRow, ExpenseRow, FriendsCard, IncomeSourceRow, Legend, MonthSelector, SectionTitle,
  SpentByTagCard, newBill, newIncomeSource,
} from './budget/widgets'
import { pct1 } from './budget/spend'
import './budget/budget.css'

/** Vista de Movimientos (0 calendario, 1 lista, 2 resumen, 3 gráficas) y mes elegido, mientras la app está abierta. */
let rememberedView = 1
let rememberedMonth: YearMonth | null = null

export function BudgetScreen() {
  const nav = useNav()
  const money = useMoney()
  const settings = useSettings()
  const bills = useDB((s) => s.bills)
  const allExpenses = useDB((s) => s.expenses)
  const categories = useDB((s) => s.categories)
  const accounts = useDB((s) => s.accounts)
  const sources = useDB((s) => s.incomeSources)
  const allIncomes = useDB((s) => s.incomes)
  const transfers = useDB((s) => s.transfers)
  const planning = usePlanning()

  const [month, setMonthState] = useState<YearMonth>(() => rememberedMonth ?? thisMonth())
  const setMonth = (m: YearMonth) => { rememberedMonth = m; setMonthState(m) }
  const [view, setViewState] = useState(rememberedView)
  const setView = (v: number) => { rememberedView = v; setViewState(v) }
  const [query, setQuery] = useState('')
  const [statsPeriod, setStatsPeriod] = useState(0)
  const [listPeriod, setListPeriod] = useState(1)
  const [selectedDay, setSelectedDay] = useState<number | null>(null)
  const [picked, setPicked] = useState<Record<string, MoveItem>>({})
  useEffect(() => { setPicked({}) }, [month.year, month.month, view])
  const sel: Selection = {
    picked,
    toggle: (m) => setPicked((p) => { const n = { ...p }; if (n[m.key]) delete n[m.key]; else n[m.key] = m; return n }),
  }

  const monthIncome = useMonthIncome(month)
  const isCurrent = sameMonth(month, thisMonth())
  const expenses = useMemo(() => allExpenses.filter((e) => sameMonth(ymOfEpoch(e.epochDay), month)), [allExpenses, month.year, month.month])

  // Copia local del reparto: los deslizadores la cambian al momento y se guarda al soltar.
  const [draft, setDraft] = useState<Category[]>(categories)
  useEffect(() => { setDraft(categories) }, [categories])
  const saveTimer = useRef<number | undefined>(undefined)
  const changeDraft = (next: Category[], now = false) => {
    setDraft(next)
    window.clearTimeout(saveTimer.current)
    if (now) db.saveCategories(next)
    else saveTimer.current = window.setTimeout(() => db.saveCategories(next), 500)
  }
  useEffect(() => () => window.clearTimeout(saveTimer.current), [])
  const [expandedId, setExpandedId] = useState<number | null>(null)

  const income = monthIncome.total
  const received = monthIncome.received
  const savingIds = useMemo(() => new Set(categories.filter((c) => c.isSavings).map((c) => c.id)), [categories])
  // Alquiler y fijos son de una vez al mes: van aparte para no desvirtuar el gasto del día a día.
  const spendingList = expenses.filter((e) => !savingIds.has(e.categoryId) && !Budgeting.isFixed(e, bills))
  const fixedMonth = expenses.filter((e) => !savingIds.has(e.categoryId) && Budgeting.isFixed(e, bills)).reduce((a, e) => a + e.amount, 0)
  const spentTotal = spendingList.reduce((a, e) => a + e.amount, 0)
  const setAside = expenses.filter((e) => savingIds.has(e.categoryId)).reduce((a, e) => a + e.amount, 0)
  const spentBy = new Map<number, number>()
  for (const e of expenses) spentBy.set(e.categoryId, (spentBy.get(e.categoryId) ?? 0) + e.amount)
  const tags = byTag(spendingList)
  // Tu liquidez es solo la cuenta del banco: los ahorros no se tocan.
  const liquid = accounts.filter((a) => a.type === 'BANK').reduce((a, x) => a + x.balance, 0)
  const totalPct = draft.reduce((a, c) => a + c.percent, 0)
  const mainSavings = draft.find((c) => c.isSavings)

  const openTag = (t: SpendTag) => nav.edit({ name: 'tagDetail', tag: t, month })

  let body: JSX.Element
  if (view === 3) {
    body = <StatsSection stats={Stats.of(planning.data, statsPeriod)} period={statsPeriod} onPeriod={setStatsPeriod} onTag={openTag} onEdit={nav.edit} />
  } else if (view !== 2) {
    const moves = buildMoves(month, allExpenses, allIncomes, transfers, accounts, bills)
    const len = monthDays(month)
    const allowance = planning.budget && planning.budget.base > 0 ? planning.budget.base / len : planning.safe.perDay ?? 0
    // Cobros que aún no han llegado este mes (nómina, YouTube el 22…), con su importe previsto.
    const expected = new Map<number, Array<[string, string]>>()
    if (isCurrent) {
      const today = todayEpoch()
      for (const sm of monthIncome.sources.filter((x) => !x.isReceived && x.amount > 0)) {
        const d = Math.max(toEpochDay({ year: month.year, month: month.month, day: Math.min(sm.source.dayFrom, len) }), today)
        expected.set(d, [...(expected.get(d) ?? []), [sm.source.name, (sm.isEstimate ? '+~' : '+') + Math.round(sm.amount)]])
      }
    }
    const sel2 = selectedDay != null && sameMonth(ymOfEpoch(selectedDay), month) ? selectedDay : null
    body = (
      <MovesSection
        calendar={view === 0} month={month} moves={moves} allowance={allowance}
        pending={isCurrent ? planning.bankPlan?.pendingTotal ?? 0 : 0}
        analytics={Stats.analytics(planning.data, listPeriod)} period={listPeriod} onPeriod={setListPeriod}
        expected={expected} query={query} onQuery={setQuery} selected={sel2} onSelect={setSelectedDay}
        onEdit={nav.edit} sel={sel}
      />
    )
  } else {
    const story = isCurrent ? Insights.monthStory(planning.data) : null
    body = (
      <>
        {/* ---------- Entró vs salió ---------- */}
        <div className="bd-mint">
          <div className="row" style={{ alignItems: 'flex-start', gap: 16 }}>
            <div className="grow">
              <div className="muted">Ha entrado</div>
              <div className="num bd-big" style={{ color: 'var(--gain)' }}>{money.fmt(received)}</div>
              {income > received + 1 && <div className="small muted">de ~{money.fmt(income)} previstos</div>}
            </div>
            <div className="grow right">
              <div className="muted">Has gastado</div>
              <div className="num bd-big">{money.fmt(spentTotal)}</div>
              <div className="small muted">{spendingList.length} pagos</div>
            </div>
          </div>
          <div style={{ marginTop: 16 }}>
            <StackedBar height={12} slices={[
              { label: 'Sin gastar', value: Math.max(0, received - spentTotal - setAside - fixedMonth), color: 'var(--gain)' },
              { label: 'Fijos', value: fixedMonth, color: '#8E8E93' },
              { label: 'Ahorro', value: setAside, color: SERIES_COLORS[1] },
              { label: 'Gastado', value: spentTotal, color: 'var(--loss)' },
            ]} />
          </div>
          <div className="bd-legend-row" style={{ marginTop: 8 }}>
            <Legend text="Sin gastar" color="var(--gain)" />
            <Legend text="Apartado" color={SERIES_COLORS[1]} />
            <Legend text="Gastado" color="var(--loss)" />
            {fixedMonth > 0 && <Legend text="Fijos" color="#8E8E93" />}
          </div>
          <p style={{ marginTop: 16 }}>
            {received <= 0 && spentTotal <= 0
              ? 'Aún no hay movimientos este mes.'
              : received <= 0
                ? `Aún no has cobrado este mes: llevas ${money.fmt(spentTotal)} gastados.`
                : `Has gastado el ${Math.trunc(spentTotal / received * 100)} % de lo que ha entrado`
                  + (setAside > 0 ? ` y has apartado ${money.fmt(setAside)}` : '') + '.'
                  + (fixedMonth > 0 ? ` Aparte, ${money.fmt(fixedMonth)} de alquiler y fijos (no cuentan aquí).` : '')
                  + (isCurrent ? ` En el banco tienes ${money.fmt(liquid)}: tu dinero disponible (los ahorros no cuentan).` : '')}
          </p>
        </div>

        {/* ---------- La rueda: en qué puedes gastar ---------- */}
        {isCurrent && planning.budget && planning.budget.base > 0 && (
          <BudgetWheelCard b={planning.budget} onProfile={() => nav.edit({ name: 'profileForm' })} onEditTag={openTag} />
        )}

        {/* ---------- ¿En qué se ha ido? ---------- */}
        {tags.length > 0 && <SpentByTagCard tags={tags} spendingList={spendingList} spentTotal={spentTotal} liquid={liquid} isCurrent={isCurrent} onTag={openTag} />}

        {/* ---------- Cuentas con amigos (y Tricount) ---------- */}
        <FriendsCard onEdit={nav.edit} />

        {/* ---------- ¿Dónde se ha ido tu dinero? ---------- */}
        {story && (story.spentPrev > 0 || story.incomePrev > 0) && (
          <div className="bd-card">
            <div className="bd-overline">¿Dónde se ha ido tu dinero?</div>
            <p style={{ marginTop: 8, lineHeight: 1.5 }}>{Insights.describe(story, money.fmt)}</p>
          </div>
        )}
        <button className="bd-card bd-shortcut" onClick={() => nav.open({ name: 'insights' })}>
          <IconTile icon="insights" color={SERIES_COLORS[1]} size={48} />
          <div style={{ fontWeight: 600, marginTop: 8 }} className="ellipsis">Análisis completo</div>
          <div className="small muted ellipsis">Salud financiera, hábitos, radar, máquina del tiempo…</div>
        </button>

        {/* ---------- Ingresos ---------- */}
        <SectionTitle title="Tus ingresos" action={<AddButton onClick={() => nav.edit({ name: 'incomeSource', source: newIncomeSource() })} />} />
        {sources.length === 0 ? (
          <div className="bd-card">
            <div style={{ fontWeight: 600, fontSize: 17 }}>Añade de dónde te entra el dinero</div>
            <div className="small muted" style={{ marginTop: 4 }}>Nómina fija, autónomo, YouTube… Con su día de cobro. Lo variable se calcula con la media de tus últimos 3 meses.</div>
            <div style={{ marginTop: 16 }}><Button icon="add" onClick={() => nav.edit({ name: 'incomeSource', source: newIncomeSource() })}>Añadir ingreso</Button></div>
          </div>
        ) : (
          <div className="bd-list">
            {monthIncome.sources.map((sm) => <IncomeSourceRow key={sm.source.id} sm={sm} month={month} accounts={accounts} onEdit={nav.edit} />)}
          </div>
        )}

        {/* ---------- Gastos fijos ---------- */}
        <SectionTitle title="Gastos fijos" action={<AddButton onClick={() => nav.edit({ name: 'bill', bill: newBill(categories) })} />} />
        {bills.length === 0 ? (
          <div className="bd-card">
            <div style={{ fontWeight: 600, fontSize: 17 }}>Alquiler, luz, suscripciones, seguros…</div>
            <div className="small muted" style={{ marginTop: 4 }}>Apúntalos una vez: así Ahorra, vaca sabe cuánto dinero tienes de verdad para gastar y te avisa antes de quedarte corto.</div>
          </div>
        ) : (
          <div className="col" style={{ gap: 8 }}>
            <BillsList bills={bills} expenses={allExpenses} categories={categories} accounts={accounts} onEdit={nav.edit} />
            <button className="link-btn" style={{ alignSelf: 'flex-start', paddingLeft: 8 }} onClick={() => nav.open({ name: 'future' })}>Ver tu futuro día a día →</button>
          </div>
        )}

        {/* ---------- Reparto ---------- */}
        <SectionTitle title="Reparto" action={<button className="bd-add" onClick={() => nav.edit({ name: 'categories' })}>Editar nombres</button>} />
        {mainSavings && (
          <div className="bd-card">
            <div className="row" style={{ alignItems: 'flex-end' }}>
              <div className="grow">
                <div style={{ fontWeight: 600, fontSize: 17 }}>¿Cuánto quieres ahorrar?</div>
                <div className="small muted">El resto se reparte solo entre lo demás</div>
              </div>
              <div className="num" style={{ fontSize: 24, fontWeight: 700, color: SERIES_COLORS[1] }}>{pct1(mainSavings.percent)}</div>
            </div>
            <input type="range" min={0} max={100} step={1} value={mainSavings.percent} aria-label="Porcentaje para ahorrar"
              className="bd-range" style={{ accentColor: SERIES_COLORS[1] }}
              onChange={(e) => changeDraft(Allocation.rebalance(draft, mainSavings.id, Math.round(Number(e.target.value))))} />
            <div className="small muted">{money.fmt(income * mainSavings.percent / 100)} al mes · {money.fmt(income * mainSavings.percent / 100 * 12)} al año</div>
            <div className="small muted" style={{ marginTop: 8 }}>Atajos (necesidades / ocio / ahorro):</div>
            <div className="row" style={{ gap: 8, marginTop: 8, flexWrap: 'wrap' }}>
              {ALLOCATION_PRESET_VALUES.map((p) => {
                const i = ALLOCATION_PRESET_INFO[p]
                return (
                  <button key={p} className="bd-preset" aria-label={i.label} onClick={() => changeDraft(Allocation.applyPreset(draft, p), true)}>
                    {Math.trunc(i.needs)}/{Math.trunc(i.wants)}/{Math.trunc(i.savings)}
                  </button>
                )
              })}
            </div>
          </div>
        )}
        {Math.abs(totalPct - 100) > 0.01 && <span className="bd-softtag">El reparto suma {pct1(totalPct)}, no 100 %</span>}
        <div className="small muted" style={{ paddingLeft: 8 }}>Toca una categoría para ajustar su %. Con el candado no se mueve.</div>
        <div className="bd-list">
          {draft.map((c, i) => (
            <CategoryRow
              key={c.id} c={c} index={i} income={income} used={spentBy.get(c.id) ?? 0}
              expanded={expandedId === c.id} onToggle={() => setExpandedId(expandedId === c.id ? null : c.id)}
              onPercent={(v) => changeDraft(Allocation.rebalance(draft, c.id, v))}
              onLock={() => changeDraft(draft.map((x) => (x.id === c.id ? { ...x, locked: !x.locked } : x)), true)}
            />
          ))}
        </div>

        {/* ---------- Pagos del mes ---------- */}
        <SectionTitle title="Pagos del mes" />
        {expenses.length === 0 ? (
          <div className="bd-card" style={{ padding: 8 }}>
            <EmptyState icon="receipt" title="Sin pagos este mes" body="Apúntalos con el + (o pega el aviso del banco)." />
          </div>
        ) : (
          <div className="bd-list">
            {[...expenses].sort((a, b) => b.epochDay - a.epochDay || b.createdAt - a.createdAt).map((e) => (
              <ExpenseRow key={e.id} e={e} categories={categories} accounts={accounts} onClick={() => nav.edit({ name: 'expense', expense: e })} />
            ))}
          </div>
        )}
      </>
    )
  }

  return (
    <div className="screen bd-screen">
      <ScreenHeader title="Movimientos" />
      <MonthSelector month={month} onShift={(by) => setMonth(plusMonths(month, by))} />
      <Segmented options={[1, 0, 3, 2]} value={view} onChange={setView} label={(v) => ['Calendario', 'Lista', 'Resumen', 'Gráficas'][v]} />
      {body}
      <MoveSelectionBar picked={Object.values(picked)} onDone={() => setPicked({})} />
    </div>
  )
}

/** Pantalla completa de "Cuentas con amigos" (y Tricount), con la misma tarjeta que Movimientos. */
export function FriendsScreen() {
  const nav = useNav()
  return (
    <Overlay title="Amigos" onClose={nav.back}>
      <FriendsCard onEdit={nav.edit} />
    </Overlay>
  )
}
