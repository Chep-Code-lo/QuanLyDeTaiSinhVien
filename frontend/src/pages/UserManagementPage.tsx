import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { isAxiosError } from 'axios'
import { PencilLine, Plus, Search, Trash2, X } from 'lucide-react'
import { adminApi } from '../api/admin'
import { ConfirmModal } from '../components/ConfirmModal'
import { CreateAccountForm } from '../components/CreateAccountForm'
import { EditAccountForm } from '../components/EditAccountForm'
import { PageHeader } from '../components/PageHeader'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import { useRequestLoading } from '../hooks/useRequestLoading'
import type { UserResponse } from '../types/domain'
import { formatDateTime } from '../utils/dateTime'
import { roleLabel } from '../utils/displayLabels'
import './UserManagementPage.css'

const PAGE_SIZE = 20

function deleteError(error: unknown) {
  const message = isAxiosError(error) ? error.response?.data?.message : undefined
  switch (message) {
    case 'You cannot delete the account currently in use.':
      return 'Bạn không thể xóa tài khoản đang đăng nhập.'
    case 'At least one active administrator account is required.':
      return 'Hệ thống phải luôn có ít nhất một quản trị viên đang hoạt động.'
    case 'User account was not found.':
      return 'Tài khoản này không còn tồn tại.'
    default:
      return message || 'Không thể xóa tài khoản. Vui lòng thử lại.'
  }
}

function rolesText(roles: UserResponse['roles']) {
  return roles.map((role) => roleLabel(role)).join(', ')
}

function accessDate(value?: string) {
  return value ? formatDateTime(value) : 'Chưa truy cập'
}

export function UserManagementPage() {
  const { user: currentUser, refreshUser } = useAuth()
  const { showError, showSuccess } = useToast()
  const [users, setUsers] = useState<UserResponse[]>([])
  const [searchInput, setSearchInput] = useState('')
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [loadError, setLoadError] = useState('')
  const [reload, setReload] = useState(0)
  const [createOpen, setCreateOpen] = useState(false)
  const [userToEdit, setUserToEdit] = useState<UserResponse | null>(null)
  const [userToDelete, setUserToDelete] = useState<UserResponse | null>(null)
  const [deleting, setDeleting] = useState(false)
  const [loading, setLoading] = useRequestLoading(JSON.stringify([page, search, reload]))

  const loadUsers = useCallback(() => {
    let active = true
    adminApi
      .listUsers({ search: search || undefined, page, size: PAGE_SIZE })
      .then((result) => {
        if (!active) return
        setUsers(result.content)
        setLoadError('')
        setTotalPages(result.totalPages)
        setTotalElements(result.totalElements)
      })
      .catch(() => {
        if (active) setLoadError('Không thể tải danh sách tài khoản. Vui lòng thử lại.')
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
  }, [page, search, setLoading])

  useEffect(() => loadUsers(), [loadUsers, reload])

  const submitSearch = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setPage(0)
    setSearch(searchInput.trim())
    setReload((value) => value + 1)
  }

  const handleCreated = () => {
    setCreateOpen(false)
    if (page !== 0) {
      setPage(0)
      return
    }
    setReload((value) => value + 1)
  }

  const handleUpdated = (updated: UserResponse) => {
    setUsers((items) => items.map((item) => (item.id === updated.id ? updated : item)))
    setUserToEdit(null)
    if (updated.id === currentUser?.id) {
      void refreshUser()
    }
  }

  const handleDelete = async () => {
    if (!userToDelete || deleting) return
    setDeleting(true)
    try {
      await adminApi.deleteUser(userToDelete.id)
      showSuccess(`Đã xóa tài khoản ${userToDelete.fullName}.`)
      setUserToDelete(null)
      if (users.length === 1 && page > 0) {
        setPage((currentPage) => currentPage - 1)
      } else {
        setReload((value) => value + 1)
      }
    } catch (error) {
      showError(deleteError(error))
    } finally {
      setDeleting(false)
    }
  }

  return (
    <div className="user-management-page">
      <PageHeader title="Quản lý tài khoản" />

      <section className="page-card">
        <div className="user-management__heading">
          <div>
            <h2 className="section-title">Danh sách tài khoản</h2>
            <p>{totalElements} tài khoản đang hoạt động</p>
          </div>
          <button type="button" className="btn btn-primary" onClick={() => setCreateOpen(true)}>
            <Plus size={18} aria-hidden="true" /> Tạo tài khoản
          </button>
        </div>

        <form className="user-management__search" onSubmit={submitSearch}>
          <label className="sr-only" htmlFor="user-management-search">
            Tìm tài khoản
          </label>
          <div className="user-management__search-input">
            <Search size={18} aria-hidden="true" />
            <input
              id="user-management-search"
              value={searchInput}
              onChange={(event) => setSearchInput(event.target.value)}
              placeholder="Tìm theo họ tên hoặc email"
            />
          </div>
          <button type="submit" className="btn btn-secondary">
            Tìm kiếm
          </button>
        </form>

        {loading ? (
          <div className="content-state" role="status">
            <span className="spinner" />
            <p>Đang tải danh sách tài khoản...</p>
          </div>
        ) : loadError ? (
          <div className="content-state" role="alert">
            <strong>Không thể tải dữ liệu</strong>
            <p>{loadError}</p>
            <button
              type="button"
              className="btn btn-secondary"
              onClick={() => setReload((value) => value + 1)}
            >
              Thử lại
            </button>
          </div>
        ) : users.length === 0 ? (
          <div className="content-state">
            <strong>Chưa có tài khoản phù hợp</strong>
            <p>
              {search
                ? 'Hãy thử từ khóa tìm kiếm khác.'
                : 'Tạo tài khoản đầu tiên để bắt đầu quản lý người dùng.'}
            </p>
          </div>
        ) : (
          <div className="table-container">
            <table className="data-table user-management__table">
              <thead>
                <tr>
                  <th>#</th>
                  <th>Tài khoản</th>
                  <th>Vai trò</th>
                  <th>Ngày tạo</th>
                  <th>Truy cập gần nhất</th>
                  <th>Địa chỉ IP</th>
                  <th>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {users.map((user, index) => {
                  const isCurrentUser = user.id === currentUser?.id
                  return (
                    <tr key={user.id}>
                      <td>{page * PAGE_SIZE + index + 1}</td>
                      <td className="user-management__identity">
                        <strong>{user.fullName}</strong>
                        <span>{user.email}</span>
                      </td>
                      <td>{rolesText(user.roles)}</td>
                      <td>{formatDateTime(user.createdAt)}</td>
                      <td>{accessDate(user.lastLoginAt)}</td>
                      <td>{user.lastLoginIp || '—'}</td>
                      <td>
                        <div className="user-management__actions">
                          <button
                            type="button"
                            className="btn btn-secondary btn-sm"
                            onClick={() => setUserToEdit(user)}
                          >
                            <PencilLine size={15} aria-hidden="true" /> Chỉnh sửa
                          </button>
                          <button
                            type="button"
                            className="btn btn-danger btn-sm user-management__delete"
                            onClick={() => setUserToDelete(user)}
                            disabled={isCurrentUser}
                            title={
                              isCurrentUser ? 'Không thể xóa tài khoản đang đăng nhập' : undefined
                            }
                          >
                            <Trash2 size={15} aria-hidden="true" /> Xóa
                          </button>
                        </div>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}

        {totalPages > 1 && (
          <div className="user-management__pagination">
            <span>
              Trang {page + 1} / {totalPages}
            </span>
            <div>
              <button
                type="button"
                className="btn btn-secondary btn-sm"
                disabled={page === 0 || loading}
                onClick={() => setPage((currentPage) => currentPage - 1)}
              >
                Trang trước
              </button>
              <button
                type="button"
                className="btn btn-secondary btn-sm"
                disabled={page >= totalPages - 1 || loading}
                onClick={() => setPage((currentPage) => currentPage + 1)}
              >
                Trang sau
              </button>
            </div>
          </div>
        )}
      </section>

      <ConfirmModal
        isOpen={userToDelete !== null}
        title="Xóa tài khoản"
        message={
          userToDelete
            ? `Xóa tài khoản ${userToDelete.fullName} (${userToDelete.email})? Tài khoản sẽ không thể đăng nhập, nhưng dữ liệu học vụ đã tạo vẫn được lưu lại.`
            : ''
        }
        confirmText="Xóa tài khoản"
        variant="danger"
        loading={deleting}
        onConfirm={handleDelete}
        onCancel={() => {
          if (!deleting) setUserToDelete(null)
        }}
      />

      {userToEdit && (
        <div className="modal-backdrop fade-in" onClick={() => setUserToEdit(null)}>
          <div
            className="modal-content user-management__edit-dialog scale-in"
            role="dialog"
            aria-modal="true"
            aria-labelledby="edit-account-dialog-title"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="modal-header">
              <h3 id="edit-account-dialog-title" className="modal-title">
                Chỉnh sửa tài khoản
              </h3>
              <button
                type="button"
                className="btn-close"
                onClick={() => setUserToEdit(null)}
                aria-label="Đóng hộp thoại"
              >
                <X size={22} aria-hidden="true" />
              </button>
            </div>
            <div className="modal-body">
              <EditAccountForm
                user={userToEdit}
                onCancel={() => setUserToEdit(null)}
                onUpdated={handleUpdated}
              />
            </div>
          </div>
        </div>
      )}

      {createOpen && (
        <div className="modal-backdrop fade-in" onClick={() => setCreateOpen(false)}>
          <div
            className="modal-content user-management__create-dialog scale-in"
            role="dialog"
            aria-modal="true"
            aria-labelledby="create-account-dialog-title"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="modal-header">
              <h3 id="create-account-dialog-title" className="modal-title">
                Tạo tài khoản mới
              </h3>
              <button
                type="button"
                className="btn-close"
                onClick={() => setCreateOpen(false)}
                aria-label="Đóng hộp thoại"
              >
                <X size={22} aria-hidden="true" />
              </button>
            </div>
            <div className="modal-body">
              <CreateAccountForm onCancel={() => setCreateOpen(false)} onCreated={handleCreated} />
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
