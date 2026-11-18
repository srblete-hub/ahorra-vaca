// Marcadores temporales mientras se portan las pantallas.
import { Overlay, Sheet } from './components'
import { useNav } from './nav'

export function Stub(name: string) {
  return function StubScreen() {
    const nav = useNav()
    const inOverlay = nav.overlays.length > 0
    const body = <div className="card"><div className="title">{name}</div><div className="muted">En preparación.</div></div>
    return inOverlay ? <Overlay title={name} onClose={nav.back}>{body}</Overlay> : <div className="screen">{body}</div>
  }
}
export function StubSheet(name: string) {
  return function StubSheetC({ onClose }: { onClose: () => void }) {
    return <Sheet title={name} onClose={onClose}><div className="muted">En preparación.</div></Sheet>
  }
}
