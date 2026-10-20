import { useCallback, useEffect, useState } from 'react'
import { academicApi } from '../api/academic'
import { groupApi } from '../api/groups'
import { milestoneApi } from '../api/milestones'
import { allPages, dateText, errorMessage, studentApi } from '../api/student'
import { PageHeader } from '../components/PageHeader'
import { SelectionPanel } from '../components/SelectionPanel'
import { AnimatedSelect } from '../components/AnimatedSelect'
import { DownloadButton, RemoteState } from '../components/RemoteState'
import { StatusBadge } from '../components/StatusBadge'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import type { MilestoneResponse, ProjectAssignmentResponse, StudentGroupResponse } from '../types/domain'

const milestoneLabels: Record<MilestoneResponse['milestoneType'], string> = {
  PROPOSAL: 'Đề cương',
  PROGRESS: 'Báo cáo tiến độ',
  FINAL: 'Báo cáo cuối kỳ & mã nguồn',
}

export function StudentProgressPage() {
  const { user } = useAuth()
  const { showSuccess, showError } = useToast()
  const [projects, setProjects] = useState<ProjectAssignmentResponse[]>([])
  const [groups, setGroups] = useState<StudentGroupResponse[]>([])
  const [milestones, setMilestones] = useState<MilestoneResponse[]>([])
  const [selected, setSelected] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [uploading, setUploading] = useState(false)
  const [target, setTarget] = useState<MilestoneResponse>()
  const [file, setFile] = useState<File>()
  const [note, setNote] = useState('')
  const load = useCallback(async () => {
    setLoading(true); setError('')
    try {
      const [ps, gs] = await Promise.all([allPages(page=>academicApi.getProjectAssignments(undefined,page,100)),groupApi.mine()])
      const ms = (await Promise.all(ps.map(p=>milestoneApi.listByAssignment(p.id)))).flat()
      setProjects(ps); setGroups(gs); setMilestones(ms)
    } catch(e) { setError(errorMessage(e)) } finally { setLoading(false) }
  },[])
  useEffect(()=>{void load()},[load])
  const canSubmit = (m: MilestoneResponse) => {
    const p = projects.find(p=>p.id===m.assignmentId)
    return p?.status==='IN_PROGRESS' && m.status!=='ACCEPTED' && groups.find(g=>g.id===p.groupId)?.members.some(m=>m.userId===user?.id&&m.memberRole==='LEADER')
  }
  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!target || !file || uploading) return
    if (!file.size || file.size>20*1024*1024) return showError('Chọn tệp có nội dung, tối đa 20 MB.')
    setUploading(true)
    try {
      const uploaded = await studentApi.upload(file)
      await studentApi.submit(target.id,uploaded.id,note)
      showSuccess('Báo cáo đã được lưu trên hệ thống.');setTarget(undefined);setFile(undefined);setNote('');await load()
    } catch(e) { showError(errorMessage(e)) } finally { setUploading(false) }
  }
  const visible = milestones
    .filter(m=>!selected||m.assignmentId===Number(selected))
    .sort((left, right) => new Date(left.dueAt).getTime() - new Date(right.dueAt).getTime())
  return <>
    <PageHeader title="Tiến độ & báo cáo" description="Chỉ trưởng nhóm được nộp báo cáo. Thành viên được xem bài nộp và phản hồi của nhóm."/>
    <RemoteState loading={loading} error={error} retry={load}/>
    {!loading&&!error&&<><SelectionPanel id="student-project" label="Dự án của nhóm" description="Theo dõi mốc báo cáo của các dự án bạn tham gia."><AnimatedSelect id="student-project" value={selected} disabled={!projects.length} onChange={e=>setSelected(e.target.value)}><option value="">{projects.length ? 'Tất cả dự án của tôi' : 'Chưa có dự án được phân công'}</option>{projects.map(p=><option key={p.id} value={p.id}>{p.topicCode} · {p.topicTitle} ({p.groupName})</option>)}</AnimatedSelect></SelectionPanel>{!projects.length&&<section className="page-card"><div className="content-state"><strong>Nhóm của bạn chưa được giao đề tài</strong><p>Báo cáo sẽ xuất hiện sau khi đăng ký được duyệt và giảng viên tạo mốc.</p></div></section>}{projects.length>0&&<p className="text-secondary">{visible.filter(m=>m.status==='ACCEPTED').length}/{visible.length} mốc đã được chấp nhận.</p>}
    {projects.length>0&&!visible.length&&<section className="page-card"><p>Giảng viên chưa tạo mốc báo cáo cho dự án này.</p></section>}
    <div className="student-timeline">{visible.map((m,index)=><section className="page-card student-timeline__item" key={m.id}><span className="student-timeline__marker" aria-hidden="true">{index+1}</span><div className="content-card__header"><div><span className="student-milestone-type">{milestoneLabels[m.milestoneType]}</span><h2>{m.title}</h2><small>{projects.find(p=>p.id===m.assignmentId)?.topicTitle}</small></div><StatusBadge status={m.status}/></div><p className="student-prose">{m.description}</p><p><strong>Hạn nộp:</strong> {dateText(m.dueAt)} {new Date(m.dueAt).getTime()<Date.now()&&m.status!=='ACCEPTED'?'· Đã qua hạn':''}</p>{m.milestoneType==='FINAL'&&<p className="student-notice">Tệp nộp cuối kỳ cần bao gồm báo cáo hoàn chỉnh và mã nguồn sản phẩm (có thể đóng gói thành một tệp ZIP).</p>}{canSubmit(m)?<button className="btn btn-primary" disabled={uploading} onClick={()=>{setTarget(m);setFile(undefined);setNote('')}}>{m.submissions.length?'Nộp phiên bản mới':'Nộp báo cáo'}</button>:<p className="student-notice">{m.status==='ACCEPTED'?'Mốc đã được chấp nhận, không thể nộp lại.':projects.find(p=>p.id===m.assignmentId)?.status!=='IN_PROGRESS'?'Dự án đã đóng, không thể nộp thêm.':'Bạn là thành viên: chỉ trưởng nhóm được nộp báo cáo.'}</p>}
      {m.submissions.map(s=><details key={s.id} className="student-submission"><summary>Phiên bản {s.versionNo} · {dateText(s.submittedAt)} · {s.submittedByName}</summary>{s.file&&<DownloadButton id={s.file.id} name={s.file.originalName}/>}<p className="student-prose">{s.note}</p>{s.feedback.length?s.feedback.map(f=><blockquote key={f.id}><strong>{f.lecturerName}</strong> · {dateText(f.createdAt)}<p className="student-prose">{f.content}</p></blockquote>):<p>Chưa có phản hồi.</p>}</details>)}
    </section>)}</div></>}
    {target&&<div className="student-dialog-backdrop"><section className="student-dialog" role="dialog" aria-modal="true" aria-labelledby="report-title"><h2 id="report-title">Nộp: {target.title}</h2><form className="stack-form" onSubmit={submit}><fieldset disabled={uploading} className="student-fieldset"><label>Tệp báo cáo (tối đa 20 MB)<input type="file" required onChange={e=>setFile(e.target.files?.[0])}/></label><label>Ghi chú<textarea className="form-input" maxLength={5000} value={note} onChange={e=>setNote(e.target.value)}/></label>{new Date(target.dueAt).getTime()<Date.now()&&<p className="student-notice">Đã qua hạn. Hệ thống ghi nhận thời gian nộp thực tế để giảng viên xem xét.</p>}<div className="inline-form"><button className="btn btn-primary" disabled={!file}>{uploading?'Đang tải và lưu...':'Xác nhận nộp'}</button><button className="btn btn-secondary" type="button" onClick={()=>setTarget(undefined)}>Hủy</button></div></fieldset></form></section></div>}
  </>
}
