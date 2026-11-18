// Cabecera común de las pestañas.
import type { ReactNode } from 'react'
import { db, useSettings } from '../data/store'
import { useNav } from './nav'
import { Icon } from './icons'

/** Botones de la cabecera de cada pestaña: ocultar importes, añadir, noticias y ajustes. */
export function HeaderActions() {
  const nav = useNav()
  const s = useSettings()
  return (
    <div className="head-actions">
      <button className="icon-btn" aria-label={s.hideAmounts ? 'Mostrar importes' : 'Ocultar importes'} onClick={() => db.setSettings({ hideAmounts: !s.hideAmounts })}>
        <Icon name={s.hideAmounts ? 'eyeOff' : 'eye'} />
      </button>
      <button className="icon-btn" aria-label="Añadir" onClick={() => nav.edit({ name: 'chooser' })}><Icon name="add" /></button>
      <button className="icon-btn" aria-label="Noticias" onClick={() => nav.open({ name: 'news' })}><Icon name="news" /></button>
      <button className="icon-btn" aria-label="Ajustes" onClick={() => nav.open({ name: 'settings' })}><Icon name="settings" /></button>
    </div>
  )
}

/** Título grande de pestaña con los botones de la cabecera a la derecha. */
export function ScreenHeader({ title, subtitle, children }: { title: ReactNode; subtitle?: ReactNode; children?: ReactNode }) {
  return (
    <div className="col" style={{ gap: 8 }}>
      <div className="row" style={{ justifyContent: 'flex-end' }}><HeaderActions /></div>
      <div className="screen-head" style={{ paddingTop: 16 }}>
        <div className="grow">
          <h1 className="big-title">{title}</h1>
          {subtitle && <div className="subtitle" style={{ marginTop: 4 }}>{subtitle}</div>}
        </div>
        {children}
      </div>
    </div>
  )
}
