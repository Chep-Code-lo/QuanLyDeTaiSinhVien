import { useEffect, useState } from 'react'
import { academicApi } from '../api/academic'
import { groupApi } from '../api/groups'
import { PageHeader } from '../components/PageHeader'
import { StatusBadge } from '../components/StatusBadge'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import type { GroupInvitationResponse, RegistrationPeriodResponse, StudentGroupResponse } from '../types/domain'

const messageOf = (error: any) => error.response?.data?.message || 'Không thể thực hiện thao tác. Vui lòng thử lại.'

function defaultExpiry(hours: number) {
  const value = new Date()
  value.setHours(value.getHours() + hours)
  value.setSeconds(0, 0)
  const pad = (part: number) => String(part).padStart(2, '0')
  return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())}T${pad(value.getHours())}:${pad(value.getMinutes())}`
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
  const [loading, setLoading] = useState(true)
  const student = hasRole('STUDENT')

  const isLeader = (group: StudentGroupResponse) => group.members.some((member) => member.userId === user?.id && member.memberRole === 'LEADER')
  const invitationExpiryHoursFor = (targetPeriodId: number) =>
    periods.find((period) => period.id === targetPeriodId)?.invitationExpiryHours ?? 72

  const load = async () => {
    setLoading(true)
    try {
      if (student) {
        const [myGroups, invitations] = await Promise.all([groupApi.mine(), groupApi.myInvitations()])
        setGroups(myGroups)
        setIncoming(invitations.content)
        const leaderGroups = myGroups.filter(isLeader)
        const invitationLists = await Promise.all(leaderGroups.map(async (group) => [group.id, await groupApi.invitationsForGroup(group.id)] as const))
        setOutgoing(Object.fromEntries(invitationLists))
      } else {
        setGroups((await groupApi.list({ periodId, size: 100 })).content)
        setIncoming([])
        setOutgoing({})
      }
    } catch (error) {
      showError(messageOf(error))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    academicApi.getRegistrationPeriods(undefined, 0, 100).then((items) => setPeriods(items.content)).catch(() => undefined)
  }, [])

  useEffect(() => {
    void load()
  }, [periodId, student, user?.id])

  const create = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!periodId || !name.trim()) return showError('Hãy chọn đợt đăng ký và nhập tên nhóm.')
    try {
      await groupApi.create({ periodId, name: name.trim() })
      setName('')
      showSuccess('Đã tạo nhóm. Bạn là trưởng nhóm.')
      await load()
    } catch (error) {
      showError(messageOf(error))
    }
  }

  const invite = async (group: StudentGroupResponse) => {
    const studentCode = codes[group.id]?.trim()
    const expiry = expiresAt[group.id] || defaultExpiry(invitationExpiryHoursFor(group.periodId))
    if (!studentCode) return showError('Nhập mã số sinh viên trước.')
    try {
      await groupApi.invite({ groupId: group.id, studentCode, expiresAt: expiry })
      setCodes((items) => ({ ...items, [group.id]: '' }))
      showSuccess('Đã gửi lời mời vào nhóm.')
      await load()
    } catch (error) {
      showError(messageOf(error))
    }
  }

  const respond = async (invitation: GroupInvitationResponse, action: 'accept' | 'reject') => {
    try {
      const result = action === 'accept' ? await groupApi.acceptInvitation(invitation.id) : await groupApi.rejectInvitation(invitation.id)
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

  return <>
    <PageHeader title="Nhóm thực hiện" description="Mỗi sinh viên chỉ thuộc một nhóm trong một đợt; trưởng nhóm mời thành viên và người được mời tự xác nhận tham gia." />

    {student && incoming.some((invitation) => invitation.status === 'PENDING') && (
      <section className="page-card">
        <h2 className="section-title">Lời mời chờ phản hồi</h2>
        <div className="stack-list">
          {incoming.filter((invitation) => invitation.status === 'PENDING').map((invitation) => (
            <article key={invitation.id} className="notification-item notification-item--unread">
              <div>
                <h2>{invitation.groupName}</h2>
                <p>{invitation.invitedByName} mời bạn tham gia nhóm trong đợt {invitation.periodName}.</p>
                <time>Hết hạn: {new Date(invitation.expiresAt).toLocaleString('vi-VN')}</time>
              </div>
              <div className="inline-form">
                <button className="btn btn-primary" onClick={() => respond(invitation, 'accept')}>Đồng ý</button>
                <button className="btn btn-secondary" onClick={() => respond(invitation, 'reject')}>Từ chối</button>
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
          <select className="form-select" value={periodId ?? ''} onChange={(event) => setPeriodId(Number(event.target.value) || undefined)} required>
            <option value="">Chọn đợt đăng ký</option>
            {periods.map((period) => <option key={period.id} value={period.id}>{period.name} - {period.projectType}</option>)}
          </select>
          <input className="form-input" value={name} onChange={(event) => setName(event.target.value)} placeholder="Tên nhóm" required />
          <button className="btn btn-primary">Tạo nhóm</button>
        </form>
      </section>
    ) : (
      <section className="page-card">
        <label className="form-label">Lọc theo đợt đăng ký</label>
        <select className="form-select" value={periodId ?? ''} onChange={(event) => setPeriodId(Number(event.target.value) || undefined)}>
          <option value="">Tất cả đợt</option>
          {periods.map((period) => <option key={period.id} value={period.id}>{period.name}</option>)}
        </select>
      </section>
    )}

    <section className="page-card">
      {loading ? (
        <div className="content-state"><span className="spinner" /><p>Đang tải nhóm...</p></div>
      ) : groups.length === 0 ? (
        <div className="content-state"><strong>Chưa có nhóm.</strong><p>Sinh viên có thể tạo nhóm trong đợt đăng ký phù hợp.</p></div>
      ) : (
        <div className="stack-list">
          {groups.map((group) => {
            const leader = isLeader(group)
            const invitations = outgoing[group.id] ?? []
            const invitationExpiryHours = invitationExpiryHoursFor(group.periodId)
            return <article key={group.id} className="group-card">
              <div className="content-card__header">
                <div><h2>{group.name}</h2><span>{group.periodName} · {group.members.length} thành viên</span></div>
                <StatusBadge status={group.status} />
              </div>
              <ul className="member-list">
                {group.members.map((member) => (
                  <li key={member.studentId}>
                    <span><strong>{member.fullName}</strong> · {member.studentCode} · {member.className}</span>
                    {member.memberRole === 'LEADER' ? <span className="badge badge-info">Trưởng nhóm</span> : student && leader ? <button className="text-button" onClick={() => remove(group.id, member.studentId)}>Xóa</button> : null}
                  </li>
                ))}
              </ul>
              {student && leader && group.status !== 'LOCKED' && group.status !== 'CLOSED' && <>
                <div className="inline-form">
                  <input className="form-input" value={codes[group.id] || ''} onChange={(event) => setCodes((items) => ({ ...items, [group.id]: event.target.value }))} placeholder="Mã số sinh viên" />
                  <input className="form-input" type="datetime-local" value={expiresAt[group.id] || defaultExpiry(invitationExpiryHours)} onChange={(event) => setExpiresAt((items) => ({ ...items, [group.id]: event.target.value }))} />
                  <button className="btn btn-secondary" onClick={() => invite(group)}>Gửi lời mời</button>
                </div>
                <p className="text-secondary text-xs">Lời mời có thời hạn tối đa {invitationExpiryHours} giờ, đồng thời không được vượt quá thời điểm đóng đăng ký.</p>
                {invitations.length > 0 && (
                  <div className="stack-list">
                    {invitations.map((invitation) => (
                      <div key={invitation.id} className="member-list">
                        <span>{invitation.invitedStudentName} · {invitation.invitedStudentCode} · <StatusBadge status={invitation.status} /></span>
                        {invitation.status === 'PENDING' && <button className="text-button" onClick={() => cancelInvitation(invitation)}>Hủy lời mời</button>}
                      </div>
                    ))}
                  </div>
                )}
              </>}
            </article>
          })}
        </div>
      )}
    </section>
  </>
}
