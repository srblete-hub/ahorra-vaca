// Estructura principal (equivale a MainActivity + MainScreen.kt).
import { Suspense, useEffect, useState } from 'react'
import { startupTasks, useSettings } from './data/store'
import { NavProvider, useNav, type Tab } from './ui/nav'
import { OVERLAYS, SHEETS, TABS } from './ui/registry'
import { Toasts } from './ui/components'
import { Icon } from './ui/icons'
import { Unlock, Welcome } from './ui/auth'
import { PlanningEffects } from './ui/screens/profile'

/** Si la app pasa más de este tiempo en segundo plano, pide el PIN otra vez. */
const RELOCK_MS = 60_000

export default function App() {
  const s = useSettings()
  useTheme(s.theme)
  const [unlocked, setUnlocked] = useState(false)
  const [welcomeDone, setWelcomeDone] = useState(false)
  const hasAccount = !!s.userName || !!s.pinHash

  useEffect(() => {
    let hiddenAt = 0
    const h = () => {
      if (document.visibilityState === 'hidden') hiddenAt = Date.now()
      else if (hiddenAt && Date.now() - hiddenAt > RELOCK_MS) setUnlocked(false)
    }
    document.addEventListener('visibilitychange', h)
    return () => document.removeEventListener('visibilitychange', h)
  }, [])

  if (!hasAccount && !welcomeDone) return <Welcome onDone={() => { setWelcomeDone(true); setUnlocked(true) }} />
  if (s.pinHash && !unlocked) return <Unlock onUnlock={() => setUnlocked(true)} />
  return (
    <NavProvider>
      <Main />
    </NavProvider>
  )
}

function useTheme(theme: 'system' | 'light' | 'dark') {
  useEffect(() => {
    const root = document.documentElement
    if (theme === 'system') root.removeAttribute('data-theme')
    else root.setAttribute('data-theme', theme)
    const mq = window.matchMedia('(prefers-color-scheme: dark)')
    const apply = () => {
      const dark = theme === 'dark' || (theme === 'system' && mq.matches)
      document.querySelectorAll('meta[name="theme-color"]').forEach((m) => m.setAttribute('content', dark ? '#12110D' : '#E8EEDF'))
    }
    apply()
    mq.addEventListener('change', apply)
    return () => mq.removeEventListener('change', apply)
  }, [theme])
}

function Main() {
  const nav = useNav()
  const s = useSettings()
  const TabScreen = TABS[nav.tab]

  useEffect(() => {
    startupTasks()
    // Precios: si los servicios están listos, refresca los que tengan más de 10 minutos.
    const mods = import.meta.glob('./data/services/actions.ts')
    const refresh = () => Object.values(mods)[0]?.().then((m: any) => m.refreshIfStale?.()).catch(() => {})
    refresh()
    const onVis = () => { if (document.visibilityState === 'visible') refresh() }
    document.addEventListener('visibilitychange', onVis)
    return () => document.removeEventListener('visibilitychange', onVis)
  }, [])

  // El asistente inicial se abre solo la primera vez.
  useEffect(() => {
    if (!s.onboardingDone && !nav.overlays.some((o) => o.name === 'onboarding')) nav.open({ name: 'onboarding' })
  }, [s.onboardingDone]) // eslint-disable-line react-hooks/exhaustive-deps

  // Tras el asistente, si aún no dijo cuán ahorrador quiere ser, se le pregunta (como en Android).
  useEffect(() => {
    if (s.onboardingDone && s.savingStyle < 0 && !nav.sheet && nav.overlays.length === 0) nav.edit({ name: 'profileForm' })
  }, [s.onboardingDone, s.savingStyle]) // eslint-disable-line react-hooks/exhaustive-deps

  const SheetC = nav.sheet ? SHEETS[nav.sheet.name] : null
  return (
    <div className="app">
      <Suspense fallback={<Loading />}>
        <TabScreen />
      </Suspense>
      <TabBar />
      {nav.overlays.map((r, i) => {
        const C = OVERLAYS[r.name]
        return <Suspense key={i + r.name} fallback={null}><C route={r} /></Suspense>
      })}
      {SheetC && nav.sheet && (
        <Suspense fallback={null}><SheetC key={JSON.stringify(nav.sheet)} route={nav.sheet} onClose={nav.closeSheet} /></Suspense>
      )}
      <PlanningEffects />
      <Toasts />
    </div>
  )
}

function Loading() {
  return <div className="screen"><div className="card" style={{ height: 240, opacity: 0.5 }} /></div>
}

const TAB_ITEMS: { tab: Tab; label: string; icon: string }[] = [
  { tab: 'HOME', label: 'Inicio', icon: 'home' },
  { tab: 'WEALTH', label: 'Patrimonio', icon: 'donut' },
  { tab: 'BUDGET', label: 'Movimientos', icon: 'pie' },
  { tab: 'INVEST', label: 'Inversión', icon: 'trendingUp' },
  { tab: 'PROFILE', label: 'Perfil', icon: 'person' },
]

function TabBar() {
  const nav = useNav()
  const s = useSettings()
  return (
    <nav className="tabbar" aria-label="Secciones">
      {TAB_ITEMS.map((t) => (
        <button key={t.tab} className={nav.tab === t.tab ? 'on' : ''} aria-current={nav.tab === t.tab ? 'page' : undefined} onClick={() => nav.setTab(t.tab)}>
          {t.tab === 'PROFILE'
            ? <span className="avatar-mini">{s.avatar ? <img src={s.avatar} alt="" /> : (s.userName.trim()[0] ?? '?').toLocaleUpperCase('es')}</span>
            : <Icon name={t.icon} />}
          <span className="ellipsis" style={{ maxWidth: '100%' }}>{t.label}</span>
        </button>
      ))}
    </nav>
  )
}
