export function PageControls({
  page,
  totalPages,
  loading,
  onPage,
  label,
}: {
  page: number
  totalPages: number
  loading: boolean
  onPage: (page: number) => void
  label: string
}) {
  return (
    <nav className="table-pagination" aria-label={label}>
      <span>
        Trang {page + 1}/{Math.max(1, totalPages)}
      </span>
      <div>
        <button
          type="button"
          className="btn btn-secondary"
          disabled={loading || page === 0}
          onClick={() => onPage(page - 1)}
        >
          Trang trước
        </button>
        <button
          type="button"
          className="btn btn-secondary"
          disabled={loading || page + 1 >= totalPages}
          onClick={() => onPage(page + 1)}
        >
          Trang sau
        </button>
      </div>
    </nav>
  )
}
