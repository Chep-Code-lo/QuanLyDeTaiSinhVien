import { useCallback, useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { groupApi } from '../api/groups'
import { registrationApi } from '../api/registrations'
import { topicApi } from '../api/topics'
import { academicApi } from '../api/academic'
import { allPages, dateText, errorMessage, isWindowOpen } from '../api/student'
import { PageHeader } from '../components/PageHeader'
import { RemoteState } from '../components/RemoteState'
import { StatusBadge } from '../components/StatusBadge'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import type { ProjectAssignmentResponse, RegistrationPeriodResponse, StudentGroupResponse, TopicRegistrationResponse, TopicResponse } from '../types/domain'

export function RegistrationsPage() {
  const { user, hasRole, hasAnyRole } = useAuth()
  const { showSuccess, showError } = useToast()
  const [params] = useSearchParams()
  const student=hasRole('STUDENT'), reviewer=hasAnyRole(['LECTURER','ADMIN'])
  const [items,setItems]=useState<TopicRegistrationResponse[]>([])
  const [groups,setGroups]=useState<StudentGroupResponse[]>([])
  const [topics,setTopics]=useState<TopicResponse[]>([])
  const [periods,setPeriods]=useState<RegistrationPeriodResponse[]>([])
  const [projects,setProjects]=useState<ProjectAssignmentResponse[]>([])
  const [groupId,setGroupId]=useState(''), [topicId,setTopicId]=useState(params.get('topic')||''), [motivation,setMotivation]=useState('')
  const [loading,setLoading]=useState(true), [error,setError]=useState(''), [busy,setBusy]=useState(false)
  const [action,setAction]=useState<{item:TopicRegistrationResponse;kind:'withdraw'|'APPROVED'|'REJECTED'}>()
  const [reason,setReason]=useState('')
  const load=useCallback(async()=>{
    setLoading(true);setError('')
    try {
      const [rs,gs,ts,ps,as]=await Promise.all([allPages(page=>registrationApi.list({page,size:100})),student?groupApi.mine():Promise.resolve([]),allPages(page=>topicApi.list({status:'PUBLISHED',page,size:100})),allPages(page=>academicApi.getRegistrationPeriods(undefined,page,100)),allPages(page=>academicApi.getProjectAssignments(undefined,page,100))]);setItems(rs);setGroups(gs);setTopics(ts);setPeriods(ps);setProjects(as)
    }catch(e){setError(errorMessage(e))}finally{setLoading(false)}
  },[student])
  useEffect(()=>{void load()},[load])
  const leader=(id:number)=>groups.find(g=>g.id===id)?.members.some(m=>m.userId===user?.id&&m.memberRole==='LEADER')
  const eligible=groups.filter(g=>leader(g.id)&&g.status==='ACTIVE'&&g.members.length<=3&&isWindowOpen(periods.find(p=>p.id===g.periodId),'registration')&&!items.some(r=>r.groupId===g.id&&['PENDING','APPROVED'].includes(r.status))&&!projects.some(p=>p.groupId===g.id))
  const selectedGroup=eligible.find(g=>g.id===Number(groupId))
  const available=topics.filter(t=>t.availableForRegistration&&selectedGroup&&t.periodId===selectedGroup.periodId&&(t.visibilityType==='OPEN'||t.reservedGroupId===selectedGroup.id))
  const register=async(e:React.FormEvent)=>{
    e.preventDefault();if(busy)return
    if(!selectedGroup||!available.some(t=>t.id===Number(topicId)))return showError('Chọn nhóm đủ điều kiện và đề tài trong cùng đợt.')
    setBusy(true)
    try{await registrationApi.create({groupId:Number(groupId),topicId:Number(topicId),motivation:motivation.trim()||undefined});showSuccess('Đã gửi đăng ký. Nhóm được khóa thay đổi thành viên trong khi chờ duyệt.');setGroupId('');setTopicId('');setMotivation('');await load()}catch(e){showError(errorMessage(e))}finally{setBusy(false)}
  }
  const confirm=async(e:React.FormEvent)=>{
    e.preventDefault();if(!action||busy)return;setBusy(true)
    try{if(action.kind==='withdraw')await registrationApi.withdraw(action.item.id,{version:action.item.version,decisionReason:reason.trim()});else await registrationApi.review(action.item.id,{version:action.item.version,status:action.kind,decisionReason:reason.trim()||undefined});showSuccess('Đã cập nhật đăng ký.');setAction(undefined);await load()}catch(e){showError(errorMessage(e))}finally{setBusy(false)}
  }
  return <><PageHeader title="Đăng ký đề tài" description="Nhóm trưởng đăng ký một đề tài đã công bố. Thành viên chỉ xem hồ sơ của nhóm mình."/><RemoteState loading={loading} error={error} retry={load}/>{!loading&&!error&&<>
    {student&&<section className="page-card"><h2>Gửi đăng ký</h2>{eligible.length?<form className="stack-form" onSubmit={register}><fieldset className="student-fieldset" disabled={busy}><label>Nhóm đủ điều kiện<select className="form-select" value={groupId} required onChange={e=>{setGroupId(e.target.value);setTopicId('')}}><option value="">Chọn nhóm bạn làm trưởng nhóm</option>{eligible.map(g=><option key={g.id} value={g.id}>{g.name} · {g.periodName}</option>)}</select></label><label>Đề tài cùng đợt<select className="form-select" required value={topicId} onChange={e=>setTopicId(e.target.value)}><option value="">Chọn đề tài</option>{available.map(t=><option key={t.id} value={t.id}>{t.code} · {t.title}</option>)}</select></label>{selectedGroup&&!available.length&&<p>Không có đề tài đã công bố phù hợp với nhóm trong đợt này.</p>}<label>Lý do / kinh nghiệm phù hợp<textarea className="form-input" value={motivation} maxLength={5000} onChange={e=>setMotivation(e.target.value)}/></label><button className="btn btn-primary" disabled={!selectedGroup||!available.some(t=>t.id===Number(topicId))}>{busy?'Đang gửi...':'Gửi đăng ký'}</button></fieldset></form>:<p className="student-notice">Bạn chưa có nhóm đủ điều kiện để gửi đăng ký. Bạn cần là trưởng nhóm đang hoạt động, trong thời gian đăng ký, chưa có hồ sơ chờ duyệt/đã duyệt hoặc đề tài được giao. Mỗi nhóm tối đa 3 thành viên.</p>}</section>}
    <section className="page-card"><h2>{student?'Hồ sơ của nhóm tôi':'Hồ sơ đăng ký'}</h2>{!items.length?<p>Chưa có hồ sơ đăng ký.</p>:<div className="table-container"><table className="data-table"><thead><tr><th>Nhóm / Đề tài</th><th>Trạng thái</th><th>Phản hồi</th><th>Thao tác</th></tr></thead><tbody>{items.map(r=><tr key={r.id}><td><strong>{r.topicCode} · {r.topicTitle}</strong><small className="table-subtext">{r.groupName} · {dateText(r.registeredAt)}</small>{r.motivation&&<small className="table-subtext">{r.motivation}</small>}</td><td><StatusBadge status={r.status}/></td><td>{r.decisionReason||'Chưa có phản hồi'}</td><td>{r.status==='PENDING'&&student&&leader(r.groupId)&&<button className="btn btn-secondary" disabled={busy} onClick={()=>{setAction({item:r,kind:'withdraw'});setReason('')}}>Rút đăng ký</button>}{r.status==='PENDING'&&reviewer&&(['APPROVED','REJECTED'] as const).map(kind=><button className="btn btn-secondary" key={kind} disabled={busy} onClick={()=>{setAction({item:r,kind});setReason('')}}>{kind==='APPROVED'?'Duyệt':'Từ chối'}</button>)}</td></tr>)}</tbody></table></div>}</section></>}
    {action&&<section className="page-card"><h2>{action.kind==='withdraw'?'Rút đăng ký':action.kind==='APPROVED'?'Duyệt đăng ký':'Từ chối đăng ký'}: {action.item.topicTitle}</h2><form className="stack-form" onSubmit={confirm}><label>Lý do<textarea className="form-input" required={action.kind!=='APPROVED'} value={reason} onChange={e=>setReason(e.target.value)}/></label><div className="inline-form"><button className="btn btn-primary" disabled={busy||(action.kind!=='APPROVED'&&!reason.trim())}>Xác nhận</button><button type="button" className="btn btn-secondary" disabled={busy} onClick={()=>setAction(undefined)}>Hủy</button></div></form></section>}
  </>
}
