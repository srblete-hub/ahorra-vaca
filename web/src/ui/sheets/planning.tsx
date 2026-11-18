// Presupuesto y planes: ingreso mensual, reparto, reparto del saldo, apartar para un plan, desglose y límite
// de un tipo de gasto, forma de ahorrar, objetivo y año de nacimiento (Sheets.kt, BudgetWidgets.kt, GoalAgeScreens.kt).
import { useMemo, useState } from 'react'
import { BigAmountInput, Dot, IconTile, ProgressLine, SERIES_COLORS, TextField, parseAmount, useMoney } from '../components'
import { Icon } from '../icons'
import { useNav, type SheetRoute } from '../nav'
import { db, getState, useDB, useSettings } from '../../data/store'
import { NONE, type Category } from '../../data/models'
import { MONTHS, MONTHS_SHORT, WEEKDAYS, dayOfWeek, inMonth, lengthOfMonth, ofEpochDay, plusMonths, thisMonth, today, type LocalDate } from '../../data/dates'
import {
  Budgeting, Classifier, GoalList, LIFE_GOAL_INFO, LIFE_GOAL_VALUES, Merchants, SPEND_TAG_INFO, SavingProfile, isDebt, isSaving,
  makeTagLimit, spendTag, spendTagOf, usePlanning, type Goal, type SpendTag,
} from '../../domain'
import {
  Choice, Chips, FieldLabel, FormSheet, MerchantLogo, NumberField, Seg, TAG_COLOR, TAG_ICON, accountIcon, editable, pct1,
  useConfirmDelete, useCurrencySymbol,
} from './form'

type R<N extends SheetRoute['name']> = Extract<SheetRoute, { name: N }>
interface P<N extends SheetRoute['name']> { route: R<N>; onClose: () => void }

// ---------- Ingreso mensual y reparto ----------

export function MonthlyIncomeSheet({ onClose }: P<'monthlyIncome'>) {
  const settings = useSettings()
  const sym = useCurrencySymbol()
  const [text, setText] = useState(editable(settings.monthlyIncome))
  const value = parseAmount(text)
  return (
    <FormSheet title="Ingreso mensual" onClose={onClose} onPrimary={() => { db.setSettings({ monthlyIncome: value ?? 0 }); onClose() }} primaryEnabled={value != null && value >= 0}>
      <div className="sh-text">Lo que entra cada mes (nómina, etc.). Los porcentajes del presupuesto se calculan sobre esto.</div>
      <BigAmountInput value={text} onChange={setText} symbol={sym} />
    </FormSheet>
  )
}

interface CatRow { key: number; cat: Category; name: string; percent: string }

export function CategoriesSheet({ onClose }: P<'categories'>) {
  const initial = useDB((s) => s.categories)
  const [rows, setRows] = useState<CatRow[]>(() => initial.map((c, i) => ({ key: i, cat: c, name: c.name, percent: editable(c.percent) })))
  const [nextKey, setNextKey] = useState(initial.length)
  const total = rows.reduce((a, r) => a + (parseAmount(r.percent) ?? 0), 0)
  const valid = rows.length > 0 && rows.every((r) => r.name.trim() !== '' && (parseAmount(r.percent) ?? -1) >= 0)
  const removing = initial.some((c) => !rows.some((r) => r.cat.id === c.id))
  const set = (i: number, patch: Partial<CatRow>) => setRows((list) => list.map((r, j) => (j === i ? { ...r, ...patch } : r)))
  const ok = Math.abs(total - 100) < 0.01

  return (
    <FormSheet
      title="Reparto del ingreso"
      onClose={onClose}
      onPrimary={() => {
        db.saveCategories(rows.map((r) => ({ ...r.cat, id: r.cat.id || undefined, name: r.name.trim(), percent: parseAmount(r.percent) ?? 0 })))
        onClose()
      }}
      primaryEnabled={valid}
    >
      <div className="sh-text small">Marca como ahorro lo que no es para gastar.</div>
      {rows.map((r, i) => (
        <div key={r.key} className="sh-cat">
          <div className="sh-row" style={{ gap: 8 }}>
            <Dot color={SERIES_COLORS[i % SERIES_COLORS.length]} size={12} />
            <input className="grow" aria-label="Nombre" value={r.name} onChange={(e) => set(i, { name: e.target.value })} />
            <input className="pct num" aria-label="Porcentaje" inputMode="decimal" value={r.percent} onChange={(e) => set(i, { percent: e.target.value.replace(/[^0-9.,]/g, '') })} />
            <span className="muted">%</span>
            <button type="button" className="icon-btn" aria-label="Quitar" onClick={() => setRows((list) => list.filter((_, j) => j !== i))}>
              <Icon name="close" size={20} style={{ color: 'var(--muted)' }} />
            </button>
          </div>
          <button type="button" className={`sh-check ${r.cat.isSavings ? 'on' : ''}`} aria-pressed={r.cat.isSavings} onClick={() => set(i, { cat: { ...r.cat, isSavings: !r.cat.isSavings } })}>
            <Icon name={r.cat.isSavings ? 'checkBox' : 'checkBoxBlank'} />
            Es ahorro
          </button>
        </div>
      ))}
      <button type="button" className="sh-link" onClick={() => {
        setRows((list) => [...list, { key: nextKey, cat: { id: 0, name: 'Nueva', percent: 0, isSavings: false, locked: false }, name: 'Nueva', percent: '' }])
        setNextKey((k) => k + 1)
      }}>
        <span className="sh-row" style={{ gap: 8 }}><Icon name="add" size={20} /> Añadir categoría</span>
      </button>
      <div className="sh-soft num" style={{ color: ok ? 'var(--gain)' : 'var(--loss)', background: `color-mix(in srgb, ${ok ? 'var(--gain)' : 'var(--loss)'} 14%, transparent)` }}>
        Total: {pct1(total)}
      </div>
      {removing && <div className="sh-text small sh-loss">Al quitar una categoría se borran también sus gastos.</div>}
    </FormSheet>
  )
}

// ---------- Reparto del saldo y planes ----------

export function BankPlanSheet({ onClose }: P<'bankPlan'>) {
  const accounts = useDB((s) => s.accounts)
  const settings = useSettings()
  const planning = usePlanning()
  const [main, setMain] = useState<number | null>(() => planning.bankPlan?.main.id ?? null)
  const [target, setTarget] = useState<number | null>(() => planning.bankPlan?.target?.id ?? null)
  const [percent, setPercent] = useState(String(Math.trunc(settings.bankSavePct)))
  const n = parseInt(percent, 10)
  const pctValue = percent !== '' && n >= 0 && n <= 100 ? n : null
  return (
    <FormSheet
      title="Tu reparto"
      onClose={onClose}
      onPrimary={() => {
        if (main == null || pctValue == null) return
        db.setSettings({ bankMainId: main, bankTargetId: target ?? NONE, bankSavePct: pctValue })
        onClose()
      }}
      primaryEnabled={main != null && pctValue != null}
    >
      <div className="sh-text">Del dinero de tu cuenta principal, Ahorra, vaca descuenta los gastos fijos que faltan (el alquiler…) y te propone mandar un % al ahorro. Lo demás es lo que puedes gastar.</div>
      <FieldLabel>Cuenta principal (donde cobras y pagas)</FieldLabel>
      <Chips>
        {accounts.filter((a) => !(isSaving(a) || isDebt(a))).map((a) => (
          <Choice key={a.id} text={a.name} selected={a.id === main} onClick={() => { setMain(a.id); if (target === a.id) setTarget(null) }} icon={accountIcon(a.type)} />
        ))}
      </Chips>
      <FieldLabel>¿Adónde va tu ahorro?</FieldLabel>
      <Chips>
        {accounts.filter((a) => a.id !== main && !isDebt(a)).map((a) => (
          <Choice key={a.id} text={a.name + (a.interestRate > 0 ? ` · ${pct1(a.interestRate)}` : '')} selected={a.id === target} onClick={() => setTarget(a.id)} icon={accountIcon(a.type)} />
        ))}
      </Chips>
      <NumberField label="% de lo que te queda que va al ahorro" value={percent} onChange={setPercent} maxLength={3} />
    </FormSheet>
  )
}

/** Apartar dinero para un plan: no se cuenta como gastable hasta que lo quites. */
export function PlanSheet({ onClose }: P<'plan'>) {
  const settings = useSettings()
  const sym = useCurrencySymbol()
  const [n, setN] = useState(settings.planName)
  const [a, setA] = useState(editable(settings.planReserve))
  const value = a.trim() === '' ? 0 : parseAmount(a)
  return (
    <FormSheet title="Apartar para un plan" onClose={onClose} onPrimary={() => { if (value != null) { db.setSettings({ planName: n.trim(), planReserve: value }); onClose() } }} primaryEnabled={value != null}>
      <div className="sh-text">Una escapada, un regalo, un concierto… Lo que apartes deja de contar como dinero para gastar hasta tu próximo cobro. Pon 0 para quitarlo.</div>
      <BigAmountInput value={a} onChange={setA} symbol={sym} />
      <TextField label="Para qué (Escapada a Lisboa…)" value={n} onChange={setN} />
    </FormSheet>
  )
}

// ---------- Tipo de gasto: desglose y límite ----------

const tagOf = (s: string): SpendTag => spendTagOf(s) ?? 'OTHER'
/** "mar. 3 nov." (EEE d MMM en español). */
function shortDay(d: LocalDate): string { return `${WEEKDAYS[dayOfWeek(d) - 1].slice(0, 3)}. ${d.day} ${MONTHS_SHORT[d.month - 1]}.` }

/** Al tocar un tipo de gasto: sus movimientos del mes, el total y cómo va frente al mes anterior. */
export function TagDetailSheet({ route, onClose }: P<'tagDetail'>) {
  const nav = useNav()
  const tag = tagOf(route.tag)
  const expenses = useDB((s) => s.expenses)
  const categories = useDB((s) => s.categories)
  const accounts = useDB((s) => s.accounts)
  const planning = usePlanning()
  const money = useMoney()
  const month = route.month ?? thisMonth()
  const label = SPEND_TAG_INFO[tag].label
  const { list, prev, total } = useMemo(() => {
    const spending = expenses.filter((x) => !categories.some((c) => c.id === x.categoryId && c.isSavings))
    const list = spending.filter((e) => inMonth(e.epochDay, month) && spendTag(e) === tag).sort((a, b) => b.epochDay - a.epochDay)
    const prevM = plusMonths(month, -1)
    const prev = spending.filter((e) => inMonth(e.epochDay, prevM) && spendTag(e) === tag).reduce((a, e) => a + e.amount, 0)
    return { list, prev, total: list.reduce((a, e) => a + e.amount, 0) }
  }, [expenses, categories, tag, month.year, month.month]) // eslint-disable-line react-hooks/exhaustive-deps
  const limit = planning.budget?.items.find((i) => i.tag === tag)?.monthly ?? null

  return (
    <FormSheet title={label} onClose={onClose} primary={`Fijar límite para ${label.toLowerCase()}`} onPrimary={() => nav.edit({ name: 'tagLimit', tag })}>
      <div className="sh-row" style={{ gap: 16 }}>
        <IconTile icon={TAG_ICON[tag]} color={TAG_COLOR[tag]} size={48} />
        <div className="grow">
          <div className="num" style={{ fontSize: 24, fontWeight: 800 }}>{money.fmt(total)}</div>
          <div className="sh-text small">
            {list.length} {list.length === 1 ? 'pago' : 'pagos'} en {MONTHS[month.month - 1]}{list.length > 0 ? ` · media ${money.fmt(total / list.length)}` : ''}
          </div>
        </div>
      </div>
      <div className="sh-text ink">
        {prev <= 0 && total <= 0 ? 'Sin gastos en este tipo ni este mes ni el anterior.'
          : prev <= 0 ? 'El mes anterior no gastaste nada en esto.'
            : total > prev ? `Llevas ${money.fmt(total - prev)} más que en todo el mes anterior (${money.fmt(prev)}).`
              : `El mes anterior gastaste ${money.fmt(prev)} en total: llevas ${money.fmt(prev - total)} menos.`}
      </div>
      {limit != null && limit > 0 && (
        <>
          <ProgressLine fraction={total / limit} color={TAG_COLOR[tag]} />
          <div className={`sh-text small ${total > limit ? 'sh-loss' : ''}`}>
            {total <= limit ? `Te quedan ${money.fmt(limit - total)} de tu límite de ${money.fmt(limit)}.` : `Te has pasado ${money.fmt(total - limit)} de tu límite de ${money.fmt(limit)}.`}
          </div>
        </>
      )}
      {list.length === 0 && <div className="sh-text">No hay movimientos de este tipo este mes.</div>}
      <div className="col" style={{ gap: 0 }}>
        {list.map((e) => (
          <button key={e.id} type="button" className="sh-item" onClick={() => nav.edit({ name: 'expense', expense: { id: e.id } })}>
            <MerchantLogo note={e.note} tag={tag} />
            <div className="grow" style={{ minWidth: 0 }}>
              <div className="t ellipsis">{Merchants.clean(e.note).trim() || label}</div>
              <div className="s ellipsis">
                {[shortDay(ofEpochDay(e.epochDay)), accounts.find((a) => a.id === e.accountId)?.name ?? 'sin cuenta', e.fromCard ? 'tarjeta' : null].filter(Boolean).join(' · ')}
              </div>
            </div>
            <div className="t num" style={{ flexShrink: 0 }}>−{money.fmt(e.amount)}</div>
          </button>
        ))}
      </div>
    </FormSheet>
  )
}

export function TagLimitSheet({ route, onClose }: P<'tagLimit'>) {
  const tag = tagOf(route.tag)
  const settings = useSettings()
  const money = useMoney()
  const sym = useCurrencySymbol()
  const current = Budgeting.parseLimits(settings.tagLimitsRaw)[tag] ?? null
  const [amount, setAmount] = useState(editable(current?.amount ?? 0))
  const [weekly, setWeekly] = useState(current?.weekly ?? true)
  const value = parseAmount(amount)
  const label = SPEND_TAG_INFO[tag].label
  /** setTagLimit del ViewModel: null = volver a automático. */
  const put = (limit: ReturnType<typeof makeTagLimit> | null) => {
    const cur = { ...Budgeting.parseLimits(getState().settings.tagLimitsRaw) }
    if (limit == null) delete cur[tag]; else cur[tag] = limit
    db.setSettings({ tagLimitsRaw: Budgeting.formatLimits(cur) })
    onClose()
  }
  return (
    <FormSheet title={label} onClose={onClose} onPrimary={() => { if (value != null) put(makeTagLimit(value, weekly)) }} primaryEnabled={value != null && value >= 0}>
      <div className="sh-text small">¿Cuánto sueles (o quieres) gastar en {label.toLowerCase()}? Lo descontamos primero y el resto se reparte entre lo demás.</div>
      <Seg options={[true, false]} value={weekly} onChange={setWeekly} label={(v) => (v ? 'Por semana' : 'Por mes')} />
      <BigAmountInput value={amount} onChange={setAmount} symbol={sym} />
      {value != null && (
        <div className="sh-text small sh-gain">{weekly ? `≈ ${money.fmt(value * 52 / 12)} al mes` : `≈ ${money.fmt(value * 12 / 52)} a la semana`}</div>
      )}
      {current != null && <button type="button" className="sh-link" onClick={() => put(null)}>Volver a automático</button>}
    </FormSheet>
  )
}

// ---------- Forma de ahorrar ----------

/** Al principio: qué objetivos tienes y cuán ahorrador quieres ser. Con eso se fijan los % de cada tipo de gasto. */
export function SavingProfileSheet({ onClose }: P<'profileForm'>) {
  const settings = useSettings()
  const [chosen, setChosen] = useState(settings.savingStyle < 0 ? 1 : settings.savingStyle)
  const [picked, setPicked] = useState<string[]>(settings.lifeGoals)
  const [cushion, setCushion] = useState(settings.bankBuffer)
  const [n, o, a] = SavingProfile.split(chosen)
  const w = SavingProfile.weights(chosen, picked) ?? {}
  const wTotal = Object.values(w).reduce((s, v) => s + (v ?? 0), 0) || 1
  const weights = (Object.entries(w) as Array<[SpendTag, number]>).sort((x, y) => y[1] - x[1])

  const save = () => {
    // setSavingProfile del ViewModel: guarda el perfil y ajusta los % del reparto.
    db.setSettings({ savingStyle: chosen, lifeGoals: picked, bankBuffer: cushion })
    const [needs, wants, savings] = SavingProfile.split(chosen)
    const cats = getState().categories
    const saving = cats.filter((c) => c.isSavings)
    const needCat = cats.find((c) => !c.isSavings && Classifier.normalize(c.name).includes('necesidad')) ?? cats.find((c) => !c.isSavings)
    const others = cats.filter((c) => !c.isSavings && c.id !== needCat?.id)
    const updated = cats.map((c) => {
      if (c.id === saving[0]?.id) return { ...c, percent: savings }
      if (c.isSavings) return { ...c, percent: 0 }
      if (c.id === needCat?.id) return { ...c, percent: others.length === 0 ? needs + wants : needs }
      return { ...c, percent: Math.round(wants / others.length * 10) / 10 }
    })
    if (updated.some((c, i) => c.percent !== cats[i].percent)) db.saveCategories(updated)
    onClose()
  }

  return (
    <FormSheet title="Tu forma de ahorrar" onClose={onClose} primary="Fijar mis porcentajes" onPrimary={save}>
      <div className="sh-text small">Con esto fijo cuánto puedes gastar en cada cosa y cuánto mandar al ahorro. Lo puedes cambiar cuando quieras.</div>
      <div className="sh-strong" style={{ fontWeight: 600 }}>¿Qué objetivos tienes?</div>
      <Chips>
        {LIFE_GOAL_VALUES.map((g) => {
          const on = picked.includes(g)
          return <Choice key={g} text={LIFE_GOAL_INFO[g].label} selected={on} onClick={() => setPicked(on ? picked.filter((x) => x !== g) : [...picked, g])} />
        })}
      </Chips>
      <div className="sh-strong" style={{ fontWeight: 600 }}>¿Cuán ahorrador quieres ser?</div>
      {SavingProfile.STYLES.map(([name, sub], i) => (
        <button key={name} type="button" className={`sh-style ${i === chosen ? 'on' : ''}`} aria-pressed={i === chosen} onClick={() => setChosen(i)}>
          <div className="t">{name}</div>
          <div className="s">{sub}</div>
        </button>
      ))}
      <div className="sh-strong" style={{ fontWeight: 600 }}>¿Con cuánto quieres llegar a fin de mes?</div>
      <div className="sh-text small">Lo que te sobra a final de mes para no quedarte en negativo. "Puedes gastar" ya lo descuenta.</div>
      <Chips>
        {[50, 100, 150, 200].map((b) => <Choice key={b} text={`+${b} €`} selected={Math.abs(b - cushion) < 0.5} onClick={() => setCushion(b)} />)}
      </Chips>
      <div className="sh-text small ink" style={{ fontWeight: 500 }}>
        Tu reparto del sueldo: {Math.trunc(n)} % necesidades · {Math.trunc(o)} % ocio · {Math.trunc(a)} % ahorro
      </div>
      <div className="sh-text small">Así quedaría lo que puedes gastar:</div>
      <div className="sh-text small ink">{weights.map(([t, v]) => `${SPEND_TAG_INFO[t].label} ${Math.trunc(v / wTotal * 100)} %`).join(' · ')}</div>
    </FormSheet>
  )
}

// ---------- Objetivo y edad ----------

function plusYears(d: LocalDate, years: number): LocalDate {
  const y = d.year + years
  return { year: y, month: d.month, day: Math.min(d.day, lengthOfMonth(y, d.month)) }
}

export function GoalSheet({ route, onClose }: P<'goal'>) {
  const settings = useSettings()
  const money = useMoney()
  const sym = useCurrencySymbol()
  const confirm = useConfirmDelete()
  const goals = useMemo(() => GoalList.parse(settings.goalsRaw), [settings.goalsRaw])
  const index = route.index
  const g: Goal | null = index != null ? goals[index] ?? null : null
  const t0 = today()
  const [n, setN] = useState(g?.name ?? '')
  const [amount, setAmount] = useState(editable(g?.target ?? 0))
  const [years, setYears] = useState(() => g ? Math.max(1, Math.trunc((g.deadline.year - t0.year) + (g.deadline.month - t0.month) / 12)) : 5)
  const value = parseAmount(amount)
  const saveGoals = (list: Goal[]) => db.setSettings({ goalsRaw: GoalList.format(list) })

  return (
    <FormSheet
      title="Tu objetivo"
      onClose={onClose}
      onPrimary={() => {
        if (value == null) return
        // setGoal del ViewModel: crea (sin índice) o cambia un objetivo, conservando su inicio.
        const list = GoalList.parse(getState().settings.goalsRaw)
        const start = index != null && list[index] ? list[index].start : t0
        const goal: Goal = { name: n.trim() || 'Mi objetivo', target: value, start, deadline: plusYears(t0, years) }
        if (index != null && index >= 0 && index < list.length) list[index] = goal; else list.push(goal)
        saveGoals(list)
        onClose()
      }}
      primaryEnabled={value != null && value > 0}
      onDelete={g ? () => confirm.ask('este objetivo', () => {
        saveGoals(GoalList.parse(getState().settings.goalsRaw).filter((_, i) => i !== index))
        onClose()
      }) : null}
    >
      <div className="sh-text small">Pon una cantidad y un plazo. Ahorra, vaca calcula cuánto apartar al mes (con los intereses de tu cuenta de ahorro), te dice si vas a tiempo y cómo llegar antes.</div>
      <BigAmountInput value={amount} onChange={setAmount} symbol={sym} autoFocus={!g} />
      <TextField label="Para qué (Entrada del piso, Colchón, Viaje…)" value={n} onChange={setN} />
      <FieldLabel>¿En cuánto tiempo?</FieldLabel>
      <Chips>
        {[1, 2, 3, 5, 10, 15].map((y) => <Choice key={y} text={`${y} ${y === 1 ? 'año' : 'años'}`} selected={y === years} onClick={() => setYears(y)} />)}
      </Chips>
      {value != null && <div className="sh-text small sh-gain">≈ {money.fmt(value / (years * 12))} al mes sin contar intereses</div>}
      {confirm.dialog}
    </FormSheet>
  )
}

export function AgeSheet({ onClose }: P<'ageForm'>) {
  const settings = useSettings()
  const [text, setText] = useState(settings.birthYear > 0 ? String(settings.birthYear) : '')
  const n = parseInt(text, 10)
  const year = n >= 1920 && n <= today().year - 14 ? n : null
  return (
    <FormSheet title="Tu año de nacimiento" onClose={onClose} onPrimary={() => { if (year != null) { db.setSettings({ birthYear: year }); onClose() } }} primaryEnabled={year != null}>
      <div className="sh-text small">Para calcular tu edad financiera la comparamos con tu edad real. Solo se guarda en tu móvil.</div>
      <NumberField label="Año (por ejemplo 1996)" value={text} onChange={setText} maxLength={4} />
    </FormSheet>
  )
}
