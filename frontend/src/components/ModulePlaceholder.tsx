type ModulePlaceholderProps = {
  actionLabel?: string
  description: string
  items: readonly string[]
  title: string
}

export function ModulePlaceholder({
  actionLabel,
  description,
  items,
  title,
}: ModulePlaceholderProps) {
  return (
    <section className="placeholder-panel">
      <div className="placeholder-panel__heading">
        <div>
          <span className="placeholder-panel__badge">Khung giao diện</span>
          <h2>{title}</h2>
          <p className="placeholder-panel__description">{description}</p>
        </div>
        {actionLabel ? (
          <button className="button--disabled" type="button" disabled>
            {actionLabel}
          </button>
        ) : null}
      </div>
      <ul className="placeholder-panel__list">
        {items.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>
    </section>
  )
}
