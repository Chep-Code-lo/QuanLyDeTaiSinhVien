import { useEffect, useState } from 'react'
import { academicApi } from '../api/academic'
import { groupApi } from '../api/groups'
import { PageHeader } from '../components/PageHeader'
import { StatusBadge } from '../components/StatusBadge'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import type { RegistrationPeriodResponse, StudentGroupResponse } from '../types/domain'

const messageOf = (error: any) => error.response?.data?.message || 'Không thể thực hiện thao tác. Vui lòng thử lại.'

export function GroupsPage() {
  const { hasRole } = useAuth(); const { showError, showSuccess } = useToast()
  const [groups, setGroups] = useState<StudentGroupResponse[]>([]); const [periods, setPeriods] = useState<RegistrationPeriodResponse[]>([])
  const [periodId, setPeriodId] = useState<number>(); const [name, setName] = useState(''); const [codes, setCodes] = useState<Record<number, string>>({}); const [loading, setLoading] = useState(true)
  const student = hasRole('STUDENT')
  const load = async () => { setLoading(true); try { setGroups(student ? await groupApi.mine() : (await groupApi.list({ periodId, size: 100 })).content) } catch (error) { showError(messageOf(error)) } finally { setLoading(false) } }
  useEffect(() => { academicApi.getRegistrationPeriods(undefined, 0, 100).then((x) => setPeriods(x.content)).catch(() => undefined) }, [])
  useEffect(() => { load() }, [periodId])
  const create = async (e: React.FormEvent) => { e.preventDefault(); if (!periodId || !name.trim()) return showError('Hãy chọn đợt đăng ký và nhập tên nhóm.'); try { await groupApi.create({ periodId, name: name.trim() }); setName(''); showSuccess('Đã tạo nhóm. Bạn là trưởng nhóm.'); await load() } catch (error) { showError(messageOf(error)) } }
  const add = async (id: number) => { const studentCode = codes[id]?.trim(); if (!studentCode) return showError('Nhập mã số sinh viên trước.'); try { const group = await groupApi.addMember(id, studentCode); setGroups((xs) => xs.map((x) => x.id === id ? group : x)); setCodes((x) => ({ ...x, [id]: '' })); showSuccess('Đã thêm thành viên.') } catch (error) { showError(messageOf(error)) } }
  const remove = async (groupId: number, studentId: number) => { try { const group = await groupApi.removeMember(groupId, studentId); setGroups((xs) => xs.map((x) => x.id === groupId ? group : x)); showSuccess('Đã xóa thành viên.') } catch (error) { showError(messageOf(error)) } }
  return <><PageHeader title="Nhóm thực hiện" description="Mỗi sinh viên chỉ thuộc một nhóm trong một đợt; nhóm có tối đa 3 thành viên và một trưởng nhóm." />
    {student ? <section className="page-card"><h2 className="section-title">Tạo nhóm mới</h2><form className="inline-form" onSubmit={create}><select className="form-select" value={periodId ?? ''} onChange={(e) => setPeriodId(Number(e.target.value) || undefined)} required><option value="">Chọn đợt đăng ký</option>{periods.map((p) => <option key={p.id} value={p.id}>{p.name} - {p.projectType}</option>)}</select><input className="form-input" value={name} onChange={(e) => setName(e.target.value)} placeholder="Tên nhóm" required /><button className="btn btn-primary">Tạo nhóm</button></form></section> : <section className="page-card"><label className="form-label">Lọc theo đợt đăng ký</label><select className="form-select" value={periodId ?? ''} onChange={(e) => setPeriodId(Number(e.target.value) || undefined)}><option value="">Tất cả đợt</option>{periods.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}</select></section>}
    <section className="page-card">{loading ? <div className="content-state"><span className="spinner" /><p>Đang tải nhóm...</p></div> : groups.length === 0 ? <div className="content-state"><strong>Chưa có nhóm.</strong><p>Sinh viên có thể tạo nhóm trong đợt đăng ký phù hợp.</p></div> : <div className="stack-list">{groups.map((group) => <article key={group.id} className="group-card"><div className="content-card__header"><div><h2>{group.name}</h2><span>{group.periodName} · {group.members.length}/3 thành viên</span></div><StatusBadge status={group.status} /></div><ul className="member-list">{group.members.map((member) => <li key={member.studentId}><span><strong>{member.fullName}</strong> · {member.studentCode} · {member.className}</span>{member.memberRole === 'LEADER' ? <span className="badge badge-info">Trưởng nhóm</span> : student ? <button className="text-button" onClick={() => remove(group.id, member.studentId)}>Xóa</button> : null}</li>)}</ul>{student && group.status !== 'LOCKED' && group.status !== 'CLOSED' && group.members.length < 3 && <div className="inline-form"><input className="form-input" value={codes[group.id] || ''} onChange={(e) => setCodes((x) => ({ ...x, [group.id]: e.target.value }))} placeholder="Mã số sinh viên" /><button className="btn btn-secondary" onClick={() => add(group.id)}>Thêm thành viên</button></div>}</article>)}</div>}</section>
  </>
}
