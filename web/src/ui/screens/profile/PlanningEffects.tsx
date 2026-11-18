// Efectos del init de FinanceViewModel.kt: guarda límites y círculos de hoy, apunta los logros nuevos y los celebra.
import { useEffect, useState } from 'react'
import { db, getState } from '../../../data/store'
import { planningUpdates, usePlanning, type Achievement } from '../../../domain'
import { CelebrationDialog } from './common'

/** Se monta una vez en App. No pinta nada salvo el diálogo de "¡Nuevo logro!" cuando toca. */
export function PlanningEffects() {
  const p = usePlanning()
  const [celebrate, setCelebrate] = useState<Achievement[]>([])
  useEffect(() => {
    const { patch, fresh } = planningUpdates(p, getState().settings)
    if (Object.keys(patch).length > 0) db.setSettings(patch)
    if (fresh.length > 0) setCelebrate((c) => [...c, ...fresh.filter((a) => !c.some((x) => x.id === a.id))])
  }, [p])
  if (celebrate.length === 0) return null
  return <CelebrationDialog list={celebrate} onClose={() => setCelebrate([])} />
}
