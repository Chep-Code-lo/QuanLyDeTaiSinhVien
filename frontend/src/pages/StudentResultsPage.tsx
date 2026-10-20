import { useCallback, useEffect, useState } from 'react'
import { evaluationApi } from '../api/evaluation'
import { allPages, dateText, errorMessage, studentApi } from '../api/student'
import type { PublishedResult } from '../api/student'
import type { DefenseSessionResponse } from '../types/domain'
import { PageHeader } from '../components/PageHeader'
import { RemoteState } from '../components/RemoteState'
import { StatusBadge } from '../components/StatusBadge'

function Criteria({ snapshot }: { snapshot: string }) {
  try {
    const items: { criterionId: number; name: string; score: number; maxScore: number; comment?: string }[] = JSON.parse(snapshot)
    if (!Array.isArray(items)) return null
    return <ul>{items.map((c,i)=><li key={c.criterionId??i}>{c.name}: <strong>{c.score}/{c.maxScore}</strong>{c.comment&&<p className="student-prose">{c.comment}</p>}</li>)}</ul>
  } catch { return <p>Không đọc được chi tiết tiêu chí. Vui lòng liên hệ khoa.</p> }
}
export function StudentResultsPage() {
  const [results,setResults] = useState<PublishedResult[]>([])
  const [defenses,setDefenses] = useState<DefenseSessionResponse[]>([])
  const [loading,setLoading] = useState(true)
  const [error,setError] = useState('')
  const load = useCallback(async()=>{
    setLoading(true);setError('')
    try { const [rs,ds]=await Promise.all([studentApi.results(),allPages(page=>evaluationApi.listDefenses({page,size:100}))]);setResults(rs);setDefenses(ds) }
    catch(e){setError(errorMessage(e))}finally{setLoading(false)}
  },[])
  useEffect(()=>{void load()},[load])
  return <><PageHeader title="Lịch bảo vệ & kết quả" description="Chỉ hiển thị lịch của nhóm và kết quả đã được khoa công bố cho bạn."/><RemoteState loading={loading} error={error} retry={load}/>{!loading&&!error&&<>
    <section className="page-card"><h2>Lịch bảo vệ</h2>{!defenses.length&&<p>Nhóm của bạn chưa có lịch bảo vệ.</p>}{defenses.map(d=><article key={d.id} className="student-submission"><h3>{d.topicCode} · {d.topicTitle}</h3><p>{d.groupName} · Hội đồng {d.committeeName}</p><p>{dateText(d.startsAt)} – {dateText(d.endsAt)}</p><p>Địa điểm: {d.location} · <StatusBadge status={d.status}/></p></article>)}</section>
    <section className="page-card"><h2>Kết quả đã công bố</h2>{!results.length&&<p>Chưa có kết quả được công bố cho bạn. Điểm chưa công bố sẽ không hiển thị.</p>}{results.map(r=><article className="student-submission" key={r.id}><div className="content-card__header"><div><h3>{r.topicCode} · {r.topicTitle}</h3><p>{r.groupName} · {r.periodName}</p></div><strong className="student-score">{Number(r.finalScore).toFixed(2)}<small>/10</small></strong></div><p>Công bố {dateText(r.publishedAt)} · Phiên bản {r.versionNo}</p><details><summary>Điểm thành phần và nhận xét</summary>{r.inputs.map((input,i)=><section key={input.id}><h4>{input.componentCode} · Phiếu {i+1}: {Number(input.scoreSnapshot).toFixed(2)}/10</h4><Criteria snapshot={input.rubricSnapshot}/></section>)}<p>Điểm cuối là trung bình cộng điểm các thành phần; mỗi thành phần lấy trung bình các phiếu chấm tương ứng.</p></details></article>)}</section>
  </>}</>
}
