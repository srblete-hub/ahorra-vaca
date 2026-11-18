// Port de ui/screens/ProfileScreen.kt: foto, tick azul, tarjeta de miembro, racha, actividad, Wrapped, logros y amigos.
import { useMemo, useRef, useState } from 'react'
import { useSettings } from '../../../data/store'
import { MONTHS, thisMonth, lengthOfMonth, ymOfEpoch } from '../../../data/dates'
import { Debts, Streaks, usePlanning } from '../../../domain'
import type { Achievement, DayMark, SpendStreak } from '../../../domain'
import { Avatar, Card, IconTile, SERIES_COLORS, SectionLabel, useMoney } from '../../components'
import { Icon } from '../../icons'
import { ScreenHeader } from '../../header'
import { useNav } from '../../nav'
import { AchievementBadge, AchievementDialog, EditProfileSheet, saveAvatar } from './common'
import './profile.css'

export function ProfileScreen() {
  const p = usePlanning()
  const s = useSettings()
  const nav = useNav()
  const m = useMoney()
  const [editing, setEditing] = useState(false)
  const [selected, setSelected] = useState<Achievement | null>(null)
  const file = useRef<HTMLInputElement>(null)
  const level = p.level

  const unlocked = useMemo(() => p.achievements.filter((a) => a.unlocked).sort((a, b) => (s.achievements[b.id] ?? 0) - (s.achievements[a.id] ?? 0)), [p.achievements, s.achievements])
  const locked = useMemo(() => p.achievements.filter((a) => !a.unlocked).sort((a, b) => b.progress - a.progress), [p.achievements])

  const streak = useMemo(() => {
    const tm = thisMonth()
    const len = lengthOfMonth(tm.year, tm.month)
    const allowance = p.budget && p.budget.base > 0 ? p.budget.base / len : p.safe.perDay
    return Streaks.of(p.data, allowance)
  }, [p.data, p.budget, p.safe.perDay])
  const friends = useMemo(() => Debts.people(Debts.parse(s.debtsRaw), Debts.parseTricount(s.tricountRaw)).length, [s.debtsRaw, s.tricountRaw])
  const months = useMemo(() => new Set(p.data.expenses.map((e) => { const y = ymOfEpoch(e.epochDay); return y.year * 12 + y.month })).size, [p.data.expenses])
  const pickPhoto = () => file.current?.click()
  const mName = MONTHS[thisMonth().month - 1]

  return (
    <div className="screen">
      <ScreenHeader title="Perfil" />

      {/* ---------- Cabecera ---------- */}
      <div className="pf-head">
        <div className="pf-avatar-wrap">
          <button className="pf-avatar-btn" onClick={pickPhoto} aria-label="Cambiar foto"><Avatar size={112} ring={SERIES_COLORS[3]} /></button>
          <button className="pf-camera" onClick={pickPhoto} aria-label="Cambiar foto"><Icon name="camera" size={16} /></button>
          <input ref={file} type="file" accept="image/*" hidden onChange={(e) => { saveAvatar(e.target.files?.[0]); e.target.value = '' }} />
        </div>
        <div className="pf-name-row">
          <span className="pf-name">{s.userName.trim() || 'Tu nombre'}</span>
          <Icon name="verified" size={32} className="pf-verified" />
          <button className="pf-edit" onClick={() => setEditing(true)} aria-label="Editar perfil"><Icon name="edit" size={16} /></button>
        </div>
        <button className={`pf-handle ${s.handle ? '' : 'empty'}`} onClick={() => setEditing(true)}>{s.handle ? `@${s.handle}` : 'Elige tu @'}</button>
      </div>

      {/* ---------- Tarjeta de miembro ---------- */}
      <MemberCard level={level.number} name={level.name} missing={level.to != null ? level.to - p.points : null} since={p.data.firstMonth} />

      {/* ---------- Racha ---------- */}
      <StreakCard s={streak} fmt={m.fmt} />

      {/* ---------- Tu actividad ---------- */}
      <div>
        <div className="pf-small-label">TU ACTIVIDAD</div>
        <div className="pf-tiles">
          <ActivityTile icon="groups" value={friends} label="Amigos" />
          <ActivityTile icon="trophy" value={unlocked.length} label="Logros" />
          <ActivityTile icon="autoAwesome" value={months} label="Wrappeds" onClick={() => nav.open({ name: 'wrapped' })} />
        </div>
      </div>

      {/* ---------- Wrapped del mes ---------- */}
      <div className="pf-hero pf-wrapped" role="button" tabIndex={0} onClick={() => nav.open({ name: 'wrapped' })}
        onKeyDown={(e) => { if (e.key === 'Enter') nav.open({ name: 'wrapped' }) }}>
        <div className="pf-overline" style={{ color: 'rgba(255,255,255,0.8)' }}>TU WRAPPED</div>
        <div style={{ fontSize: 28, fontWeight: 800, letterSpacing: '-0.02em', lineHeight: 1.15, marginTop: 4 }}>Tu {mName}, en tarjetas</div>
        <div style={{ fontSize: 14, color: 'rgba(255,255,255,0.9)', marginTop: 4 }}>Tus números del mes, al día, y una ficha para compartir →</div>
        <div className="pf-wrapped-chips" aria-hidden="true">
          {['#1DB954', '#5E5CE6', '#FF6B3D', '#E91E63', '#FFB300', '#00BCD4'].map((c) => <span key={c} style={{ background: c }} />)}
        </div>
      </div>

      {/* ---------- Estadísticas ---------- */}
      <Card className="tight">
        <div className="pf-stats">
          <div><div className="v num">{m.fmt(p.wealth.total)}</div><div className="l">Patrimonio</div></div>
          <div><div className="v num">{m.fmt(p.wealth.savings + p.wealth.invested)}</div><div className="l">Ahorrado</div></div>
          <div><div className="v num">{unlocked.length}/{p.achievements.length}</div><div className="l">Logros</div></div>
        </div>
      </Card>

      {/* ---------- Logros ---------- */}
      <SectionLabel>Logros</SectionLabel>
      <Card className="tight">
        <div className="pf-badges">
          {[...unlocked, ...locked].map((a) => (
            <button key={a.id} className="pf-badge" onClick={() => setSelected(a)}>
              <AchievementBadge a={a} />
              <span className="t" style={{ color: a.unlocked ? 'var(--ink)' : 'var(--muted)' }}>{a.title}</span>
              {!a.unlocked && <span className="tiny muted">{Math.floor(a.progress * 100)} %</span>}
            </button>
          ))}
        </div>
      </Card>

      {/* ---------- Amigos (Supabase sin configurar: "Muy pronto", como en Android) ---------- */}
      <SectionLabel>Amigos</SectionLabel>
      <Card>
        <div className="row">
          <IconTile icon="groups" color={SERIES_COLORS[1]} size={48} round />
          <div className="grow">
            <div style={{ fontWeight: 700, fontSize: 17 }}>Muy pronto</div>
            <div className="small muted">Busca a tus amigos por su @, mándales solicitud y celebra sus logros. Tus importes nunca se ven.</div>
          </div>
        </div>
      </Card>

      {editing && <EditProfileSheet onClose={() => setEditing(false)} />}
      {selected && <AchievementDialog a={selected} onClose={() => setSelected(null)} />}
    </div>
  )
}

function ActivityTile({ icon, value, label, onClick }: { icon: string; value: number; label: string; onClick?: () => void }) {
  const body = (
    <>
      <div className="pf-tile-top">
        <span className="pf-tile-ico"><Icon name={icon} size={20} /></span>
        <span className="pf-tile-num num">{value}</span>
      </div>
      <div className="pf-tile-label">{label}</div>
    </>
  )
  return onClick ? <button className="pf-tile" onClick={onClick}>{body}</button> : <div className="pf-tile">{body}</div>
}

/** Tarjeta de miembro: nivel, desde cuándo ahorras y cuánto falta para el siguiente. */
function MemberCard({ level, name, missing, since }: { level: number; name: string; missing: number | null; since: { year: number; month: number } | null }) {
  return (
    <div className="pf-hero pf-member">
      <span className="pf-bubble" style={{ width: 260, height: 260, right: -40, top: '50%', transform: 'translateY(-50%)' }} />
      <div className="pf-member-cow" aria-hidden="true"><img src="icon.svg" alt="" /></div>
      <div className="pf-member-top">
        <div className="pf-member-logo"><img src="icon.svg" alt="" /></div>
        <div className="grow">
          <div style={{ fontSize: 22, fontWeight: 700, lineHeight: 1.15 }}>Ahorra, vaca</div>
          <div style={{ fontSize: 12, fontWeight: 600, letterSpacing: '0.25em', color: 'rgba(255,255,255,0.75)' }}>MIEMBRO</div>
        </div>
        <span className="pf-active"><i />Activo</span>
      </div>
      <div className="pf-level">
        <Icon name="premium" size={32} style={{ color: '#B7E4C7', flexShrink: 0 }} />
        <div style={{ minWidth: 0 }}>
          <div className="pf-level-num">NIVEL {level}</div>
          <div style={{ fontSize: 16, fontWeight: 600, color: 'rgba(255,255,255,0.9)' }}>{name}</div>
          {since && <div style={{ fontSize: 14, color: 'rgba(255,255,255,0.8)' }}>Ahorrando desde {String(since.month).padStart(2, '0')}/{since.year}</div>}
        </div>
      </div>
      <div style={{ marginTop: 16, fontSize: 12, letterSpacing: '0.25em', color: 'rgba(255,255,255,0.7)' }}>SIGUIENTE NIVEL</div>
      <div style={{ fontSize: 24, fontWeight: 700 }}>{missing != null ? `Faltan ${missing} pts` : 'Nivel máximo'}</div>
    </div>
  )
}

const DAY_NAMES = ['L', 'M', 'X', 'J', 'V', 'S', 'D']

/** Racha: días seguidos gastando lo que te toca o menos (sin contar alquiler ni fijos). */
function StreakCard({ s, fmt }: { s: SpendStreak; fmt: (v: number) => string }) {
  const dayBg = (mk: DayMark) => (mk === 'FUTURE' ? 'transparent' : mk === 'OVER' ? 'rgba(255,255,255,0.25)' : '#fff')
  const left = s.nextGoal != null ? s.nextGoal - s.current : 0
  return (
    <div className="pf-hero pf-streak">
      <span className="pf-bubble" style={{ width: 220, height: 220, right: -40, top: -40 }} />
      <div className="row" style={{ alignItems: 'flex-start' }}>
        <div className="grow">
          <div className="pf-overline" style={{ color: 'rgba(255,255,255,0.85)' }}>RACHA ACTUAL</div>
          <div className="row" style={{ alignItems: 'flex-end', gap: 4, marginTop: 8 }}>
            <Icon name="fire" size={40} style={{ marginBottom: 8, flexShrink: 0 }} />
            <span className="pf-streak-num num">{s.current}</span>
            <span style={{ fontSize: 24, fontWeight: 600, marginBottom: 6 }}>{s.current === 1 ? 'día' : 'días'}</span>
          </div>
        </div>
        <div className="col" style={{ alignItems: 'flex-end', flexShrink: 0 }}>
          <span className="pf-chip">Récord {s.record}</span>
          <span className="pf-chip"><Icon name="eco" size={16} /> ×{s.noSpendThisMonth}</span>
        </div>
      </div>
      <div className="pf-week">
        {s.week.map(([, mk], i) => (
          <div key={i} className="pf-day">
            <span style={{ background: dayBg(mk), boxShadow: mk === 'FUTURE' ? 'inset 0 0 0 2px rgba(255,255,255,0.45)' : undefined }}>
              {mk === 'OK' && <Icon name="check" size={24} style={{ color: '#E0703A' }} />}
              {mk === 'NO_SPEND' && <Icon name="eco" size={24} style={{ color: '#E0703A' }} />}
              {mk === 'OVER' && <Icon name="close" size={16} />}
            </span>
            {DAY_NAMES[i]}
          </div>
        ))}
      </div>
      <div style={{ marginTop: 16, fontSize: 16, fontWeight: 600 }}>
        {s.today === 'NO_SPEND' ? 'Hoy: día sin gastos (por ahora)'
          : s.today === 'OK' ? `Hoy: ${fmt(s.spentToday)} de ${fmt(s.allowance)}, dentro de lo tuyo`
            : 'Hoy te has pasado: la racha vuelve a empezar mañana'}
      </div>
      {s.nextGoal != null && (
        <div style={{ marginTop: 8, fontSize: 14, color: 'rgba(255,255,255,0.85)' }}>
          {left} {left === 1 ? 'día' : 'días'} para tu racha de {s.nextGoal === 30 ? '1 mes' : `${s.nextGoal} días`}.
        </div>
      )}
      <div style={{ marginTop: 4, fontSize: 12, color: 'rgba(255,255,255,0.7)' }}>Cuenta los días que gastas lo que te toca o menos; el alquiler y los fijos no cuentan.</div>
    </div>
  )
}
