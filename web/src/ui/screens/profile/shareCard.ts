// Dibuja la ficha del mes (MonthShareCard) en un canvas para compartirla como PNG.
import type { MonthWrapped } from '../../../domain'
import { MONTHS, MONTHS_SHORT, lengthOfMonth, today } from '../../../data/dates'

export function shortDay(day: number, month: number) { return `${day} ${MONTHS_SHORT[month - 1]}` }

export interface ShareTexts {
  /** Importe grande ya formateado (saldo del banco o balance del mes). */
  amount: string
  income: string
  spent: string
}

/** Genera el PNG de la ficha. Tamaño lógico 360×(alto) dibujado a 3×. */
export async function renderShareCard(w: MonthWrapped, t: ShareTexts): Promise<Blob> {
  const font = getComputedStyle(document.body).fontFamily || 'system-ui, sans-serif'
  try { await (document as Document & { fonts?: FontFaceSet }).fonts?.ready } catch { /* sin fuentes cargadas */ }
  const W = 360, H = 400, S = 3, pad = 24
  const canvas = document.createElement('canvas')
  canvas.width = W * S
  canvas.height = H * S
  const ctx = canvas.getContext('2d')!
  ctx.scale(S, S)

  // Fondo con esquinas redondeadas y degradado.
  const g = ctx.createLinearGradient(0, 0, W, H)
  g.addColorStop(0, '#0E2A1B')
  g.addColorStop(1, '#1E4D33')
  ctx.fillStyle = g
  roundRect(ctx, 0, 0, W, H, 32)
  ctx.fill()

  const faded = 'rgba(255,255,255,0.62)'
  const len = lengthOfMonth(w.month.year, w.month.month)
  const shown = w.isCurrent ? w.daysElapsed : len
  ctx.textBaseline = 'alphabetic'

  // Cabecera
  ctx.fillStyle = faded
  ctx.font = `700 15px ${font}`
  ctx.fillText(`‹  ${MONTHS[w.month.month - 1].toUpperCase()} ⌄  ›`, pad, pad + 16)
  ctx.font = `600 15px ${font}`
  ctx.textAlign = 'right'
  ctx.fillText(w.isCurrent ? 'Hoy' : 'Último día', W - pad, pad + 16)
  ctx.textAlign = 'left'

  ctx.font = `400 16px ${font}`
  ctx.fillText(w.isCurrent ? 'En el banco' : 'Balance del mes', pad, pad + 56)
  ctx.fillStyle = '#fff'
  let size = 44
  ctx.font = `800 ${size}px ${font}`
  while (ctx.measureText(t.amount).width > W - 2 * pad && size > 20) { size -= 2; ctx.font = `800 ${size}px ${font}` }
  ctx.fillText(t.amount, pad, pad + 108)

  // Barras por día
  const top = pad + 132, barsH = 66, gap = 2
  const bw = (W - 2 * pad - gap * (len - 1)) / len
  const max = Math.max(1, ...w.daily.slice(0, shown))
  for (let i = 0; i < len; i++) {
    const v = w.daily[i] ?? 0
    const last = i === shown - 1
    const h = i < shown ? 6 + 54 * (v / max) : 4
    const x = pad + i * (bw + gap)
    ctx.fillStyle = last ? '#fff' : i < shown ? 'rgba(255,255,255,0.42)' : 'rgba(255,255,255,0.12)'
    roundRect(ctx, x, top + barsH - h, bw, h, Math.min(3, bw / 2))
    ctx.fill()
    if (last) { ctx.beginPath(); ctx.arc(x + bw / 2, top + barsH - h - 6, 2, 0, Math.PI * 2); ctx.fillStyle = '#fff'; ctx.fill() }
  }

  // Fechas
  const yDates = top + barsH + 24
  ctx.font = `400 14px ${font}`
  ctx.fillStyle = faded
  ctx.fillText(shortDay(1, w.month.month), pad, yDates)
  ctx.textAlign = 'right'
  const end = shortDay(len, w.month.month)
  ctx.fillText(end, W - pad, yDates)
  if (w.isCurrent) {
    ctx.fillStyle = '#fff'
    ctx.font = `700 14px ${font}`
    const endW = ctx.measureText(end).width
    ctx.fillText(`Hoy · ${today().day}`, W - pad - endW - 12, yDates)
  }

  // Ingresos y gastos
  const yMoney = yDates + 40
  ctx.textAlign = 'left'
  ctx.font = `400 14px ${font}`
  ctx.fillStyle = faded
  ctx.fillText('Ingresos ', pad, yMoney)
  let x = pad + ctx.measureText('Ingresos ').width
  ctx.font = `700 16px ${font}`
  ctx.fillStyle = '#4ADE80'
  ctx.fillText(`+${t.income}`, x, yMoney)
  ctx.textAlign = 'right'
  ctx.fillStyle = '#fff'
  const spent = `−${t.spent}`
  ctx.fillText(spent, W - pad, yMoney)
  x = W - pad - ctx.measureText(spent).width
  ctx.font = `400 14px ${font}`
  ctx.fillStyle = faded
  ctx.fillText('Gastos ', x, yMoney)

  ctx.textAlign = 'left'
  ctx.font = `400 12px ${font}`
  ctx.fillStyle = 'rgba(255,255,255,0.4)'
  ctx.fillText('Ahorra, vaca 🐄', pad, H - pad)

  return new Promise((ok, ko) => canvas.toBlob((b) => (b ? ok(b) : ko(new Error('No se pudo crear la imagen'))), 'image/png'))
}

function roundRect(ctx: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, r: number) {
  const rr = Math.max(0, Math.min(r, w / 2, h / 2))
  ctx.beginPath()
  ctx.moveTo(x + rr, y)
  ctx.arcTo(x + w, y, x + w, y + h, rr)
  ctx.arcTo(x + w, y + h, x, y + h, rr)
  ctx.arcTo(x, y + h, x, y, rr)
  ctx.arcTo(x, y, x + w, y, rr)
  ctx.closePath()
}
