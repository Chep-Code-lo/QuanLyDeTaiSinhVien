import { AnimatedSelect } from '../components/AnimatedSelect'
import { useEffect, useState } from 'react'
import { academicApi } from '../api/academic'
import { allPages } from '../api/student'
import { groupApi } from '../api/groups'
import { registrationApi } from '../api/registrations'
import { topicApi } from '../api/topics'
import { PageHeader } from '../components/PageHeader'
import { StatusBadge } from '../components/StatusBadge'
import { RegistrationTopicForm } from '../components/RegistrationTopicForm'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import { formatDateTime } from '../utils/dateTime'
import { registrationWindowError } from '../utils/registrationWindow'
import type {
  RegistrationPeriodResponse,
  StudentGroupResponse,
  TopicRegistrationResponse,
  TopicResponse,
} from '../types/domain'

const registrationMessages: Record<string, string> = {
  'Topics can only be registered during the registration window.':
    'Hiện không trong thời gian mở đăng ký của đợt. Vui lòng kiểm tra thời gian đăng ký.',
  'Topic and student group must belong to the same registration period.':
    'Nhóm và đề tài phải thuộc cùng một đợt đăng ký.',
  'Only active student groups can register a topic.':
    'Chỉ nhóm đang hoạt động mới được đăng ký đề tài.',
  'Only the group leader can manage topic registration.':
    'Chỉ trưởng nhóm mới được đăng ký đề tài.',
  'This group already has a pending or approved topic registration.':
    'Nhóm đã có đăng ký đang chờ duyệt hoặc đã được duyệt.',
  'This group already has a project assignment.': 'Nhóm đã được phân công đề tài.',
  'This topic already has a project assignment.': 'Đề tài đã được phân công cho nhóm khác.',
  'This topic is reserved for another student group.': 'Đề tài được dành riêng cho nhóm khác.',
  'Only published topics can be registered.': 'Chỉ đề tài đã công bố mới được đăng ký.',
}
const messageOf = (error: any) => {
  const message = error.response?.data?.message
  return (
    registrationMessages[message] || message || 'Không thể thực hiện thao tác. Vui lòng thử lại.'
  )
}
export function RegistrationsPage() {
  const { user, hasAnyRole, hasRole, hasAppointment } = useAuth()
  const { showError, showSuccess } = useToast()
  const student = hasRole('STUDENT')
  const reviewer = hasAnyRole(['LECTURER', 'ADMIN'])
  const canCreateTopic =
    hasRole('ADMIN') || hasAppointment('DEAN') || hasAppointment('DEPARTMENT_HEAD')
  const [items, setItems] = useState<TopicRegistrationResponse[]>([])
  const [groups, setGroups] = useState<StudentGroupResponse[]>([])
  const [topics, setTopics] = useState<TopicResponse[]>([])
  const [periods, setPeriods] = useState<RegistrationPeriodResponse[]>([])
  const [groupId, setGroupId] = useState('')
  const [topicId, setTopicId] = useState('')
  const [motivation, setMotivation] = useState('')
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [currentTime, setCurrentTime] = useState(Date.now)
  const selectedGroup = groups.find((group) => group.id === Number(groupId))
  const selectedPeriod = periods.find((period) => period.id === selectedGroup?.periodId)
  const availableTopics = topics.filter((topic) => topic.periodId === selectedGroup?.periodId && topic.availableForRegistration && (!topic.reservedGroupId || topic.reservedGroupId === selectedGroup?.id))
  const registrationError = (now: number) => {
    if (!selectedGroup) return 'Chọn nhóm của bạn để xem đề tài cùng đợt đăng ký.'
    if (!selectedPeriod) return 'Không tải được đợt đăng ký của nhóm. Vui lòng tải lại trang.'
    const windowError = registrationWindowError(selectedPeriod, now)
    if (windowError) return windowError
    if (selectedGroup.status !== 'ACTIVE') return 'Chỉ nhóm đang hoạt động mới được đăng ký đề tài.'
    if (
      !selectedGroup.members.some(
        (member) => member.userId === user?.id && member.memberRole === 'LEADER',
      )
    )
      return 'Chỉ trưởng nhóm mới được đăng ký đề tài.'
    if (
      items.some(
        (item) =>
          item.groupId === selectedGroup.id && ['PENDING', 'APPROVED'].includes(item.status),
      )
    )
      return 'Nhóm đã có đăng ký đang chờ duyệt hoặc đã được duyệt.'
    return ''
  }
  const blockedReason = registrationError(currentTime)
  const load = async () => {
    setLoading(true)
    try {
      const xs = await allPages(page => registrationApi.list({ page, size: 100 }))
      setItems(xs)
      if (student) {
        const [mine, periodPage] = await Promise.all([
          groupApi.mine(),
          allPages(page => academicApi.getRegistrationPeriods(undefined, page, 100)),
        ])
        setGroups(mine)
        setPeriods(periodPage)
      }
      setTopics(await allPages(page => topicApi.list({ status: 'PUBLISHED', page, size: 100 })))
    } catch (error) {
      showError(messageOf(error))
    } finally {
      setLoading(false)
    }
  }
  useEffect(() => {
    load()
  }, [])
  useEffect(() => {
    if (!student) return
    const timer = window.setInterval(() => setCurrentTime(Date.now()), 1000)
    return () => window.clearInterval(timer)
  }, [student])
  const register = async (e: React.FormEvent) => {
    e.preventDefault()
    if (loading || submitting) return
    const reason = registrationError(Date.now())
    if (reason) return showError(reason)
    if (!groupId || !topicId) return showError('Chọn nhóm và đề tài để đăng ký.')
    if (!availableTopics.some((topic) => topic.id === Number(topicId)))
      return showError('Vui lòng chọn đề tài thuộc cùng đợt đăng ký với nhóm.')
    setSubmitting(true)
    try {
      await registrationApi.create({
        groupId: Number(groupId),
        topicId: Number(topicId),
        motivation: motivation.trim() || undefined,
      })
      setGroupId('')
      setTopicId('')
      setMotivation('')
      showSuccess('Đã gửi đăng ký đề tài.')
      await load()
    } catch (error) {
      showError(messageOf(error))
    } finally {
      setSubmitting(false)
    }
  }
  const review = async (item: TopicRegistrationResponse, status: 'APPROVED' | 'REJECTED') => {
    const decisionReason =
      status === 'REJECTED' ? window.prompt('Lý do từ chối (bắt buộc):') : undefined
    if (status === 'REJECTED' && !decisionReason?.trim()) return
    try {
      const updated = await registrationApi.review(item.id, {
        version: item.version,
        status,
        decisionReason: decisionReason?.trim(),
      })
      setItems((xs) => xs.map((x) => (x.id === item.id ? updated : x)))
      showSuccess(status === 'APPROVED' ? 'Đã duyệt đăng ký.' : 'Đã từ chối đăng ký.')
    } catch (error) {
      showError(messageOf(error))
    }
  }
  const withdraw = async (item: TopicRegistrationResponse) => {
    const decisionReason = window.prompt('Lý do rút đăng ký:')
    if (!decisionReason?.trim()) return
    try {
      const updated = await registrationApi.withdraw(item.id, {
        version: item.version,
        decisionReason: decisionReason.trim(),
      })
      setItems((xs) => xs.map((x) => (x.id === item.id ? updated : x)))
      showSuccess('Đã rút đăng ký.')
    } catch (error) {
      showError(messageOf(error))
    }
  }
  return (
    <>
      <PageHeader
        title="Đăng ký đề tài"
        description={student ? 'Mỗi nhóm chỉ được đăng ký một đề tài đã công bố. Sau khi được duyệt, nhóm thực hiện theo hướng dẫn của giảng viên phụ trách.' : undefined}
      />
      {canCreateTopic && (
        <RegistrationTopicForm onCreated={(topic) => setTopics((current) => [topic, ...current])} />
      )}
      {student && (
        <section className="page-card">
          <h2 className="section-title">Gửi đăng ký đề tài</h2>
          <form className="stack-form" onSubmit={register}>
            <AnimatedSelect
              className="form-select"
              aria-label="Nhóm đăng ký đề tài"
              value={groupId}
              onChange={(e) => {
                setGroupId(e.target.value)
                setTopicId('')
              }}
              disabled={loading || submitting}
              required
            >
              <option value="">Chọn nhóm của bạn</option>
              {groups.map((g) => (
                <option key={g.id} value={g.id}>
                  {g.name} - {g.periodName} ({g.status})
                </option>
              ))}
            </AnimatedSelect>
            {selectedPeriod && (
              <p className="text-secondary text-sm">
                Đợt {selectedPeriod.name}: đăng ký từ{' '}
                {formatDateTime(selectedPeriod.registrationStartAt)}
                {' đến '}
                {formatDateTime(selectedPeriod.registrationEndAt)}.
              </p>
            )}
            {groupId && blockedReason && (
              <p className="form-error" role="status">
                {blockedReason}
              </p>
            )}
            <AnimatedSelect
              className="form-select"
              aria-label="Đề tài cùng đợt đăng ký"
              value={topicId}
              onChange={(e) => setTopicId(e.target.value)}
              disabled={loading || submitting || !!blockedReason}
              required
            >
              <option value="">
                {!groupId ? 'Chọn nhóm trước khi chọn đề tài' : 'Chọn đề tài cùng đợt đã công bố'}
              </option>
              {availableTopics.map((topic) => (
                <option key={topic.id} value={topic.id}>
                  {topic.code} - {topic.title}
                </option>
              ))}
            </AnimatedSelect>
            {selectedGroup && !blockedReason && availableTopics.length === 0 && (
              <p className="text-secondary text-sm" role="status">
                Chưa có đề tài đã công bố trong đợt {selectedGroup.periodName}.
              </p>
            )}
            <textarea
              className="form-input"
              value={motivation}
              onChange={(e) => setMotivation(e.target.value)}
              disabled={loading || submitting || !!blockedReason}
              placeholder="Lý do/kinh nghiệm phù hợp với đề tài (không bắt buộc)"
            />
            <button
              className="btn btn-primary"
              disabled={loading || submitting || !!blockedReason || !topicId}
            >
              {submitting ? 'Đang gửi đăng ký...' : 'Gửi đăng ký'}
            </button>
          </form>
        </section>
      )}
      <section className="page-card">
        <h2 className="section-title">Danh sách hồ sơ</h2>
        {loading ? (
          <div className="content-state">
            <span className="spinner" />
            <p>Đang tải đăng ký...</p>
          </div>
        ) : (
          <div className="table-container">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Nhóm</th>
                  <th>Đề tài</th>
                  <th>Người đăng ký</th>
                  <th>Trạng thái</th>
                  <th>Phản hồi</th>
                  <th>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {items.length === 0 && <tr><td colSpan={6}><div className="content-state"><strong>Chưa có hồ sơ đăng ký</strong><p>Hồ sơ sẽ xuất hiện tại đây khi nhóm gửi đăng ký đề tài.</p></div></td></tr>}
                {items.map((item) => (
                  <tr key={item.id}>
                    <td>
                      {item.groupName}
                      <span className="table-subtext">{item.periodName}</span>
                    </td>
                    <td>
                      <strong>{item.topicCode}</strong>
                      <span className="table-subtext">{item.topicTitle}</span>
                    </td>
                    <td>{item.registeredByName}</td>
                    <td>
                      <StatusBadge status={item.status} />
                    </td>
                    <td>{item.decisionReason || '—'}</td>
                    <td>
                      {reviewer && item.status === 'PENDING' && (
                        <>
                          <button
                            className="btn btn-primary btn-sm"
                            onClick={() => review(item, 'APPROVED')}
                          >
                            Duyệt
                          </button>{' '}
                          <button
                            className="btn btn-secondary btn-sm"
                            onClick={() => review(item, 'REJECTED')}
                          >
                            Từ chối
                          </button>
                        </>
                      )}
                      {student && item.status === 'PENDING' && (
                        <button className="btn btn-secondary btn-sm" onClick={() => withdraw(item)}>
                          Rút
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </>
  )
}
