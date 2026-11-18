// Piezas de Movimientos: selector de mes, la rueda (BudgetWidgets.kt), cuentas con amigos (FriendsUi.kt),
// ingresos (IncomeSourceRow), gastos fijos (BillsList) y el reparto (CategoryRow).
import { useMemo, useState, type ReactNode } from 'react'
import type { Account, Category, Expense, IncomeSource, RecurringBill } from '../../../data/models'
import { MONTHS, capitalize, dayLabel, thisMonth, compareYm, toEpochDay, monthDays, sameMonth, type YearMonth } from '../../../data/dates'
import { db, getState, useSettings } from '../../../data/store'
import { Bills, Debts, SPEND_TAG_INFO, spendTag, type FriendBalance, type SourceMonth, type SpendingBudget, type SpendTag } from '../../../domain'
import type { SheetRoute } from '../../nav'
import { Donut, IconButton, IconTile, ProgressLine, SERIES_COLORS, colorFor, useMoney } from '../../components'
import { Icon } from '../../icons'
import { TAG_COLOR, TAG_ICON, categoryColor, categoryIcon, dayShortDot, pct1 } from './spend'

type Edit = (r: SheetRoute) => void

export function MonthSelector({ month, onShift }: { month: YearMonth; onShift: (by: number) => void }) {
  const canNext = compareYm(month, thisMonth()) < 0
  return (
    <div className="row" style={{ gap: 8 }}>
      <IconButton icon="chevronLeft" label="Mes anterior" filled onClick={() => onShift(-1)} />
      <div className="grow" style={{ display: 'flex', justifyContent: 'center' }}>
        <div className="bd-month">{capitalize(MONTHS[month.month - 1])} de {month.year}</div>
      </div>
      {canNext ? <IconButton icon="chevronRight" label="Mes siguiente" filled onClick={() => onShift(1)} /> : <div style={{ width: 48, flexShrink: 0 }} />}
    </div>
  )
}

export function SectionTitle({ title, action }: { title: string; action?: ReactNode }) {
  return (
    <div className="bd-section">
      <h2 className="grow">{title}</h2>
      {action}
    </div>
  )
}
export function AddButton({ label = 'Añadir', onClick }: { label?: string; onClick: () => void }) {
  return <button className="bd-add" onClick={onClick}><Icon name="add" size={20} />{label}</button>
}

export function Legend({ text, color }: { text: string; color: string }) {
  return <span className="bd-legend"><span className="dot" style={{ background: color }} />{text}</span>
}

// ---------- La rueda: en qué puedes gastar ----------

/** Qué % de lo que tienes para gastar va a cada cosa, en euros al mes y a la semana. */
export function BudgetWheelCard({ b, onProfile, onEditTag }: { b: SpendingBudget; onProfile?: () => void; onEditTag: (t: SpendTag) => void }) {
  const money = useMoney()
  const items = b.items.filter((it) => it.monthly >= 1 || it.spent > 0)
  return (
    <div className="bd-card">
      <h2 className="bd-h2">En qué puedes gastar</h2>
      <div className="small muted">
        {b.fromBank
          ? 'Lo que te queda este mes después del alquiler, los fijos y el ahorro, repartido por tipo de gasto'
          : `Ingresos ${money.fmt(b.income)} − fijos ${money.fmt(b.fixed)} − ahorro ${money.fmt(b.saving)}`}
      </div>
      <div className="bd-center" style={{ marginTop: 16 }}>
        <Donut slices={items.map((it) => ({ label: SPEND_TAG_INFO[it.tag].label, value: it.monthly, color: TAG_COLOR[it.tag] }))} size={190} thickness={24}>
          <div className="col" style={{ gap: 0, alignItems: 'center' }}>
            <span className="num" style={{ fontSize: 20, fontWeight: 600 }}>{money.fmt(b.base)}</span>
            <span className="tiny muted" style={{ maxWidth: 120 }}>este mes para gastar</span>
          </div>
        </Donut>
      </div>
      <div className="small muted" style={{ marginTop: 8 }}>Toca una para fijar cuánto quieres gastar (por semana o por mes); el resto se reparte según tu perfil de ahorrador.</div>
      {onProfile && <button className="link-btn" style={{ marginTop: 4 }} onClick={onProfile}>Cambiar mi perfil de ahorrador</button>}
      <div style={{ marginTop: 8 }}>
        {items.map((t) => {
          const c = TAG_COLOR[t.tag]
          return (
            <button key={t.tag} className="bd-wheel-row" onClick={() => onEditTag(t.tag)}>
              <div className="row">
                <IconTile icon={TAG_ICON[t.tag]} color={c} size={40} />
                <div className="grow">
                  <div style={{ fontWeight: 500 }}>{SPEND_TAG_INFO[t.tag].label}</div>
                  <div className="small muted">{money.fmt(t.monthly)}/mes · {money.fmt(t.weekly)}/semana{t.fixedByYou ? ' · fijado por ti' : ''}</div>
                </div>
                <span className="num" style={{ fontSize: 20, fontWeight: 600, color: c }}>{Math.trunc(t.pct)} %</span>
              </div>
              <div className="row" style={{ gap: 8, paddingLeft: 52, marginTop: 8 }}>
                <div className="grow"><ProgressLine fraction={t.monthly > 0 ? t.spent / t.monthly : 0} color={t.monthly > 0 && t.spent > t.monthly ? 'var(--loss)' : c} /></div>
                <span className="tiny" style={{ color: t.left >= 0 ? 'var(--muted)' : 'var(--loss)', whiteSpace: 'nowrap' }}>
                  {t.left >= 0 ? `quedan ${money.fmt(t.left)}` : `te pasas ${money.fmt(-t.left)}`}
                </span>
              </div>
            </button>
          )
        })}
      </div>
    </div>
  )
}

// ---------- ¿En qué se ha ido? ----------

export function SpentByTagCard({ tags, spendingList, spentTotal, liquid, isCurrent, onTag }: {
  tags: Array<[SpendTag, number]>; spendingList: Expense[]; spentTotal: number; liquid: number; isCurrent: boolean; onTag: (t: SpendTag) => void
}) {
  const money = useMoney()
  const needs = tags.filter(([t]) => SPEND_TAG_INFO[t].need).reduce((a, [, v]) => a + v, 0)
  const total = spentTotal || 1
  return (
    <div className="bd-card">
      <h2 className="bd-h2">¿En qué se ha ido?</h2>
      <div className="small muted">Toca una sección para ver en qué se ha ido, pago a pago</div>
      <div className="bd-center" style={{ marginTop: 16 }}>
        <Donut slices={tags.map(([t, v]) => ({ label: SPEND_TAG_INFO[t].label, value: v, color: TAG_COLOR[t] }))} size={176} thickness={24}>
          <div className="col" style={{ gap: 0, alignItems: 'center', maxWidth: 120 }}>
            <span className="num" style={{ fontSize: 20, fontWeight: 600 }}>{money.fmt(spentTotal)}</span>
            <span className="small muted">gastado</span>
            {isCurrent && liquid > 0 && <span className="tiny muted">de {money.fmt(liquid)}</span>}
            {isCurrent && liquid > 0 && <span className="muted" style={{ fontSize: 11, lineHeight: 1.2 }}>disponibles en el banco</span>}
          </div>
        </Donut>
      </div>
      <div className="col" style={{ gap: 0, marginTop: 8 }}>
        {tags.map(([t, v]) => {
          const count = spendingList.filter((e) => spendTag(e) === t).length
          return (
            <button key={t} className="bd-tag-row" onClick={() => onTag(t)}>
              <IconTile icon={TAG_ICON[t]} color={TAG_COLOR[t]} size={40} />
              <div className="grow">
                <div className="ellipsis" style={{ fontWeight: 500 }}>{SPEND_TAG_INFO[t].label}</div>
                <div className="small muted ellipsis">{count} {count === 1 ? 'pago' : 'pagos'}{SPEND_TAG_INFO[t].need ? ' · necesario' : ' · prescindible'}</div>
              </div>
              <div className="right" style={{ flexShrink: 0 }}>
                <div className="num" style={{ fontWeight: 500 }}>{money.fmt(v)}</div>
                <div className="small num" style={{ color: TAG_COLOR[t] }}>{pct1(v / total * 100)}</div>
              </div>
              <Icon name="chevronRight" style={{ color: 'var(--muted)', flexShrink: 0 }} />
            </button>
          )
        })}
      </div>
      <div className="small muted" style={{ marginTop: 8 }}>
        Necesario {money.fmt(needs)} ({pct1(needs / total * 100)}) · prescindible {money.fmt(spentTotal - needs)} ({pct1((spentTotal - needs) / total * 100)})
      </div>
    </div>
  )
}

// ---------- Cuentas con amigos ----------

/** "Se ha pagado" (settlePerson del ViewModel): todo lo pendiente con esa persona queda saldado, también en Tricount. */
function settlePerson(person: string) {
  const s = getState().settings
  const lower = person.toLowerCase()
  const debts = Debts.parse(s.debtsRaw).map((d) => (d.person.toLowerCase() === lower ? { ...d, settled: true } : d))
  const tri = Object.fromEntries(Object.entries(Debts.parseTricount(s.tricountRaw)).filter(([k]) => k.toLowerCase() !== lower))
  db.setSettings({ debtsRaw: Debts.format(debts), tricountRaw: Debts.formatTricount(tri) })
}
/** "Pagado" en un apunte suelto (settleDebt). */
function settleDebt(id: number) {
  const s = getState().settings
  db.setSettings({ debtsRaw: Debts.format(Debts.parse(s.debtsRaw).map((d) => (d.id === id ? { ...d, settled: true } : d))) })
}

/** Quién te debe y a quién debes, con el saldo de Tricount si lo usáis. */
export function FriendsCard({ onEdit }: { onEdit: Edit }) {
  const s = useSettings()
  const money = useMoney()
  const friends: FriendBalance[] = useMemo(() => Debts.balances(Debts.parse(s.debtsRaw), Debts.parseTricount(s.tricountRaw)), [s.debtsRaw, s.tricountRaw])
  const [open, setOpen] = useState<string | null>(null)
  const head = (
    <div className="row">
      <h2 className="bd-h2 grow">Cuentas con amigos</h2>
      <button className="bd-add" onClick={() => onEdit({ name: 'debt' })}>+ Añadir</button>
    </div>
  )
  if (friends.length === 0) {
    return (
      <div className="bd-card">
        {head}
        <div className="small muted" style={{ marginTop: 8 }}>
          ¿Pagaste el pádel y la mitad era de Juanpa? Ábrelo y toca «Alguien me debe parte», o añade aquí lo que debes o te deben. Si usáis Tricount, puedes poner su saldo.
        </div>
      </div>
    )
  }
  const owed = friends.filter((f) => f.net > 0).reduce((a, f) => a + f.net, 0)
  const owe = -friends.filter((f) => f.net < 0).reduce((a, f) => a + f.net, 0)
  return (
    <div className="bd-card">
      {head}
      <div className="row" style={{ padding: '8px 0', alignItems: 'flex-start' }}>
        <div className="grow">
          <div className="small muted">Te deben</div>
          <div className="num bd-friends-num" style={{ color: 'var(--gain)' }}>{money.fmt(owed)}</div>
        </div>
        <div className="grow right">
          <div className="small muted">Debes</div>
          <div className="num bd-friends-num" style={{ color: owe > 0 ? 'var(--loss)' : 'var(--ink)' }}>{money.fmt(owe)}</div>
        </div>
      </div>
      {friends.map((f) => {
        const expanded = open === f.person
        const t = f.tricount
        return (
          <div key={f.person} className="bd-friend">
            <div className="row">
              <button className="row grow" style={{ textAlign: 'left', minWidth: 0 }} aria-expanded={expanded} onClick={() => setOpen(expanded ? null : f.person)}>
                <div className="ico round" style={{ width: 40, height: 40, background: `color-mix(in srgb, ${colorFor(f.person)} 25%, transparent)`, color: 'var(--ink)', fontSize: 16 }}>
                  {f.person.slice(0, 1).toLocaleUpperCase('es')}
                </div>
                <div className="grow">
                  <div className="ellipsis" style={{ fontWeight: 500 }}>{f.person}</div>
                  <div className="small" style={{ color: f.net > 0.005 ? 'var(--gain)' : f.net < -0.005 ? 'var(--loss)' : 'var(--muted)' }}>
                    {f.net > 0.005 ? `Te debe ${money.fmt(f.net)}` : f.net < -0.005 ? `Le debes ${money.fmt(-f.net)}` : 'En paz'}{'  '}{expanded ? '▴' : '▾'}
                  </div>
                </div>
              </button>
              <button className="bd-chip-btn" onClick={() => settlePerson(f.person)}>Se ha pagado</button>
            </div>
            <button className="bd-tricount" onClick={() => onEdit({ name: 'tricount', person: f.person })}>
              {t ? `Tricount: ${t.amount >= 0 ? `te debe ${money.fmt(t.amount)}` : `le debes ${money.fmt(-t.amount)}`} (al ${dayShortDot(t.epochDay)}) · cambiar` : '¿Lo lleváis en Tricount? Pon su saldo'}
            </button>
            {expanded && f.open.map((d) => {
              const inside = d.inTricount && t != null && d.epochDay <= t.epochDay
              return (
                <div key={d.id} className="row" style={{ gap: 8, marginTop: 8 }}>
                  <button className="row grow" style={{ textAlign: 'left', minWidth: 0, gap: 8 }} onClick={() => onEdit({ name: 'debt', debtId: d.id })}>
                    <div className="grow">
                      <div className="small ellipsis">{d.note.trim() || 'Sin concepto'}</div>
                      <div className="tiny muted">{dayShortDot(d.epochDay)}{d.inTricount ? (inside ? ' · ya incluido en Tricount' : ' · en Tricount') : ''}</div>
                    </div>
                    <span className="small num" style={{ fontWeight: 500, color: inside ? 'var(--muted)' : d.amount >= 0 ? 'var(--gain)' : 'var(--loss)' }}>{money.signed(d.amount)}</span>
                  </button>
                  <button className="bd-paid" onClick={() => settleDebt(d.id)}>Pagado</button>
                </div>
              )
            })}
          </div>
        )
      })}
      <div className="tiny muted" style={{ marginTop: 4 }}>«Se ha pagado» deja a esa persona en paz (también el saldo de Tricount).</div>
    </div>
  )
}

// ---------- Ingresos ----------

export function newIncomeSource(): Partial<IncomeSource> {
  return { name: '', variable: false, amount: 0, dayFrom: 1, dayTo: 5, accountId: null }
}

/** Cobro nuevo de `source` en `month`, con lo esperado como importe de partida. */
function newIncomeFor(source: IncomeSource, expected: number, month: YearMonth) {
  const isNow = sameMonth(month, thisMonth())
  const day = isNow ? new Date().getDate() : Math.min(source.dayFrom, monthDays(month))
  return { sourceId: source.id, amount: expected, note: source.name, epochDay: toEpochDay({ year: month.year, month: month.month, day }), accountId: source.accountId }
}

export function IncomeSourceRow({ sm, month, accounts, onEdit }: { sm: SourceMonth; month: YearMonth; accounts: Account[]; onEdit: Edit }) {
  const money = useMoney()
  const s = sm.source
  const days = s.dayFrom === s.dayTo ? `día ${s.dayFrom}` : `días ${s.dayFrom}–${s.dayTo}`
  const account = accounts.find((a) => a.id === s.accountId)?.name
  const kind = s.variable
    ? (sm.confirmed != null ? 'Variable · confirmado este mes' : sm.average != null ? 'Variable · media de 3 meses' : 'Variable · estimado')
    : 'Fijo'
  return (
    <div className="bd-row">
      <button className="row grow" style={{ textAlign: 'left', minWidth: 0 }} onClick={() => onEdit({ name: 'incomeSource', source: s })}>
        <IconTile icon={s.variable ? 'bolt' : 'work'} color={s.variable ? SERIES_COLORS[2] : SERIES_COLORS[0]} size={48} />
        <div className="grow">
          <div className="t ellipsis">{s.name}</div>
          <div className="s">{[kind, days, account].filter(Boolean).join(' · ')}</div>
        </div>
      </button>
      <div className="col" style={{ gap: 4, alignItems: 'flex-end', flexShrink: 0 }}>
        <div className="num" style={{ fontWeight: 700 }}>{(sm.isEstimate ? '~' : '') + money.fmt(sm.amount)}</div>
        {sm.isReceived
          ? <span className="bd-ok"><Icon name="checkCircle" size={14} /> Cobrado</span>
          : (
            <>
              <button className="bd-mini" onClick={() => onEdit({ name: 'income', income: newIncomeFor(s, sm.amount, month) })}>Cobrado</button>
              {s.variable && (
                <button className="bd-link-sm" onClick={() => onEdit({ name: 'expectedIncome', sourceId: s.id, month })}>
                  {sm.confirmed != null ? 'Cambiar importe' : '¿Cuánto este mes?'}
                </button>
              )}
            </>
          )}
      </div>
    </div>
  )
}

// ---------- Gastos fijos ----------

export function newBill(categories: Category[]): Partial<RecurringBill> {
  return { name: '', amount: 0, day: 1, yearly: false, month: 1, categoryId: categories.find((c) => !c.isSavings)?.id ?? 0, accountId: null }
}

/** Lista de gastos fijos con su estado de este mes y un botón para apuntar el pago. */
export function BillsList({ bills, expenses, categories, accounts, onEdit }: { bills: RecurringBill[]; expenses: Expense[]; categories: Category[]; accounts: Account[]; onEdit: Edit }) {
  const money = useMoney()
  const month = thisMonth()
  return (
    <div className="bd-list">
      {bills.map((b) => {
        const due = Bills.dueDate(b, month)
        const paid = due != null && Bills.isPaid(b, month, expenses)
        const sub = [b.yearly ? `Cada año · ${MONTHS[b.month - 1]} ${b.day}` : `Día ${b.day} de cada mes`, accounts.find((a) => a.id === b.accountId)?.name].filter(Boolean).join(' · ')
        return (
          <div key={b.id} className="bd-row">
            <button className="row grow" style={{ textAlign: 'left', minWidth: 0 }} onClick={() => onEdit({ name: 'bill', bill: b })}>
              <IconTile icon={categoryIcon(categories.find((c) => c.id === b.categoryId))} color={categoryColor(categories, b.categoryId)} size={48} />
              <div className="grow">
                <div className="t ellipsis">{b.name}</div>
                <div className="s">{sub}</div>
              </div>
            </button>
            <div className="col" style={{ gap: 4, alignItems: 'flex-end', flexShrink: 0 }}>
              <div className="num" style={{ fontWeight: 700 }}>-{money.fmt(b.amount)}</div>
              {due == null
                ? <span className="tiny muted">No toca este mes</span>
                : paid
                  ? <span className="bd-ok"><Icon name="checkCircle" size={14} /> Pagado</span>
                  : <button className="bd-mini" onClick={() => db.payBill(b)}>Pagar</button>}
            </div>
          </div>
        )
      })}
    </div>
  )
}

// ---------- Reparto ----------

export function CategoryRow({ c, index, income, used, expanded, onToggle, onPercent, onLock }: {
  c: Category; index: number; income: number; used: number; expanded: boolean
  onToggle: () => void; onPercent: (v: number) => void; onLock: () => void
}) {
  const money = useMoney()
  const color = SERIES_COLORS[index % SERIES_COLORS.length]
  const budget = income * c.percent / 100
  return (
    <div className="bd-row col" style={{ alignItems: 'stretch', gap: 8 }}>
      <div className="row">
        <button className="row grow" style={{ textAlign: 'left', minWidth: 0 }} onClick={onToggle} aria-expanded={expanded}>
          <IconTile icon={categoryIcon(c)} color={color} size={48} />
          <div className="grow">
            <div className="t ellipsis">{c.name}</div>
            <div className="s ellipsis">{income > 0 ? `${money.fmt(used)} de ${money.fmt(budget)}` : money.fmt(used)}</div>
          </div>
          <div className="right" style={{ flexShrink: 0 }}>
            <div className="num" style={{ fontWeight: 500 }}>{pct1(c.percent)}</div>
            {budget > 0 && <div className="small" style={{ color: used > budget && !c.isSavings ? 'var(--loss)' : 'var(--muted)' }}>usado {pct1(used / budget * 100)}</div>}
          </div>
        </button>
        <button className={`bd-lock ${c.locked ? 'on' : ''}`} onClick={onLock} aria-label={c.locked ? `Desbloquear ${c.name}` : `Bloquear ${c.name}`} aria-pressed={c.locked}>
          <Icon name={c.locked ? 'lock' : 'lockOpen'} size={16} />
        </button>
      </div>
      <ProgressLine fraction={budget > 0 ? used / budget : 0} color={budget > 0 && used > budget ? 'var(--loss)' : color} />
      {expanded && (
        <input type="range" min={0} max={100} step={1} value={c.percent} disabled={c.locked} aria-label={`Porcentaje de ${c.name}`}
          style={{ accentColor: color }} onChange={(e) => onPercent(Math.round(Number(e.target.value)))} />
      )}
    </div>
  )
}

// ---------- Pagos ----------

export function ExpenseRow({ e, categories, accounts, onClick }: { e: Expense; categories: Category[]; accounts: Account[]; onClick: () => void }) {
  const money = useMoney()
  const cat = categories.find((c) => c.id === e.categoryId)
  const t = spendTag(e)
  const sub = [dayLabel(e.epochDay), SPEND_TAG_INFO[t].label, e.fromCard ? 'Tarjeta' : null, e.billId != null ? 'Gasto fijo' : null].filter(Boolean).join(' · ')
  return (
    <button className="bd-row" style={{ textAlign: 'left' }} onClick={onClick}>
      {cat?.isSavings ? <IconTile icon={categoryIcon(cat)} color={SERIES_COLORS[1]} size={48} /> : <IconTile icon={TAG_ICON[t]} color={TAG_COLOR[t]} size={48} />}
      <div className="grow">
        <div className="t ellipsis">{e.note.trim() || cat?.name || 'Gasto'}</div>
        <div className="s ellipsis">{sub}</div>
      </div>
      <div className="right" style={{ flexShrink: 0, maxWidth: '40%' }}>
        <div className="num" style={{ fontWeight: 500 }}>-{money.fmt(e.amount)}</div>
        {accounts.find((a) => a.id === e.accountId) && <div className="small muted ellipsis">{accounts.find((a) => a.id === e.accountId)!.name}</div>}
      </div>
    </button>
  )
}
