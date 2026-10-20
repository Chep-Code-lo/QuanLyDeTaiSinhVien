import { useEffect, useState } from 'react'
import { allPages, isWindowOpen, errorMessage } from '../api/student'
import { registrationApi } from '../api/registrations'
import { RemoteState } from '../components/RemoteState'
import { academicApi } from '../api/academic'
import { groupApi } from '../api/groups'
import { PageHeader } from '../components/PageHeader'
import { StatusBadge } from '../components/StatusBadge'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import type { GroupInvitationResponse, RegistrationPeriodResponse, StudentGroupResponse, TopicRegistrationResponse } from '../types/domain'

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
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [registrations, setRegistrations] = useState<TopicRegistrationResponse[]>([])
  const [assignedGroups, setAssignedGroups] = useState<number[]>([])
  const hasOpenGroup = groups.some(g => g.status !== 'CLOSED')
  const canManage = (g: StudentGroupResponse) => isLeader(g) && ['FORMING','ACTIVE'].includes(g.status) && isWindowOpen(periods.find(p => p.id === g.periodId), 'registration') && !registrations.some(r=>r.groupId===g.id&&['PENDING','APPROVED'].includes(r.status)) && !assignedGroups.includes(g.id)
  const expiryFor = (g: StudentGroupResponse) => {
    const end = periods.find(p=>p.id===g.periodId)?.registrationEndAt
    const value = defaultExpiry(invitationExpiryHoursFor(g.periodId))
    if (!end || new Date(end) >= new Date(value)) return value
    const local = new Date(end)
    local.setMinutes(local.getMinutes() - local.getTimezoneOffset())
    return local.toISOString().slice(0,16)
  }

  const isLeader = (group: StudentGroupResponse) => group.members.some((member) => member.userId === user?.id && member.memberRole === 'LEADER')
  const invitationExpiryHoursFor = (targetPeriodId: number) =>
    periods.find((period) => period.id === targetPeriodId)?.invitationExpiryHours ?? 72

  const load = async () => {
    setLoading(true)
    setError('')
    try {
      if (student) {
        const [myGroups, invitations, rs, ps] = await Promise.all([groupApi.mine(), allPages(page=>groupApi.myInvitations(page,100)), allPages(page=>registrationApi.list({page,size:100})), allPages(page=>academicApi.getProjectAssignments(undefined,page,100))])
        setRegistrations(rs)
        setAssignedGroups(ps.map(p=>p.groupId))
        setGroups(myGroups)
        setIncoming(invitations)
        const leaderGroups = myGroups.filter(isLeader)
        const invitationLists = await Promise.all(leaderGroups.map(async (group) => [group.id, await groupApi.invitationsForGroup(group.id)] as const))
        setOutgoing(Object.fromEntries(invitationLists))
      } else {
        setGroups((await groupApi.list({ periodId, size: 100 })).content)
        setIncoming([])
        setOutgoing({})
      }
    } catch (error) {
      setError(errorMessage(error))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    allPages(page=>academicApi.getRegistrationPeriods(undefined,page,100)).then(setPeriods).catch(e=>setError(errorMessage(e)))
  }, [])

  useEffect(() => {
    void load()
  }, [periodId, student, user?.id])

  const create = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!periodId || !name.trim()) return showError('Hãy chọn đợt đăng ký và nhập tên nhóm.')
    if (busy) return
    setBusy(true)
    try {
      await groupApi.create({ periodId, name: name.trim() })
      setName('')
      showSuccess('Đã tạo nhóm. Bạn là trưởng nhóm.')
      await load()
    } catch (error) {
      showError(messageOf(error))
    } finally { setBusy(false) }
  }

  const invite = async (group: StudentGroupResponse) => {
    const studentCode = codes[group.id]?.trim()
    const expiry = expiresAt[group.id] || expiryFor(group)
    if (!studentCode) return showError('Nhập mã số sinh viên trước.')
    if (busy) return
    setBusy(true)
    try {
      await groupApi.invite({ groupId: group.id, studentCode, expiresAt: new Date(expiry).toISOString() })
      setCodes((items) => ({ ...items, [group.id]: '' }))
      showSuccess('Đã gửi lời mời vào nhóm.')
      await load()
    } catch (error) {
      showError(messageOf(error))
    } finally { setBusy(false) }
  }

  const respond = async (invitation: GroupInvitationResponse, action: 'accept' | 'reject') => {
    if (busy) return
    setBusy(true)
    try {
      const result = action === 'accept' ? await groupApi.acceptInvitation(invitation.id) : await groupApi.rejectInvitation(invitation.id)
      if (result.status === 'EXPIRED') showError('Lời mời đã hết hạn.')
      else showSuccess(action === 'accept' ? 'Bạn đã tham gia nhóm.' : 'Đã từ chối lời mời.')
      await load()
    } catch (error) {
      showError(messageOf(error))
    } finally { setBusy(false) }
  }

  const cancelInvitation = async (invitation: GroupInvitationResponse) => {
    if (busy) return
    setBusy(true)
    try {
      const result = await groupApi.cancelInvitation(invitation.id)
      if (result.status === 'EXPIRED') showError('Lời mời đã hết hạn.')
      else showSuccess('Đã hủy lời mời.')
      await load()
    } catch (error) {
      showError(messageOf(error))
    } finally { setBusy(false) }
  }

  const remove = async (groupId: number, studentId: number) => {
    if (!window.confirm("Xác nhận xóa thành viên khỏi nhóm?")) return
    if (busy) return
    setBusy(true)
    try {
      await groupApi.removeMember(groupId, studentId)
      showSuccess('Đã xóa thành viên.')
      await load()
    } catch (error) {
      showError(messageOf(error))
    } finally { setBusy(false) }
  }

  return <>
    <PageHeader title="Nhóm thực hiện" description="Mỗi sinh viên chỉ thuộc một nhóm đang thực hiện, tối đa 3 người; trưởng nhóm mời thành viên và người được mời tự xác nhận tham gia." />

    <RemoteState loading={false} error={error} retry={load} />
    <fieldset className="student-fieldset" disabled={busy || loading || !!error}>
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
                <button className="btn btn-primary" disabled={hasOpenGroup || !isWindowOpen(periods.find(p=>p.id===invitation.periodId),'registration')} onClick={() => respond(invitation, 'accept')}>Đồng ý</button>
                <button className="btn btn-secondary" onClick={() => respond(invitation, 'reject')}>Từ chối</button>
              </div>
            </article>
          ))}
        </div>
      </section>
    )}

    {student ? (hasOpenGroup ? <section className="page-card"><p>Bạn đã thuộc một nhóm đang thực hiện. Không thể tạo hoặc tham gia nhóm khác.</p></section> : (
      <section className="page-card">
        <h2 className="section-title">Tạo nhóm mới</h2>
        <form className="inline-form" onSubmit={create}>
          <select className="form-select" value={periodId ?? ''} onChange={(event) => setPeriodId(Number(event.target.value) || undefined)} required>
            <option value="">Chọn đợt đăng ký</option>
            {periods.filter(p=>isWindowOpen(p,'registration')&&!groups.some(g=>g.periodId===p.id)).map((period) => <option key={period.id} value={period.id}>{period.name} - {period.projectType}</option>)}
          </select>
          <input className="form-input" value={name} onChange={(event) => setName(event.target.value)} placeholder="Tên nhóm" required />
          <button className="btn btn-primary">Tạo nhóm</button>
        </form>
      </section>
    )) : (
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
                    {member.memberRole === 'LEADER' ? <span className="badge badge-info">Trưởng nhóm</span> : student && canManage(group) ? <button className="text-button" onClick={() => remove(group.id, member.studentId)}>Xóa</button> : null}
                  </li>
                ))}
              </ul>
              {student && canManage(group) && <>
                <div className="inline-form">
                  <input className="form-input" value={codes[group.id] || ''} onChange={(event) => setCodes((items) => ({ ...items, [group.id]: event.target.value }))} placeholder="Mã số sinh viên" />
                  <input className="form-input" type="datetime-local" value={expiresAt[group.id] || expiryFor(group)} onChange={(event) => setExpiresAt((items) => ({ ...items, [group.id]: event.target.value }))} />
                  <button className="btn btn-secondary" disabled={group.members.length >= 3} onClick={() => invite(group)}>Gửi lời mời</button>
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
    </fieldset>
  </>
}
