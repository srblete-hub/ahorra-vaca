// "En el banco" (la tarjeta tinta de arriba) y la tarjeta de primeros pasos (HomeScreen.kt + NowWidgets.kt).
import type { Account, NetWorthSnapshot } from '../../../data/models'
import { today as todayDate, todayEpoch, toEpochDay } from '../../../data/dates'
import type { Wealth } from '../../../domain'
import { isDebt, isSaving } from '../../../domain'
import { AnimatedAmount, SERIES_COLORS, SpottedCard, StackedBar, useMoney } from '../../components'
import { Icon } from '../../icons'
import { useNav } from '../../nav'
import { AVAILABLE_COLOR, INVESTED_COLOR, MintCard, Overline, SAVINGS_COLOR, Tile } from './shared'

/** Patrimonio de hoy comparado con la foto más antigua de este mes (o la última del mes pasado). */
export function changeThisMonth(snapshots: NetWorthSnapshot[], total: number): number | null {
  const t = todayDate()
  const firstOfMonth = toEpochDay({ ...t, day: 1 })
  const today = todayEpoch()
  const before = snapshots.filter((s) => s.epochDay < firstOfMonth)
  const base = before[before.length - 1] ?? snapshots.find((s) => s.epochDay >= firstOfMonth && s.epochDay < today)
  return base ? total - base.total : null
}

export function BankHero({ wealth, accounts, snapshots, todayLeft }: { wealth: Wealth; accounts: Account[]; snapshots: NetWorthSnapshot[]; todayLeft: number | null }) {
  const nav = useNav()
  const m = useMoney()
  const bank = accounts.filter((a) => !isSaving(a) && !isDebt(a))
  const change = changeThisMonth(snapshots, wealth.total)
  return (
    <SpottedCard>
      <div className="muted" style={{ fontSize: 18 }}>En el banco</div>
      <div style={{ height: 8 }} />
      <AnimatedAmount value={wealth.available} size={heroSize(m.fmt(wealth.available))} />
      {todayLeft != null && (
        <div className={`hm-today-pill ${todayLeft >= 0 ? '' : 'over'}`}>
          {todayLeft >= 0 ? `Hoy te quedan ${m.fmt(todayLeft)}` : `Hoy te has pasado ${m.fmt(-todayLeft)}`}
        </div>
      )}
      <div style={{ height: 16 }} />
      <StackedBar slices={bank.map((a, i) => ({ value: a.balance, color: SERIES_COLORS[i % SERIES_COLORS.length], label: a.name }))} height={12} />
      <div style={{ height: 16 }} />
      <AddOnRow label="+ Ahorrado" value={wealth.savings} color={SAVINGS_COLOR} onClick={() => nav.setTab('WEALTH')} />
      <AddOnRow label="+ Invertido" value={wealth.invested} color={INVESTED_COLOR} onClick={() => nav.setTab('INVEST')} />
      {wealth.debts > 0 && <AddOnRow label="− Deudas" value={-wealth.debts} color="var(--loss)" onClick={() => nav.setTab('WEALTH')} />}
      <div className="divider" />
      <div className="row" style={{ alignItems: 'flex-start' }}>
        <div className="grow muted" style={{ fontSize: 16 }}>Patrimonio total</div>
        <div className="right">
          <div className="num" style={{ fontSize: 16, fontWeight: 600 }}>{m.fmt(wealth.total)}</div>
          {change != null && Math.abs(change) >= 0.01 && (
            <div className={`num ${change >= 0 ? 'gain' : 'loss'}`} style={{ fontSize: 14 }}>
              {(change >= 0 ? '↑ ' : '↓ ') + m.signed(change)} este mes
            </div>
          )}
        </div>
      </div>
    </SpottedCard>
  )
}

/** Tamaño del importe grande para que quepa a 375 px ("123.456,78 €" no cabe a 48). */
function heroSize(text: string) { return text.length > 13 ? 32 : text.length > 11 ? 40 : 48 }

/** "+ Ahorrado 9.140 €": lo que se suma al dinero del banco. */
function AddOnRow({ label, value, color, onClick }: { label: string; value: number; color: string; onClick: () => void }) {
  const m = useMoney()
  return (
    <button className="hm-addon" onClick={onClick}>
      <span className="hm-dot" style={{ background: color }} />
      <span className="grow" style={{ textAlign: 'left' }}>{label}</span>
      <span className="num" style={{ fontWeight: 500 }}>{m.fmt(Math.abs(value))}</span>
      <Icon name="chevronRight" size={16} style={{ opacity: 0.6 }} />
    </button>
  )
}

/** Cuando aún no hay nada: pide el saldo de hoy de cada sitio, con atajos claros. */
export function SetupCard() {
  const nav = useNav()
  return (
    <MintCard>
      <Overline>Empieza aquí</Overline>
      <div style={{ height: 8 }} />
      <div className="title">¿Cuánto tienes ahora mismo?</div>
      <div className="muted" style={{ marginTop: 4 }}>
        Pon el saldo que tiene hoy cada sitio donde guardas dinero. Lo que arrastras de meses anteriores ya va incluido ahí.
      </div>
      <div className="col" style={{ marginTop: 16 }}>
        <SetupButton title="Cuenta del banco" sub="Nómina, cuenta corriente…" icon="bank" color={AVAILABLE_COLOR}
          onClick={() => nav.edit({ name: 'account', account: { name: '', type: 'BANK', balance: 0 } })} />
        <SetupButton title="Ahorro" sub="Cuenta de ahorro, hucha, fondo, con o sin intereses" icon="savings" color={SAVINGS_COLOR}
          onClick={() => nav.edit({ name: 'account', account: { name: '', type: 'BANK_SAVINGS', balance: 0 } })} />
        <SetupButton title="Inversión" sub="Cripto, acciones o ETF" icon="trendingUp" color={INVESTED_COLOR} onClick={() => nav.edit({ name: 'chooser' })} />
      </div>
      <button className="link-btn" style={{ alignSelf: 'center', marginTop: 8 }} onClick={() => nav.open({ name: 'onboarding' })}>Hacerlo paso a paso</button>
    </MintCard>
  )
}

function SetupButton({ title, sub, icon, color, onClick }: { title: string; sub: string; icon: string; color: string; onClick: () => void }) {
  return (
    <button className="hm-setup-btn" onClick={onClick}>
      <Tile icon={icon} color={color} />
      <div className="grow" style={{ textAlign: 'left' }}>
        <div style={{ fontWeight: 700 }}>{title}</div>
        <div className="small muted">{sub}</div>
      </div>
      <span className="gain" style={{ fontSize: 24, fontWeight: 500 }}>+</span>
    </button>
  )
}
