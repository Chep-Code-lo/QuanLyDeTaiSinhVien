import type { ReactNode } from 'react'
import { SlidersHorizontal } from 'lucide-react'

export function SelectionPanel({ id, label, description, children }: {
  id: string
  label: string
  description?: string
  children: ReactNode
}) {
  return <section className="page-card selection-panel">
    <div className="selection-panel__copy">
      <span className="selection-panel__icon" aria-hidden="true"><SlidersHorizontal size={18} /></span>
      <div>
        <label htmlFor={id}>{label}</label>
        {description && <p>{description}</p>}
      </div>
    </div>
    <div className="selection-panel__control">{children}</div>
  </section>
}
