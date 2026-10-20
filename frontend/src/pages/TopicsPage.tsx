import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { topicApi } from '../api/topics'
import { academicApi } from '../api/academic'
import { allPages, dateText, errorMessage, isWindowOpen } from '../api/student'
import { apiClient } from '../api/client'
import { PageHeader } from '../components/PageHeader'
import { RemoteState } from '../components/RemoteState'
import { StatusBadge } from '../components/StatusBadge'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import type { RegistrationPeriodResponse, TopicResponse } from '../types/domain'

const emptyForm = { periodId: '', code: '', title: '', description: '', objectives: '', expectedOutcomes: '', prerequisites: '' }
export function TopicsPage() {
  const { user, hasRole } = useAuth()
  const { showSuccess, showError } = useToast()
  const [params] = useSearchParams()
  const [topics, setTopics] = useState<TopicResponse[]>([])
  const [periods, setPeriods] = useState<RegistrationPeriodResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [query, setQuery] = useState(params.get('q') || '')
  const [filter, setFilter] = useState('PUBLISHED')
  const [page, setPage] = useState(0)
  const [selected, setSelected] = useState<number>(Number(params.get('topic')) || 0)
  const [form, setForm] = useState(emptyForm)
  const [editing, setEditing] = useState<TopicResponse>()
  const [showForm, setShowForm] = useState(false)
  const [busy, setBusy] = useState(false)
  const [withdrawId, setWithdrawId] = useState<number>()
  const [reason, setReason] = useState('')
  const student = hasRole('STUDENT')
  const load = useCallback(async () => {
    setLoading(true); setError('')
    try { const [ts, ps] = await Promise.all([allPages(page => topicApi.list({page,size:100})), allPages(page => academicApi.getRegistrationPeriods(undefined,page,100))]); setTopics(ts); setPeriods(ps) }
    catch(e) { setError(errorMessage(e)) } finally { setLoading(false) }
  }, [])
  useEffect(() => { void load() }, [load])
  useEffect(() => { setQuery(params.get('q') || ''); setSelected(Number(params.get('topic')) || 0); setPage(0) }, [params])
  const visible = useMemo(() => topics.filter(t => (filter === 'MINE' ? t.proposedBy === user?.id : filter ? t.status === filter : true) && `${t.code} ${t.title} ${t.departmentName} ${t.supervisors.map(s=>s.lecturerName).join(' ')}`.toLocaleLowerCase('vi').includes(query.toLocaleLowerCase('vi'))), [topics,filter,query,user?.id])
  const detail = topics.find(t => t.id === selected)
  const openPeriods = periods.filter(p => isWindowOpen(p,'proposal'))
  const save = async (e: React.FormEvent) => {
    e.preventDefault(); if (busy) return
    setBusy(true)
    try {
      const payload = { ...form, periodId: Number(form.periodId), visibilityType:'OPEN', reservedGroupId:null }
      if (editing) await apiClient.put(`/topics/${editing.id}`, { ...payload, version:editing.version })
      else await topicApi.create(payload)
      showSuccess(editing ? 'Đã cập nhật đề xuất.' : 'Đã gửi đề xuất để xét duyệt.'); setShowForm(false); setEditing(undefined); setForm(emptyForm); setFilter('MINE'); await load()
    } catch(e) { showError(errorMessage(e)) } finally { setBusy(false) }
  }
  const edit = (t: TopicResponse) => { setEditing(t); setForm({periodId:String(t.periodId),code:t.code,title:t.title,description:t.description,objectives:t.objectives,expectedOutcomes:t.expectedOutcomes,prerequisites:t.prerequisites||''}); setShowForm(true) }
  return <>
    <PageHeader title="Đề tài" description="Tra cứu đề tài đã công bố và theo dõi đề xuất của bạn." />
    {student && <section className="page-card"><div className="content-card__header"><div><h2>Đề xuất ý tưởng</h2><p>Đề xuất cần được duyệt, phân công hướng dẫn và công bố trước khi nhóm đăng ký thực hiện.</p></div>{openPeriods.length > 0 && <button className="btn btn-primary" disabled={loading||busy} onClick={()=>{setForm(emptyForm);setEditing(undefined);setShowForm(true)}}>Đề xuất đề tài</button>}</div>{!openPeriods.length && !loading && <p>Hiện không có đợt nhận đề xuất.</p>}</section>}
    {showForm && <section className="page-card"><h2>{editing ? 'Sửa đề xuất' : 'Đề xuất mới'}</h2><form className="stack-form" onSubmit={save}><fieldset disabled={busy} className="student-fieldset"><label>Đợt đề xuất<select required disabled={!!editing} className="form-select" value={form.periodId} onChange={e=>setForm({...form,periodId:e.target.value})}><option value="">Chọn đợt</option>{openPeriods.map(p=><option key={p.id} value={p.id}>{p.name}</option>)}</select></label><label>Mã đề tài<input className="form-input" disabled={!!editing} required maxLength={30} value={form.code} onChange={e=>setForm({...form,code:e.target.value})}/></label><label>Tên đề tài<input className="form-input" required maxLength={300} value={form.title} onChange={e=>setForm({...form,title:e.target.value})}/></label>{(['description','objectives','expectedOutcomes','prerequisites'] as const).map((key,i)=><label key={key}>{['Mô tả','Mục tiêu','Kết quả mong đợi','Điều kiện tiên quyết (không bắt buộc)'][i]}<textarea className="form-input" required={key!=='prerequisites'} value={form[key]} onChange={e=>setForm({...form,[key]:e.target.value})}/></label>)}<div className="inline-form"><button className="btn btn-primary">{busy?'Đang gửi...':'Lưu đề xuất'}</button><button type="button" className="btn btn-secondary" onClick={()=>setShowForm(false)}>Hủy</button></div></fieldset></form></section>}
    <section className="page-card"><div className="topics-toolbar"><input aria-label="Tìm đề tài" className="form-input" placeholder="Tìm mã, tên, bộ môn, GVHD..." value={query} onChange={e=>{setQuery(e.target.value);setPage(0)}}/><select aria-label="Lọc đề tài" className="form-select" value={filter} onChange={e=>{setFilter(e.target.value);setPage(0)}}><option value="PUBLISHED">Đã công bố</option>{student&&<option value="MINE">Đề xuất của tôi</option>}<option value="">Tất cả được phép xem</option></select></div><RemoteState loading={loading} error={error} retry={load}/>{!loading&&!error&&<><p>{visible.length} đề tài</p><div className="table-container"><table className="data-table"><thead><tr><th>Đề tài</th><th>Bộ môn / GVHD</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>{visible.slice(page*10,page*10+10).map(t=><tr key={t.id}><td><strong>{t.title}</strong><small className="table-subtext">{t.code} · {t.periodName}</small></td><td>{t.departmentName}<small className="table-subtext">{t.supervisors.map(s=>s.lecturerName).join(', ')||'Chưa phân công'}</small></td><td><StatusBadge status={t.status}/></td><td><button className="text-button" onClick={()=>setSelected(t.id)}>Chi tiết</button></td></tr>)}</tbody></table></div>{!visible.length&&<p>Không có đề tài phù hợp.</p>}<div className="table-pagination"><button className="btn btn-secondary" disabled={page===0} onClick={()=>setPage(page-1)}>Trước</button><span>Trang {page+1}/{Math.max(1,Math.ceil(visible.length/10))}</span><button className="btn btn-secondary" disabled={(page+1)*10>=visible.length} onClick={()=>setPage(page+1)}>Sau</button></div></>}</section>
    {detail&&!loading&&!error&&<section className="page-card" aria-label="Chi tiết đề tài"><div className="content-card__header"><h2>{detail.title}</h2><button className="btn btn-secondary" onClick={()=>setSelected(0)}>Đóng chi tiết</button></div><p>{detail.code} · {detail.periodName} · <StatusBadge status={detail.status}/></p>{[['Mô tả',detail.description],['Mục tiêu',detail.objectives],['Kết quả mong đợi',detail.expectedOutcomes],['Điều kiện',detail.prerequisites||'Không có']].map(([label,text])=><div key={label}><h3>{label}</h3><p className="student-prose">{text}</p></div>)}{detail.proposedBy===user?.id&&detail.latestReview&&<p className="student-notice">Phản hồi: {detail.latestReview.comment}</p>}<p>Hạn đăng ký: {dateText(periods.find(p=>p.id===detail.periodId)?.registrationEndAt)}</p><div className="inline-form">{student&&detail.availableForRegistration&&detail.status==='PUBLISHED'&&isWindowOpen(periods.find(p=>p.id===detail.periodId),'registration')&&<Link className="btn btn-primary" to={`/registrations?topic=${detail.id}`}>Đăng ký theo nhóm</Link>}{student&&detail.proposedBy===user?.id&&['DRAFT','PENDING','REVISION_REQUIRED'].includes(detail.status)&&isWindowOpen(periods.find(p=>p.id===detail.periodId),'proposal')&&<button className="btn btn-secondary" onClick={()=>edit(detail)}>Sửa đề xuất</button>}{student&&detail.proposedBy===user?.id&&!['PUBLISHED','WITHDRAWN'].includes(detail.status)&&<button className="btn btn-secondary" onClick={()=>{setWithdrawId(detail.id);setReason('')}}>Rút đề xuất</button>}</div></section>}
    {withdrawId&&<section className="page-card"><form onSubmit={async e=>{e.preventDefault();if(busy)return;setBusy(true);try{const t=topics.find(t=>t.id===withdrawId)!;await apiClient.patch(`/topics/${t.id}/withdraw`,{version:t.version,reason});setWithdrawId(undefined);showSuccess('Đã rút đề xuất.');await load()}catch(e){showError(errorMessage(e))}finally{setBusy(false)}}}><label>Lý do rút đề xuất<textarea className="form-input" required value={reason} onChange={e=>setReason(e.target.value)}/></label><button className="btn btn-primary" disabled={busy||!reason.trim()}>Xác nhận rút</button> <button type="button" className="btn btn-secondary" disabled={busy} onClick={()=>setWithdrawId(undefined)}>Hủy</button></form></section>}
  </>
}
