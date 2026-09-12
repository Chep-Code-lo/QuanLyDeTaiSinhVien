type PageHeaderProps = {
  description: string
  eyebrow?: string
  title: string
}

export function PageHeader({ description, eyebrow = 'Quản lý đề tài', title }: PageHeaderProps) {
  return (
    <header className="page-header">
      <div>
        <span className="page-header__eyebrow">{eyebrow}</span>
        <h1>{title}</h1>
        <p>{description}</p>
      </div>
    </header>
  )
}
