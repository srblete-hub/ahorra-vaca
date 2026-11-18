// Port de AccountScreen.kt: una cuenta por dentro (saldo, intereses, acciones rápidas y movimientos).
import { useEffect, useMemo } from 'react'
import { ACCOUNT_TYPES, type Expense, type Income, type Transfer } from '../../../data/models'
import { dayLabel, ofEpochDay, today as todayDate } from '../../../data/dates'
import { useDB } from '../../../data/store'
import { InterestEarned } from '../../../domain'
import { AccountLogo, AnimatedAmount, EmptyState, IconTile, ListRow, Overlay, SERIES_COLORS, useMoney } from '../../components'
import { Icon } from '../../icons'
import { useNav, type OverlayRoute } from '../../nav'
import { ExpenseRow, IncomeRow, SectionTitle, pct1 } from './common'

type Move = { day: number; created: number } & ({ kind: 'e'; x: Expense } | { kind: 'i'; x: Income } | { kind: 't'; x: Transfer })

export function AccountScreen({ route }: { route: Extract<OverlayRoute, { name: 'account' }> }) {
  const nav = useNav()
  const accountId = route.accountId
  const accounts = useDB((s) => s.accounts)
  const expenses = useDB((s) => s.expenses)
  const incomes = useDB((s) => s.incomes)
  const transfers = useDB((s) => s.transfers)
  const categories = useDB((s) => s.categories)
  const m = useMoney()
  const account = accounts.find((a) => a.id === accountId)

  // Si la cuenta se borra, se vuelve atrás.
  useEffect(() => { if (!account) nav.back() }, [account]) // eslint-disable-line react-hooks/exhaustive-deps

  const moves = useMemo<Move[]>(() => [
    ...expenses.filter((e) => e.accountId === accountId).map((x): Move => ({ day: x.epochDay, created: x.createdAt, kind: 'e', x })),
    ...incomes.filter((i) => i.accountId === accountId).map((x): Move => ({ day: x.epochDay, created: x.createdAt, kind: 'i', x })),
    ...transfers.filter((t) => t.fromAccountId === accountId || t.toAccountId === accountId).map((x): Move => ({ day: x.epochDay, created: x.createdAt, kind: 't', x })),
  ].sort((a, b) => b.day - a.day || b.created - a.created), [expenses, incomes, transfers, accountId])

  const interest = useMemo(() => {
    if (!account || account.interestRate <= 0) return null
    const days = [...incomes.map((i) => i.epochDay), ...expenses.map((e) => e.epochDay), ...transfers.map((t) => t.epochDay)]
    const since = days.length ? ofEpochDay(Math.min(...days)) : todayDate()
    return InterestEarned.of(account, incomes, transfers, todayDate(), since)
  }, [account, incomes, expenses, transfers])

  if (!account) return null

  return (
    <Overlay title="" onClose={nav.back}>
      <div className="w-acc-head">
        <AccountLogo name={account.name} size={72} />
        <div className="w-acc-name">{account.name}</div>
        <div className="muted">{ACCOUNT_TYPES[account.type].label}</div>
        <div style={{ marginTop: 8 }}><AnimatedAmount value={account.balance} size={40} /></div>
        {interest && (
          <div className="gain" style={{ fontWeight: 600, fontSize: 14, marginTop: 4 }}>
            {pct1(account.interestRate)} TAE · {interest.estimated ? '≈ ' : ''}+{m.fmt(interest.earned)} ganados (interés compuesto) · ahora +{m.fmt(interest.monthlyNow)}/mes
          </div>
        )}
      </div>

      <div className="w-acc-actions">
        <ActionButton icon="swap" label="Transferir" onClick={() => nav.edit({ name: 'transfer', fromAccountId: account.id })} />
        <ActionButton icon="tune" label="Ajustar saldo" onClick={() => nav.edit({ name: 'balance', accountId: account.id })} />
        <ActionButton icon="edit" label="Editar" onClick={() => nav.edit({ name: 'account', account })} />
      </div>

      <SectionTitle>Movimientos</SectionTitle>
      {moves.length === 0
        ? <EmptyState icon="receipt" title="Sin movimientos" body="Los gastos, cobros y transferencias de esta cuenta aparecerán aquí." />
        : (
          <div className="card tight list">
            {moves.map((mv) => {
              if (mv.kind === 'e') return <ExpenseRow key={'e' + mv.x.id} e={mv.x} categories={categories} accounts={accounts} onClick={() => nav.edit({ name: 'expense', expense: mv.x })} />
              if (mv.kind === 'i') return <IncomeRow key={'i' + mv.x.id} i={mv.x} accounts={accounts} onClick={() => nav.edit({ name: 'income', income: mv.x })} />
              const x = mv.x
              const incoming = x.toAccountId === accountId
              const other = accounts.find((a) => a.id === (incoming ? x.fromAccountId : x.toAccountId))?.name ?? 'otra cuenta'
              return (
                <ListRow
                  key={'t' + x.id}
                  title={incoming ? `Desde ${other}` : `A ${other}`}
                  subtitle={[dayLabel(x.epochDay), 'Transferencia', x.note.trim() || null].filter(Boolean).join(' · ')}
                  leading={<IconTile icon="swap" color={SERIES_COLORS[1]} />}
                  trailing={(incoming ? '+' : '-') + m.fmt(x.amount)}
                  trailingColor={incoming ? 'var(--gain)' : undefined}
                />
              )
            })}
          </div>
        )}
    </Overlay>
  )
}

function ActionButton({ icon, label, onClick }: { icon: string; label: string; onClick: () => void }) {
  return (
    <button className="w-acc-action" onClick={onClick}>
      <span className="w-acc-action-ico"><Icon name={icon} size={24} /></span>
      <span>{label}</span>
    </button>
  )
}
