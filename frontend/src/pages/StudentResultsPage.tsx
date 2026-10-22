import { useCallback, useEffect, useState } from 'react'
import { dateText, errorMessage, studentApi } from '../api/student'
import type { PublishedResult } from '../api/student'
import type { DefenseSessionResponse } from '../types/domain'
import { PageHeader } from '../components/PageHeader'
import { RemoteState } from '../components/RemoteState'
import { StatusBadge } from '../components/StatusBadge'

type CriterionSnapshot = {
  criterionId: number
  name: string
  score: number
  maxScore: number
  comment?: string
}

function parseCriteria(snapshot: string): CriterionSnapshot[] | null {
  try {
    const items: unknown = JSON.parse(snapshot)
    return Array.isArray(items) ? (items as CriterionSnapshot[]) : null
  } catch {
    return null
  }
}

function Criteria({ snapshot }: { snapshot: string }) {
  const items = parseCriteria(snapshot)
  if (!items) return <p>Không đọc được chi tiết tiêu chí. Vui lòng liên hệ khoa.</p>
  return <ul>{items.map((criterion, index)=><li key={criterion.criterionId ?? index}>{criterion.name}: <strong>{criterion.score}/{criterion.maxScore}</strong>{criterion.comment&&<p className="student-prose">{criterion.comment}</p>}</li>)}</ul>
}
export function StudentResultsPage() {
  const [results,setResults] = useState<PublishedResult[]>([])
  const [defenses,setDefenses] = useState<DefenseSessionResponse[]>([])
  const [loading,setLoading] = useState(true)
  const [error,setError] = useState('')
  const load = useCallback(()=>Promise.all([studentApi.results(),studentApi.defenses()])
    .then(([rs,ds])=>{setResults(rs);setDefenses(ds);setError('')})
    .catch(e=>setError(errorMessage(e))).finally(()=>setLoading(false)),[])
  useEffect(()=>{void load()},[load])
  return <><PageHeader title="Lịch bảo vệ & kết quả"/><RemoteState loading={loading} error={error} retry={()=>{setLoading(true);setError('');void load()}}/>{!loading&&!error&&<>
    <section className="page-card"><h2>Lịch bảo vệ</h2>{!defenses.length&&<p>Nhóm của bạn chưa có lịch bảo vệ.</p>}{defenses.map(d=><article key={d.id} className="student-submission"><h3>{d.topicCode} · {d.topicTitle}</h3><p>{d.groupName} · Hội đồng {d.committeeName}</p><p>{dateText(d.startsAt)} – {dateText(d.endsAt)}</p><p>Địa điểm: {d.location} · <StatusBadge status={d.status}/></p></article>)}</section>
    <section className="page-card"><h2>Kết quả đã công bố</h2>{!results.length&&<p>Chưa có kết quả được công bố cho bạn. Điểm chưa công bố sẽ không hiển thị.</p>}{results.map(r=><article className="student-submission" key={r.id}><div className="content-card__header"><div><h3>{r.topicCode} · {r.topicTitle}</h3><p>{r.groupName} · {r.periodName}</p></div><div className="student-final-score"><span>Điểm trung bình cuối cùng</span><strong className="student-score">{Number(r.finalScore).toFixed(2)}<small>/10</small></strong></div></div><p>Công bố {dateText(r.publishedAt)} · Phiên bản {r.versionNo}</p><div className="table-container"><table className="data-table student-score-table"><thead><tr><th>Giảng viên</th><th>Thành phần</th><th>Điểm</th><th>Nhận xét</th><th>Chi tiết</th></tr></thead><tbody>{r.inputs.map((input,i)=><tr key={input.id}><td>{input.lecturerName || `Giảng viên ${i+1}`}</td><td>{input.componentCode}</td><td><strong>{Number(input.scoreSnapshot).toFixed(2)}/10</strong></td><td>{input.overallComment || 'Chưa có nhận xét chung'}</td><td><details><summary>Xem tiêu chí</summary><Criteria snapshot={input.rubricSnapshot}/></details></td></tr>)}</tbody></table></div><p className="text-secondary text-sm">Điểm cuối là trung bình cộng điểm các thành phần; mỗi thành phần lấy trung bình các phiếu chấm tương ứng.</p></article>)}</section>
  </>}</>
}
