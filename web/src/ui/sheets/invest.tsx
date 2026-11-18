// Cuentas e inversiones: cuenta (con calculadora de intereses), buscar cripto, cripto y acción (Sheets.kt).
import { useEffect, useRef, useState } from 'react'
import { AmountField, BigAmountInput, Initials, SERIES_COLORS, TextField, parseAmount, useMoney } from '../components'
import { Icon } from '../icons'
import { useNav, type SheetRoute } from '../nav'
import { db, getState, useSettings } from '../../data/store'
import { ACCOUNT_TYPE_ORDER, ACCOUNT_TYPES, type Account, type CryptoHolding, type StockHolding } from '../../data/models'
import { InterestMath } from '../../domain'
import { cryptoPrices, searchCoins, searchStocks, stockQuote, type CoinResult, type StockResult } from '../../data/services/prices'
import { refreshCrypto, refreshStocks } from '../../data/services/actions'
import {
  Choice, Chips, FieldLabel, FormSheet, Spinner, SwitchBox, accountIcon, editable, quantity, signedPct1, symbolColor,
  useConfirmDelete, useCurrencySymbol,
} from './form'

type R<N extends SheetRoute['name']> = Extract<SheetRoute, { name: N }>
interface P<N extends SheetRoute['name']> { route: R<N>; onClose: () => void }

// ---------- Cuenta ----------

export function AccountSheet({ route, onClose }: P<'account'>) {
  const sym = useCurrencySymbol()
  const confirm = useConfirmDelete()
  const [initial] = useState<Account>(() => {
    const r = route.account ?? {}
    const stored = r.id ? getState().accounts.find((a) => a.id === r.id) : undefined
    return { id: 0, name: '', type: 'BANK', balance: 0, note: '', interestRate: 0, autoInterest: false, interestFromMonth: 0, ...(stored ?? r) }
  })
  const isNew = !initial.id
  const [name, setName] = useState(initial.name)
  const [type, setType] = useState(initial.type)
  const [balance, setBalance] = useState(editable(initial.balance))
  const [note, setNote] = useState(initial.note)
  const [paysInterest, setPaysInterest] = useState(initial.interestRate > 0)
  const [rate, setRate] = useState(editable(initial.interestRate))
  const [autoInterest, setAutoInterest] = useState(initial.autoInterest || isNew)
  const value = balance.trim() === '' ? 0 : parseAmount(balance)
  const rateValue = parseAmount(rate)

  return (
    <FormSheet
      title={isNew ? 'Nueva cuenta' : 'Editar cuenta'}
      onClose={onClose}
      onPrimary={() => {
        if (value == null) return
        const r = paysInterest ? rateValue ?? 0 : 0
        db.saveAccount({
          ...initial, id: initial.id || undefined, name: name.trim(), type, balance: value, note: note.trim(),
          interestRate: r, autoInterest: r > 0 && autoInterest,
          // Si se apaga el abono automático, al volver a encenderlo empieza de cero.
          interestFromMonth: r > 0 && autoInterest ? initial.interestFromMonth : 0,
        })
        onClose()
      }}
      primaryEnabled={name.trim() !== '' && value != null && (!paysInterest || (rateValue != null && rateValue > 0))}
      onDelete={isNew ? null : () => confirm.ask(`"${initial.name}"`, () => { db.deleteAccount(initial.id); onClose() })}
    >
      <BigAmountInput value={balance} onChange={setBalance} symbol={sym} autoFocus={isNew} />
      <TextField label="Nombre (BBVA nómina, Hucha, MyInvestor…)" value={name} onChange={setName} />
      <FieldLabel>¿Dónde está?</FieldLabel>
      <Chips>
        {ACCOUNT_TYPE_ORDER.map((t) => <Choice key={t} text={ACCOUNT_TYPES[t].label} selected={t === type} onClick={() => setType(t)} icon={accountIcon(t)} />)}
      </Chips>
      <SwitchBox title="Da intereses" sub="Cuenta remunerada, depósito, fondo monetario…" checked={paysInterest} onChange={setPaysInterest} />
      {paysInterest && (
        <>
          <AmountField label="Rentabilidad anual (TAE)" value={rate} onChange={setRate} suffix="%" />
          <SwitchBox title="Sumar los intereses cada mes" sub="Se añaden solos al saldo al empezar cada mes" checked={autoInterest} onChange={setAutoInterest} />
          {value != null && rateValue != null && rateValue > 0 && <InterestCalculator balance={value} ratePct={rateValue} />}
        </>
      )}
      <TextField label="Nota (opcional)" value={note} onChange={setNote} />
      {confirm.dialog}
    </FormSheet>
  )
}

/** Cuánto da una cuenta remunerada: quieta, metiendo algo fijo al mes y con una parte variable. */
function InterestCalculator({ balance, ratePct }: { balance: number; ratePct: number }) {
  const money = useMoney()
  const [fixed, setFixed] = useState('')
  const [varMin, setVarMin] = useState('')
  const [varMax, setVarMax] = useState('')
  const [years, setYears] = useState(5)
  const f = parseAmount(fixed) ?? 0
  const vMin = parseAmount(varMin) ?? 0
  const vMax = Math.max(parseAmount(varMax) ?? vMin, vMin)
  const months = years * 12
  const low = InterestMath.project(balance, ratePct, f + vMin, months)
  const high = InterestMath.project(balance, ratePct, f + vMax, months)
  const ranged = vMax > vMin
  const yl = (y: number) => `${y} ${y === 1 ? 'año' : 'años'}`
  const range = (a: number, b: number) => money.fmt(a) + (ranged ? '–' + money.fmt(b) : '')
  return (
    <div className="sh-box big sh-mint col" style={{ gap: 8 }}>
      <div className="sh-label" style={{ fontSize: 12, letterSpacing: '0.06em', textTransform: 'uppercase' }}>Si lo dejas como está</div>
      <div className="sh-two">
        <div>
          <div className="num sh-gain" style={{ fontSize: 22, fontWeight: 700 }}>{money.fmt(InterestMath.monthlyInterest(balance, ratePct))}</div>
          <div className="tiny muted">al mes</div>
        </div>
        <div>
          <div className="num sh-gain" style={{ fontSize: 22, fontWeight: 700 }}>{money.fmt(balance * ratePct / 100)}</div>
          <div className="tiny muted">al año</div>
        </div>
      </div>
      <div className="sh-label" style={{ fontSize: 12, letterSpacing: '0.06em', textTransform: 'uppercase', marginTop: 8 }}>Si vas metiendo dinero</div>
      <AmountField label="Cantidad fija al mes" value={fixed} onChange={setFixed} suffix="€" />
      <div className="sh-text small">Y si además metes algo variable cada mes (opcional):</div>
      <div className="sh-two">
        <AmountField label="Mínimo" value={varMin} onChange={setVarMin} suffix="€" />
        <AmountField label="Máximo" value={varMax} onChange={setVarMax} suffix="€" />
      </div>
      <Chips>
        {[1, 3, 5, 10, 20].map((y) => <Choice key={y} text={yl(y)} selected={y === years} onClick={() => setYears(y)} />)}
      </Chips>
      <div className="num" style={{ fontSize: 24, fontWeight: 800, overflowWrap: 'anywhere' }}>
        {ranged ? `${money.fmt(low.finalBalance)} – ${money.fmt(high.finalBalance)}` : money.fmt(low.finalBalance)}
      </div>
      <div className="sh-text small">
        Tendrías esto en {yl(years)}: {range(low.contributed + balance, high.contributed + balance)} puesto por ti y {range(low.interest, high.interest)} de intereses.
      </div>
      <div className="sh-text small sh-gain" style={{ fontWeight: 700 }}>
        Entonces te daría {range(InterestMath.monthlyInterestAtEnd(low, ratePct), InterestMath.monthlyInterestAtEnd(high, ratePct))} al mes.
      </div>
    </div>
  )
}

// ---------- Cripto ----------

export function CoinSearchSheet({ onClose }: P<'coinSearch'>) {
  const nav = useNav()
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<CoinResult[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const seq = useRef(0)
  const run = async () => {
    const q = query.trim()
    if (!q) return
    const my = ++seq.current
    setLoading(true); setError(null)
    try {
      const r = await searchCoins(q)
      if (my !== seq.current) return
      setResults(r)
      if (r.length === 0) setError('No se ha encontrado nada.')
    } catch {
      if (my !== seq.current) return
      setError('Sin conexión con CoinGecko.')
    }
    setLoading(false)
  }
  return (
    <FormSheet title="Añadir cripto" onClose={onClose} primary={null} onSubmit={() => void run()}>
      <div className="sh-search">
        <Icon name="search" style={{ color: 'var(--muted)', flexShrink: 0 }} />
        <input
          type="search" enterKeyHint="search" inputMode="search" autoFocus autoComplete="off" autoCorrect="off" spellCheck={false}
          aria-label="Buscar cripto" placeholder="Bitcoin, ETH, Solana…" value={query} onChange={(e) => setQuery(e.target.value)}
        />
        {loading ? <span style={{ padding: 12 }}><Spinner /></span> : (
          <button type="submit" className="icon-btn" aria-label="Buscar" disabled={!query.trim()}><Icon name="trendingUp" /></button>
        )}
      </div>
      {error && <div className="sh-text small sh-loss">{error}</div>}
      <div className="col" style={{ gap: 0 }}>
        {results.map((c, i) => (
          <button key={c.id} type="button" className="sh-item" onClick={() => nav.edit({ name: 'crypto', holding: { coinId: c.id, symbol: c.symbol, name: c.name, amount: 0, avgBuyPrice: 0 } })}>
            <Initials text={c.symbol} color={SERIES_COLORS[(i + 2) % SERIES_COLORS.length]} size={40} />
            <div className="grow" style={{ minWidth: 0 }}>
              <div className="t ellipsis">{c.name}</div>
              <div className="s">{c.symbol}</div>
            </div>
          </button>
        ))}
      </div>
    </FormSheet>
  )
}

/** Cripto por dinero: pones cuánto tienes en euros y se calcula la cantidad con el precio de ahora. */
export function CryptoSheet({ route, onClose }: P<'crypto'>) {
  const money = useMoney()
  const settings = useSettings()
  const sym = useCurrencySymbol()
  const confirm = useConfirmDelete()
  const [initial] = useState<CryptoHolding>(() => {
    const r = route.holding
    const stored = r.id ? getState().crypto.find((h) => h.id === r.id) : undefined
    return { id: 0, coinId: '', symbol: '', name: '', amount: 0, avgBuyPrice: 0, price: 0, updatedAt: 0, ...(stored ?? r) }
  })
  const isNew = !initial.id
  const [price, setPrice] = useState<number | null>(initial.price > 0 ? initial.price : null)
  const [loading, setLoading] = useState(initial.price <= 0)
  useEffect(() => {
    let alive = true
    cryptoPrices([initial.coinId], settings.currency || 'EUR')
      .then((m) => { if (alive && m[initial.coinId] != null) setPrice(m[initial.coinId]) })
      .catch(() => { /* sin conexión: se pone la cantidad a mano */ })
      .finally(() => { if (alive) setLoading(false) })
    return () => { alive = false }
  }, [initial.coinId, settings.currency])
  const [byQuantity, setByQuantity] = useState(false)
  const [value, setValue] = useState(editable(initial.amount * initial.price))
  const [invested, setInvested] = useState(editable(initial.amount * initial.avgBuyPrice))
  const [qtyText, setQtyText] = useState(editable(initial.amount))
  const pr = price
  const v = parseAmount(value)
  const inv = invested.trim() === '' ? 0 : parseAmount(invested)
  const qty = byQuantity || pr == null ? parseAmount(qtyText) : v != null ? v / pr : null
  const avg = qty != null && qty > 0 && inv != null && inv > 0 ? inv / qty : 0

  return (
    <FormSheet
      title={`${initial.name} · ${initial.symbol}`}
      onClose={onClose}
      onPrimary={() => {
        if (qty == null) return
        db.saveCrypto({ ...initial, id: initial.id || undefined, amount: qty, avgBuyPrice: avg, price: pr ?? initial.price, updatedAt: pr != null ? Date.now() : initial.updatedAt })
        void refreshCrypto(true)
        onClose()
      }}
      primaryEnabled={qty != null && qty > 0 && inv != null}
      onDelete={isNew ? null : () => confirm.ask(initial.name, () => { db.deleteCrypto(initial.id); onClose() })}
    >
      <div className="sh-box sh-row">
        <Initials text={initial.symbol} color={symbolColor(initial.symbol)} size={40} />
        <div className="grow sh-strong" style={{ fontWeight: 600 }}>
          {pr != null ? `1 ${initial.symbol} = ${money.fmt(pr)} ahora` : loading ? 'Buscando el precio…' : 'Sin conexión: pon la cantidad a mano'}
        </div>
        {loading && <Spinner small />}
      </div>
      {pr != null && !byQuantity ? (
        <>
          <FieldLabel>¿Cuánto dinero tienes en {initial.name}?</FieldLabel>
          <BigAmountInput value={value} onChange={setValue} symbol={sym} autoFocus={isNew} />
          {qty != null && qty > 0 && <div className="sh-strong sh-gain" style={{ fontSize: 15 }}>Son {quantity(qty)} {initial.symbol}</div>}
        </>
      ) : (
        <AmountField label="Cantidad que tienes" value={qtyText} onChange={setQtyText} suffix={initial.symbol} />
      )}
      <AmountField label="¿Cuánto metiste en total? (opcional)" value={invested} onChange={setInvested} suffix={sym} />
      {qty != null && qty > 0 && inv != null && inv > 0 && pr != null ? (() => {
        const pnl = qty * pr - inv
        return (
          <div className={`sh-strong ${pnl > 0 ? 'sh-gain' : pnl < 0 ? 'sh-loss' : ''}`} style={{ fontSize: 15 }}>
            {pnl >= 0 ? 'Ganas ' : 'Pierdes '}{money.fmt(Math.abs(pnl))} ({signedPct1(pnl / inv * 100)})
          </div>
        )
      })() : <div className="sh-text small">Si pones lo que metiste, verás cuánto ganas o pierdes.</div>}
      {pr != null && (
        <button type="button" className="sh-link" onClick={() => {
          const next = !byQuantity
          setByQuantity(next)
          if (next && qty != null) setQtyText(editable(qty))
          if (!next) { const q = parseAmount(qtyText); if (q != null) setValue(editable(Math.round(q * pr * 100) / 100)) }
        }}>
          {byQuantity ? 'Prefiero poner el dinero que tengo' : `Prefiero poner la cantidad exacta de ${initial.symbol}`}
        </button>
      )}
      {confirm.dialog}
    </FormSheet>
  )
}

// ---------- Acción ----------

export function StockSheet({ route, onClose }: P<'stock'>) {
  const money = useMoney()
  const settings = useSettings()
  const currency = settings.currency || 'EUR'
  const confirm = useConfirmDelete()
  const [initial] = useState<StockHolding>(() => {
    const r = route.holding ?? {}
    const stored = r.id ? getState().stocks.find((h) => h.id === r.id) : undefined
    return { id: 0, ticker: '', name: '', shares: 0, avgBuyPrice: 0, price: 0, updatedAt: 0, ...(stored ?? r) }
  })
  const isNew = !initial.id
  const [ticker, setTicker] = useState(initial.ticker)
  const [name, setName] = useState(initial.name)
  const [shares, setShares] = useState(editable(initial.shares))
  const [avg, setAvg] = useState(editable(initial.avgBuyPrice))
  const [price, setPrice] = useState(editable(initial.price))
  const s = parseAmount(shares)
  const a = avg.trim() === '' ? 0 : parseAmount(avg)
  const manual = parseAmount(price)

  // Sugerencias de Yahoo al escribir el ticker o el nombre (pueden fallar por el proxy: se escribe a mano).
  const [hits, setHits] = useState<StockResult[]>([])
  const [searching, setSearching] = useState(false)
  const [typed, setTyped] = useState(false)
  useEffect(() => {
    const q = ticker.trim()
    if (!typed || q.length < 2) { setHits([]); return }
    let alive = true
    const t = window.setTimeout(() => {
      setSearching(true)
      searchStocks(q).then((r) => { if (alive) setHits(r.slice(0, 6)) }).catch(() => { if (alive) setHits([]) }).finally(() => { if (alive) setSearching(false) })
    }, 450)
    return () => { alive = false; window.clearTimeout(t) }
  }, [ticker, typed])

  // Precio actual del ticker elegido, para orientar (no se guarda si no lo escribes tú).
  const [quote, setQuote] = useState<{ ticker: string; price: number | null; failed: boolean } | null>(null)
  const lookup = (tk: string) => {
    setQuote({ ticker: tk, price: null, failed: false })
    stockQuote(tk, currency).then((q) => setQuote((cur) => (cur?.ticker === tk ? { ticker: tk, price: q.price, failed: false } : cur)))
      .catch(() => setQuote((cur) => (cur?.ticker === tk ? { ticker: tk, price: null, failed: true } : cur)))
  }

  return (
    <FormSheet
      title={isNew ? 'Nueva acción' : initial.ticker}
      onClose={onClose}
      onPrimary={() => {
        if (s == null || a == null) return
        const priceChangedByHand = manual != null && manual !== initial.price
        const updated: StockHolding = {
          ...initial, ticker: ticker.trim().toUpperCase(), name: name.trim(), shares: s, avgBuyPrice: a,
          price: manual ?? initial.price,
          // updatedAt = 0 marca un precio puesto a mano: la recarga automática no lo pisa.
          updatedAt: priceChangedByHand ? 0 : initial.updatedAt,
        }
        db.saveStock({ ...updated, id: updated.id || undefined })
        if (!priceChangedByHand && (isNew || updated.ticker !== initial.ticker)) void refreshStocks(true)
        onClose()
      }}
      primaryEnabled={ticker.trim() !== '' && s != null && s > 0 && a != null && a >= 0}
      onDelete={isNew ? null : () => confirm.ask(initial.ticker, () => { db.deleteStock(initial.id); onClose() })}
    >
      <TextField label="Ticker (AAPL, SAN.MC, VWCE.DE…)" value={ticker} autoFocus={isNew} onChange={(t) => { setTicker(t); setTyped(true); setQuote(null) }} />
      {typed && (searching || hits.length > 0) && (
        <div className="col" style={{ gap: 0 }}>
          {searching && hits.length === 0 && <div className="sh-row sh-text small"><Spinner small /> Buscando…</div>}
          {hits.map((h) => (
            <button key={h.ticker} type="button" className="sh-item" onClick={() => {
              setTicker(h.ticker); if (!name.trim()) setName(h.name); setTyped(false); setHits([]); lookup(h.ticker)
            }}>
              <Initials text={h.ticker.split('.')[0]} color={symbolColor(h.ticker)} size={40} />
              <div className="grow" style={{ minWidth: 0 }}>
                <div className="t ellipsis">{h.name}</div>
                <div className="s ellipsis">{h.ticker}{h.exchange ? ` · ${h.exchange}` : ''}</div>
              </div>
            </button>
          ))}
        </div>
      )}
      {quote && (
        <div className="sh-text small">
          {quote.price != null ? `Ahora cotiza a ${money.fmt(quote.price)}.` : quote.failed ? 'No he podido traer el precio ahora: puedes ponerlo a mano.' : 'Buscando el precio…'}
        </div>
      )}
      <TextField label="Nombre (opcional)" value={name} onChange={setName} />
      <AmountField label="Número de acciones" value={shares} onChange={setShares} suffix="" />
      <AmountField label="Precio medio de compra" value={avg} onChange={setAvg} suffix={currency} />
      <AmountField label="Precio actual (vacío = automático)" value={price} onChange={setPrice} suffix={currency} />
      {confirm.dialog}
    </FormSheet>
  )
}
