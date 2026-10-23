import { hasAcademicLeadership } from '../utils/managementAccess'
import { DateTimeInput } from '../components/DateTimeInput'
import { AnimatedSelect } from '../components/AnimatedSelect'
import { useCallback, useEffect, useRef, useState } from 'react'
import { isAxiosError } from 'axios'
import { academicApi } from '../api/academic'
import { allPages } from '../api/student'
import { useRequestLoading } from '../hooks/useRequestLoading'
import { groupApi } from '../api/groups'
import { PageHeader } from '../components/PageHeader'
import { ConfirmModal } from '../components/ConfirmModal'
import { SelectionPanel } from '../components/SelectionPanel'
import { StatusBadge } from '../components/StatusBadge'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import { parseUtcDateTime, toLocalDateTimeInput, formatDateTime } from '../utils/dateTime'
import type {
  GroupInvitationResponse,
  RegistrationPeriodResponse,
  StudentGroupResponse,
} from '../types/domain'

const invitationMessages: Record<string, string> = {
  'Invitation expiry must be in the future.': 'Hạn lời mời phải sau thời điểm hiện tại.',
  'Invitation expiry must be within the configured invitation lifetime and registration window.':
    'Hạn lời mời vượt quá số giờ cho phép hoặc thời điểm đóng đăng ký. Hãy chọn lại hạn lời mời.',
  'Student was not found.':
    'Không tìm thấy sinh viên có mã số này. Hãy kiểm tra lại mã số sinh viên.',
  'This student already has a pending invitation to the group.':
    'Sinh viên này đã có lời mời vào nhóm đang chờ phản hồi.',
  'Student already belongs to an active group in this registration period.':
    'Sinh viên này đã tham gia một nhóm trong đợt đăng ký này.',
  'All group members must belong to the same faculty as the registration period.':
    'Sinh viên được mời phải thuộc cùng khoa với đợt đăng ký.',
  'The group has reached its configured maximum number of members.':
    'Nhóm đã đủ số thành viên tối đa của đợt đăng ký.',
  'Group invitations are only available in an active registration period.':
    'Chỉ có thể mời sinh viên khi đợt đăng ký đang hoạt động.',
  'Group invitations are only available during the registration window.':
    'Chỉ có thể mời sinh viên trong thời gian mở đăng ký.',
  'Student already belongs to an active group.':
    'Bạn đã thuộc một nhóm đang hoạt động trong đợt đăng ký này.',
  'Student groups can only be created in an active registration period.':
    'Chỉ có thể tạo nhóm trong đợt đăng ký đang hoạt động.',
  'Student groups can only be managed during the registration window.':
    'Chỉ có thể tạo nhóm trong thời gian mở đăng ký.',
  'Student must belong to the same faculty as the registration period.':
    'Bạn cần thuộc cùng khoa với đợt đăng ký để tạo nhóm.',
}

const messageOf = (error: unknown) => {
  if (!isAxiosError(error)) return 'Không thể thực hiện thao tác. Vui lòng thử lại.'
  if (error.response?.status === 401) return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'
  if (error.response?.status === 403) return 'Tài khoản không có quyền thực hiện thao tác này.'
  if (!error.response) {
    if (error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT') {
      return 'Máy chủ phản hồi quá lâu. Vui lòng thử lại.'
    }
    return 'Không kết nối được máy chủ. Hãy kiểm tra mạng rồi thử lại.'
  }
  const message = error.response.data?.message
  return invitationMessages[message] || message || 'Không thể thực hiện thao tác. Vui lòng thử lại.'
}

function latestInvitationExpiry(period: RegistrationPeriodResponse, now: number) {
  return new Date(
    Math.min(
      now + period.invitationExpiryHours * 60 * 60 * 1000,
      parseUtcDateTime(period.registrationEndAt).getTime(),
    ),
  )
}

export function GroupsPage() {
  const { hasRole, user } = useAuth()
  const { showError, showSuccess } = useToast()
  const [groups, setGroups] = useState<StudentGroupResponse[]>([])
  const [periods, setPeriods] = useState<RegistrationPeriodResponse[]>([])
  const [incoming, setIncoming] = useState<GroupInvitationResponse[]>([])
  const [outgoing, setOutgoing] = useState<Record<number, GroupInvitationResponse[]>>({})
  const [periodId, setPeriodId] = useState<number>()
  const [name, setName] = useState('')
  const [codes, setCodes] = useState<Record<number, string>>({})
  const [expiresAt, setExpiresAt] = useState<Record<number, string>>({})
  const [creating, setCreating] = useState(false)
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [loadError, setLoadError] = useState('')
  const loadRequest = useRef(0)
  const [pendingAction, setPendingAction] = useState<{
    message: string
    action: () => Promise<void>
  } | null>(null)
  const [actionBusy, setActionBusy] = useState(false)
  const [currentTime, setCurrentTime] = useState(() => new Date().getTime())
  const student = hasRole('STUDENT')
  const [loading, setLoading] = useRequestLoading(
    JSON.stringify([periodId, student, user?.id, page]),
  )

  const isLeader = useCallback(
    (group: StudentGroupResponse) =>
      group.members.some((member) => member.userId === user?.id && member.memberRole === 'LEADER'),
    [user?.id],
  )

  useEffect(() => {
    const timer = window.setInterval(() => setCurrentTime(new Date().getTime()), 30_000)
    return () => window.clearInterval(timer)
  }, [])

  const load = useCallback(
    (isCurrent: () => boolean = () => true) => {
      const requestId = ++loadRequest.current
      const request = student
        ? Promise.all([
            groupApi.mine(),
            allPages((page) => groupApi.myInvitations(page, 100, 'PENDING')),
          ]).then(async ([myGroups, invitations]) => {
            const leaderGroups = myGroups.filter(
              (group) => isLeader(group) && group.status !== 'LOCKED' && group.status !== 'CLOSED',
            )
            const invitationLists = await Promise.all(
              leaderGroups.map(
                async (group) => [group.id, await groupApi.invitationsForGroup(group.id)] as const,
              ),
            )
            return {
              groups: myGroups,
              incoming: invitations,
              outgoing: Object.fromEntries(invitationLists),
              totalPages: 0,
            }
          })
        : groupApi.list({ periodId, page, size: 10 }).then((result) => ({
            groups: result.content,
            incoming: [],
            outgoing: {},
            totalPages: result.totalPages,
          }))
      return request
        .then((result) => {
          if (!isCurrent() || requestId !== loadRequest.current) return
          setGroups(result.groups)
          setIncoming(result.incoming)
          setOutgoing(result.outgoing)
          setTotalPages(result.totalPages)
          setLoadError('')
        })
        .catch((error) => {
          if (!isCurrent() || requestId !== loadRequest.current) return
          setLoadError(messageOf(error))
        })
        .finally(() => {
          if (isCurrent() && requestId === loadRequest.current) setLoading(false)
        })
    },
    [student, isLeader, periodId, page, setLoading],
  )

  useEffect(() => {
    const loadPeriods = async () => {
      const firstPage = await academicApi.getRegistrationPeriods(undefined, 0, 100)
      const remainingPages = await Promise.all(
        Array.from({ length: Math.max(0, firstPage.totalPages - 1) }, (_, index) =>
          academicApi.getRegistrationPeriods(undefined, index + 1, 100),
        ),
      )
      setPeriods([firstPage, ...remainingPages].flatMap((page) => page.content))
    }
    void loadPeriods().catch(() => showError('Không tải được đợt đăng ký. Vui lòng tải lại trang.'))
  }, [showError])

  useEffect(() => {
    let active = true
    void load(() => active)
    return () => {
      active = false
    }
  }, [load])

  const create = async (event: React.FormEvent) => {
    event.preventDefault()
    if (creating) return
    if (!periodId || !name.trim()) return showError('Hãy chọn đợt đăng ký và nhập tên nhóm.')
    setCreating(true)
    try {
      await groupApi.create({ periodId, name: name.trim() })
      setName('')
      showSuccess('Đã tạo nhóm. Bạn là trưởng nhóm.')
      await load()
    } catch (error) {
      showError(messageOf(error))
    } finally {
      setCreating(false)
    }
  }

  const invite = async (group: StudentGroupResponse, expiry: string) => {
    const studentCode = codes[group.id]?.trim()
    if (!studentCode) return showError('Nhập mã số sinh viên trước.')
    const period = periods.find((item) => item.id === group.periodId)
    if (!period) return showError('Chưa tải được thông tin đợt đăng ký. Vui lòng tải lại trang.')
    const now = new Date().getTime()
    if (
      period.status !== 'ACTIVE' ||
      now < parseUtcDateTime(period.registrationStartAt).getTime() ||
      now >= parseUtcDateTime(period.registrationEndAt).getTime()
    ) {
      return showError(
        'Chỉ có thể mời sinh viên trong thời gian mở đăng ký của đợt đang hoạt động.',
      )
    }
    const expiryTime = new Date(expiry).getTime()
    if (!Number.isFinite(expiryTime) || expiryTime <= now) {
      return showError('Hạn lời mời phải sau thời điểm hiện tại.')
    }
    const latestExpiry = latestInvitationExpiry(period, now)
    if (expiryTime > latestExpiry.getTime()) {
      return showError(`Hạn lời mời tối đa là ${formatDateTime(latestExpiry)}.`)
    }
    try {
      await groupApi.invite({ groupId: group.id, studentCode, expiresAt: expiry })
      setCodes((items) => ({ ...items, [group.id]: '' }))
      setExpiresAt((items) => {
        const next = { ...items }
        delete next[group.id]
        return next
      })
      showSuccess('Đã gửi lời mời vào nhóm.')
      await load()
    } catch (error) {
      showError(messageOf(error))
    }
  }

  const respond = async (invitation: GroupInvitationResponse, action: 'accept' | 'reject') => {
    try {
      const result =
        action === 'accept'
          ? await groupApi.acceptInvitation(invitation.id)
          : await groupApi.rejectInvitation(invitation.id)
      if (result.status === 'EXPIRED') showError('Lời mời đã hết hạn.')
      else showSuccess(action === 'accept' ? 'Bạn đã tham gia nhóm.' : 'Đã từ chối lời mời.')
      await load()
    } catch (error) {
      showError(messageOf(error))
    }
  }

  const cancelInvitation = async (invitation: GroupInvitationResponse) => {
    try {
      const result = await groupApi.cancelInvitation(invitation.id)
      if (result.status === 'EXPIRED') showError('Lời mời đã hết hạn.')
      else showSuccess('Đã hủy lời mời.')
      await load()
    } catch (error) {
      showError(messageOf(error))
    }
  }

  const remove = async (groupId: number, studentId: number) => {
    try {
      await groupApi.removeMember(groupId, studentId)
      showSuccess('Đã xóa thành viên.')
      await load()
    } catch (error) {
      showError(messageOf(error))
    }
  }

  return (
    <>
      <PageHeader
        title={
          user?.roles?.includes('LECTURER') && !hasAcademicLeadership(user)
            ? 'Nhóm hướng dẫn'
            : 'Nhóm đề tài'
        }
      />

      {student && incoming.some((invitation) => invitation.status === 'PENDING') && (
        <section className="page-card">
          <h2 className="section-title">Lời mời chờ phản hồi</h2>
          <div className="stack-list">
            {incoming
              .filter((invitation) => invitation.status === 'PENDING')
              .map((invitation) => (
                <article
                  key={invitation.id}
                  className="notification-item notification-item--unread"
                >
                  <div>
                    <h2>{invitation.groupName}</h2>
                    <p>
                      {invitation.invitedByName} mời bạn tham gia nhóm trong đợt{' '}
                      {invitation.periodName}.
                    </p>
                    <time>Hết hạn: {formatDateTime(invitation.expiresAt)}</time>
                  </div>
                  <div className="inline-form">
                    <button
                      className="btn btn-primary"
                      onClick={() => respond(invitation, 'accept')}
                    >
                      Đồng ý
                    </button>
                    <button
                      className="btn btn-secondary"
                      onClick={() => respond(invitation, 'reject')}
                    >
                      Từ chối
                    </button>
                  </div>
                </article>
              ))}
          </div>
        </section>
      )}

      {student ? (
        <section className="page-card">
          <h2 className="section-title">Tạo nhóm mới</h2>
          <form className="inline-form" onSubmit={create}>
            <AnimatedSelect
              className="form-select"
              value={periodId ?? ''}
              onChange={(event) => {
                setPage(0)
                setPeriodId(Number(event.target.value) || undefined)
              }}
              required
            >
              <option value="">Chọn đợt đăng ký</option>
              {periods.map((period) => (
                <option key={period.id} value={period.id}>
                  {period.name} - {period.projectType}
                </option>
              ))}
            </AnimatedSelect>
            <input
              className="form-input"
              value={name}
              onChange={(event) => setName(event.target.value)}
              placeholder="Tên nhóm"
              required
            />
            <button className="btn btn-primary" type="submit" disabled={creating}>
              {creating ? 'Đang tạo...' : 'Tạo nhóm'}
            </button>
          </form>
        </section>
      ) : (
        <SelectionPanel id="group-period-filter" label="Lọc theo đợt đăng ký">
          <AnimatedSelect
            id="group-period-filter"
            className="form-select"
            value={periodId ?? ''}
            onChange={(event) => {
              setPage(0)
              setPeriodId(Number(event.target.value) || undefined)
            }}
          >
            <option value="">Tất cả đợt</option>
            {periods.map((period) => (
              <option key={period.id} value={period.id}>
                {period.name}
              </option>
            ))}
          </AnimatedSelect>
        </SelectionPanel>
      )}

      <section className="page-card">
        {loadError && (
          <p role="alert">
            {loadError}{' '}
            <button type="button" className="text-button" onClick={() => void load()}>
              Thử lại
            </button>
          </p>
        )}
        {loading ? (
          <div className="content-state">
            <span className="spinner" />
            <p>Đang tải nhóm...</p>
          </div>
        ) : groups.length === 0 ? (
          <div className="content-state">
            <strong>Chưa có nhóm.</strong>
            <p>Sinh viên có thể tạo nhóm trong đợt đăng ký phù hợp.</p>
          </div>
        ) : (
          <div className="stack-list">
            {groups.map((group) => {
              const leader = isLeader(group)
              const invitations = outgoing[group.id] ?? []
              const period = periods.find((item) => item.id === group.periodId)
              const latestExpiry = period ? latestInvitationExpiry(period, currentTime) : undefined
              const expiry =
                expiresAt[group.id] ?? (latestExpiry ? toLocalDateTimeInput(latestExpiry) : '')
              const now = currentTime
              const registrationOpen =
                period?.status === 'ACTIVE' &&
                now >= parseUtcDateTime(period.registrationStartAt).getTime() &&
                now < parseUtcDateTime(period.registrationEndAt).getTime()
              return (
                <article key={group.id} className="group-card">
                  <div className="content-card__header">
                    <div>
                      <h2>{group.name}</h2>
                      <span>
                        {group.periodName} · {group.members.length} thành viên
                      </span>
                    </div>
                    <StatusBadge
                      status={group.status}
                      label={student && group.status === 'LOCKED' ? 'Đã khóa' : undefined}
                    />
                  </div>
                  <ul className="member-list">
                    {group.members.map((member) => (
                      <li key={member.studentId}>
                        <span>
                          <strong>{member.fullName}</strong> · {member.studentCode} ·{' '}
                          {member.className}
                        </span>
                        {member.memberRole === 'LEADER' ? (
                          <span className="badge badge-info">Trưởng nhóm</span>
                        ) : student && leader ? (
                          <button
                            className="text-button"
                            onClick={() =>
                              setPendingAction({
                                message: `Xóa ${member.fullName} khỏi nhóm ${group.name}?`,
                                action: () => remove(group.id, member.studentId),
                              })
                            }
                          >
                            Xóa
                          </button>
                        ) : null}
                      </li>
                    ))}
                  </ul>
                  {student && leader && group.status !== 'LOCKED' && group.status !== 'CLOSED' && (
                    <>
                      <form
                        className="inline-form"
                        onSubmit={(event) => {
                          event.preventDefault()
                          void invite(group, expiry)
                        }}
                      >
                        <input
                          className="form-input"
                          aria-label="Mã số sinh viên được mời"
                          value={codes[group.id] || ''}
                          onChange={(event) =>
                            setCodes((items) => ({ ...items, [group.id]: event.target.value }))
                          }
                          placeholder="Mã số sinh viên"
                        />
                        <DateTimeInput
                          className="form-input"
                          type="datetime-local"
                          aria-label="Hạn lời mời"
                          required
                          min={toLocalDateTimeInput(new Date(Math.ceil((now + 1) / 60000) * 60000))}
                          max={latestExpiry ? toLocalDateTimeInput(latestExpiry) : undefined}
                          value={expiry}
                          onValueChange={(value) =>
                            setExpiresAt((items) => ({ ...items, [group.id]: value }))
                          }
                        />
                        <button
                          type="submit"
                          className="btn btn-secondary"
                          disabled={!registrationOpen}
                        >
                          Gửi lời mời
                        </button>
                      </form>
                      <p className="text-secondary text-xs">
                        {!period
                          ? 'Đang tải thời hạn lời mời...'
                          : !registrationOpen
                            ? 'Hiện không trong thời gian mở đăng ký của đợt đang hoạt động.'
                            : `Lời mời có thời hạn tối đa ${period.invitationExpiryHours} giờ và không quá ${formatDateTime(latestExpiry!)}.`}
                      </p>
                      {invitations.length > 0 && (
                        <div className="stack-list">
                          {invitations.map((invitation) => (
                            <div key={invitation.id} className="member-list group-invitation-row">
                              <span className="group-invitation-row__student">
                                {invitation.invitedStudentName} · {invitation.invitedStudentCode}
                              </span>
                              <span className="group-invitation-row__actions">
                                <StatusBadge status={invitation.status} />
                                {invitation.status === 'PENDING' && (
                                  <button
                                    className="text-button"
                                    onClick={() =>
                                      setPendingAction({
                                        message: `Hủy lời mời vào nhóm dành cho ${invitation.invitedStudentName}?`,
                                        action: () => cancelInvitation(invitation),
                                      })
                                    }
                                  >
                                    Hủy lời mời
                                  </button>
                                )}
                              </span>
                            </div>
                          ))}
                        </div>
                      )}
                    </>
                  )}
                </article>
              )
            })}
          </div>
        )}
        {!student && totalPages > 1 && (
          <div className="table-pagination">
            <span>
              Trang {page + 1} / {totalPages}
            </span>
            <div>
              <button
                type="button"
                className="btn btn-secondary"
                disabled={loading || page === 0}
                onClick={() => setPage(page - 1)}
              >
                Trước
              </button>
              <button
                type="button"
                className="btn btn-secondary"
                disabled={loading || page >= totalPages - 1}
                onClick={() => setPage(page + 1)}
              >
                Sau
              </button>
            </div>
          </div>
        )}
      </section>
      <ConfirmModal
        isOpen={pendingAction !== null}
        title="Xác nhận thay đổi nhóm"
        message={pendingAction?.message ?? ''}
        loading={actionBusy}
        onCancel={() => {
          if (!actionBusy) setPendingAction(null)
        }}
        onConfirm={() => {
          if (!pendingAction || actionBusy) return
          setActionBusy(true)
          void pendingAction.action().finally(() => {
            setActionBusy(false)
            setPendingAction(null)
          })
        }}
      />
    </>
  )
}
