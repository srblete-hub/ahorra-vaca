// Port de FutureScreen.kt: saldo previsto día a día, lo que viene y los gastos fijos.
import { useState } from 'react'
import { MONTHS, thisMonth, toEpochDay, today as todayDate } from '../../../data/dates'
import type { Account, Category, Expense, RecurringBill } from '../../../data/models'
import { db, useDB } from '../../../data/store'
import { Bills, usePlanning, type FlowEvent } from '../../../domain'
import { EmptyState, IconTile, LineChart, ListRow, Overlay, Segmented, useMoney } from '../../components'
import { Icon } from '../../icons'
import { useNav } from '../../nav'
import { AddButton, MintCard, Overline, SectionTitle, categoryColor, categoryIcon, dayDeMonth, prettyDate } from './common'

/** Lista de gastos fijos con su estado de este mes y un botón para apuntar el pago. */
export function BillsList({ bills, expenses, categories, accounts, onEdit, onPay }: {
  bills: RecurringBill[]; expenses: Expense[]; categories: Category[]; accounts: Account[]; onEdit: (b: RecurringBill) => void; onPay: (b: RecurringBill) => void
}) {
  const m = useMoney()
  const month = thisMonth()
  return (
    <div className="card tight list">
      {bills.map((b) => {
        const due = Bills.dueDate(b, month)
        const paid = due != null && Bills.isPaid(b, month, expenses)
        const sub = [b.yearly ? `Cada año · ${MONTHS[b.month - 1]} ${b.day}` : `Día ${b.day} de cada mes`, accounts.find((a) => a.id === b.accountId)?.name].filter(Boolean).join(' · ')
        return (
          <ListRow
            key={b.id}
            title={b.name}
            subtitle={sub}
            onClick={() => onEdit(b)}
            leading={<IconTile icon={categoryIcon(categories.find((c) => c.id === b.categoryId))} color={categoryColor(categories, b.categoryId)} />}
            trailing={<span style={{ fontWeight: 700 }}>-{m.fmt(b.amount)}</span>}
            trailingSub={
              due == null ? <span className="tiny">No toca este mes</span>
                : paid ? <span className="tiny gain w-paid"><Icon name="checkCircle" size={12} /> Pagado</span>
                  : <button className="w-pay" onClick={(e) => { e.stopPropagation(); onPay(b) }}>Pagar</button>
            }
          />
        )
      })}
    </div>
  )
}

export function EventRow({ e }: { e: FlowEvent }) {
  const m = useMoney()
  return (
    <ListRow
      title={e.label}
      subtitle={prettyDate(e.date) + (e.overdue ? ' · debería haber llegado ya' : '')}
      leading={<IconTile icon={e.isIncome ? 'work' : 'calendar'} color={e.isIncome ? 'var(--gain)' : 'var(--loss)'} />}
      trailing={m.signed(e.amount)}
      trailingColor={e.isIncome ? 'var(--gain)' : undefined}
    />
  )
}

/** "Tu futuro": saldo previsto día a día con cobros, gastos fijos y tu gasto habitual. */
export function FutureScreen() {
  const nav = useNav()
  const p = usePlanning()
  const bills = useDB((s) => s.bills)
  const expenses = useDB((s) => s.expenses)
  const categories = useDB((s) => s.categories)
  const accounts = useDB((s) => s.accounts)
  const m = useMoney()
  const [days, setDays] = useState(30)

  const today = todayDate()
  const points = p.forecast.points.slice(0, days + 1)
  const limit = toEpochDay(today) + days
  const events = p.forecast.events.filter((e) => toEpochDay(e.date) <= limit)
  const end = points[points.length - 1]
  const lowest = points.reduce((a, x) => (x[1] < a[1] ? x : a), points[0])
  const negative = points.find((x) => x[1] < 0) ?? null

  const title = (
    <div>
      <div className="title">Tu futuro</div>
      <div className="small muted" style={{ fontWeight: 400 }}>Cuánto tendrás disponible cada día</div>
    </div>
  )
  const newBill = () => nav.edit({ name: 'bill', bill: { name: '', amount: 0, day: 1, yearly: false, month: 1, categoryId: categories.find((c) => !c.isSavings)?.id ?? 0, accountId: accounts[0]?.id ?? null } })

  return (
    <Overlay title={title} onClose={nav.back}>
      <Segmented options={[30, 60, 90]} value={days} onChange={setDays} label={(d) => `${d} días`} />

      {negative && (
        <div className="card flat" style={{ background: 'color-mix(in srgb, var(--loss) 12%, var(--card))' }}>
          <div className="row">
            <IconTile icon="warning" color="var(--loss)" />
            <div className="grow">
              <div style={{ fontSize: 18, fontWeight: 600 }}>Te quedarías corto</div>
              <div className="small">
                {prettyDate(negative[0])} tu saldo disponible bajaría a {m.fmt(negative[1])}. Tienes tiempo para mover dinero del ahorro o recortar gastos antes.
              </div>
            </div>
          </div>
        </div>
      )}

      {end && (
        <MintCard>
          <Overline>Disponible el {dayDeMonth(end[0])}</Overline>
          <div className="hero-num" style={{ fontSize: 38, marginTop: 4, color: end[1] < 0 ? 'var(--loss)' : undefined }}>{m.fmt(end[1])}</div>
          <div style={{ fontWeight: 600, fontSize: 14, marginTop: 4, color: lowest[1] < 0 ? 'var(--loss)' : 'var(--muted)' }}>
            Punto más bajo: {m.fmt(lowest[1])} · {prettyDate(lowest[0])}
          </div>
          <div style={{ marginTop: 16 }}>
            <LineChart values={points.map((x) => x[1])} color={negative ? 'var(--loss)' : 'var(--gain)'} height={128} />
          </div>
          <div className="small muted" style={{ marginTop: 8 }}>
            Cuenta tus cobros, tus gastos fijos y tu gasto habitual de {m.fmt(p.forecast.dailySpend)} al día (media de los últimos 60 días). Solo el dinero disponible: el ahorro no se toca.
          </div>
        </MintCard>
      )}

      <SectionTitle>Lo que viene</SectionTitle>
      {events.length === 0
        ? <EmptyState icon="calendar" title="Nada previsto" body="Añade tus ingresos y tus gastos fijos para ver aquí lo que va a pasar en tu cuenta." />
        : <div className="card tight list">{events.map((e, i) => <EventRow key={i} e={e} />)}</div>}

      <SectionTitle action={<AddButton onClick={newBill} />}>Gastos fijos</SectionTitle>
      {bills.length === 0
        ? <EmptyState icon="calendar" title="Sin gastos fijos" body="Alquiler, luz, suscripciones, seguros… Apúntalos una vez y Ahorra, vaca los tendrá en cuenta cada mes." />
        : (
          <div className="col">
            <BillsList bills={bills} expenses={expenses} categories={categories} accounts={accounts} onEdit={(b) => nav.edit({ name: 'bill', bill: b })} onPay={(b) => db.payBill(b)} />
            <div className="small muted" style={{ paddingLeft: 8 }}>
              Total: {m.fmt(bills.reduce((a, b) => a + Bills.monthly(b), 0))} al mes (los anuales, repartidos)
            </div>
          </div>
        )}
    </Overlay>
  )
}
