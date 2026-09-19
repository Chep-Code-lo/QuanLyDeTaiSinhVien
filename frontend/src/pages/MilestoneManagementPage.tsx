import React, { useEffect, useState } from 'react'
import { academicApi } from '../api/academic'
import { milestoneApi } from '../api/milestones'
import { StatusBadge } from '../components/StatusBadge'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import type {
  CreateMilestoneRequest,
  FeedbackResponse,
  MilestoneResponse,
  MilestoneType,
  ProjectAssignmentResponse,
  SubmissionResponse,
} from '../types/domain'

export const MilestoneManagementPage: React.FC = () => {
  const { hasAnyRole } = useAuth()
  const { showSuccess, showError } = useToast()

  const [assignments, setAssignments] = useState<ProjectAssignmentResponse[]>([])
  const [selectedAssignmentId, setSelectedAssignmentId] = useState<number | null>(null)
  const [milestones, setMilestones] = useState<MilestoneResponse[]>([])
  const [loadingAssignments, setLoadingAssignments] = useState(false)
  const [loadingMilestones, setLoadingMilestones] = useState(false)

  // Create Milestone Modal State
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false)
  const [submittingMilestone, setSubmittingMilestone] = useState(false)
  const [newMilestone, setNewMilestone] = useState<{
    title: string
    milestoneType: MilestoneType
    description: string
    dueAt: string
  }>({
    title: '',
    milestoneType: 'PROGRESS',
    description: '',
    dueAt: '',
  })

  // Feedback Modal State
  const [selectedSubmission, setSelectedSubmission] = useState<SubmissionResponse | null>(null)
  const [isFeedbackModalOpen, setIsFeedbackModalOpen] = useState(false)
  const [feedbackContent, setFeedbackContent] = useState('')
  const [feedbackAccepted, setFeedbackAccepted] = useState(true)
  const [submittingFeedback, setSubmittingFeedback] = useState(false)

  // Load project assignments
  useEffect(() => {
    setLoadingAssignments(true)
    academicApi
      .getProjectAssignments()
      .then((res) => {
        setAssignments(res.content)
        if (res.content.length > 0) {
          setSelectedAssignmentId(res.content[0].id)
        }
      })
      .catch((err) => {
        showError('Không thể tải danh sách dự án: ' + (err.response?.data?.message || err.message))
      })
      .finally(() => setLoadingAssignments(false))
  }, [showError])

  // Load milestones when project assignment selection changes
  useEffect(() => {
    if (!selectedAssignmentId) {
      setMilestones([])
      return
    }
    setLoadingMilestones(true)
    milestoneApi
      .listByAssignment(selectedAssignmentId)
      .then((res) => {
        setMilestones(res)
      })
      .catch((err) => {
        showError('Không thể tải danh sách milestone: ' + (err.response?.data?.message || err.message))
      })
      .finally(() => setLoadingMilestones(false))
  }, [selectedAssignmentId, showError])

  const handleCreateMilestone = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedAssignmentId) {
      showError('Vui lòng chọn một đề tài/dự án')
      return
    }
    if (!newMilestone.title.trim() || !newMilestone.description.trim() || !newMilestone.dueAt) {
      showError('Vui lòng điền đầy đủ các thông tin bắt buộc')
      return
    }

    try {
      setSubmittingMilestone(true)
      const payload: CreateMilestoneRequest = {
        assignmentId: selectedAssignmentId,
        title: newMilestone.title.trim(),
        milestoneType: newMilestone.milestoneType,
        description: newMilestone.description.trim(),
        dueAt: new Date(newMilestone.dueAt).toISOString(),
      }
      const created = await milestoneApi.create(payload)
      showSuccess('Tạo milestone thành công!')
      setMilestones((prev: MilestoneResponse[]) => [...prev, created])
      setIsCreateModalOpen(false)
      setNewMilestone({ title: '', milestoneType: 'PROGRESS', description: '', dueAt: '' })
    } catch (err: any) {
      showError('Lỗi tạo milestone: ' + (err.response?.data?.message || err.message))
    } finally {
      setSubmittingMilestone(false)
    }
  }

  const handleOpenFeedback = (submission: SubmissionResponse) => {
    setSelectedSubmission(submission)
    setFeedbackContent('')
    setFeedbackAccepted(true)
    setIsFeedbackModalOpen(true)
  }

  const handleSubmitFeedback = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedSubmission) return
    if (!feedbackContent.trim()) {
      showError('Vui lòng nhập nội dung nhận xét / feedback')
      return
    }

    try {
      setSubmittingFeedback(true)
      const updatedSubmission = await milestoneApi.submitFeedback(selectedSubmission.id, {
        content: feedbackContent.trim(),
        accepted: feedbackAccepted,
      })
      showSuccess('Gửi feedback thành công!')

      // Update local state with new feedback
      setMilestones((prev: MilestoneResponse[]) =>
        prev.map((m: MilestoneResponse) => ({
          ...m,
          submissions: m.submissions.map((sub: SubmissionResponse) =>
            sub.id === updatedSubmission.id ? updatedSubmission : sub
          ),
        }))
      )
      setIsFeedbackModalOpen(false)
      setSelectedSubmission(null)
    } catch (err: any) {
      showError('Lỗi gửi feedback: ' + (err.response?.data?.message || err.message))
    } finally {
      setSubmittingFeedback(false)
    }
  }

  const isLecturerOrAdmin = hasAnyRole(['LECTURER', 'ADMIN'])

  return (
    <div className="milestone-page">
      <div className="page-header">
        <div>
          <span className="page-header__eyebrow">Quản lý tiến độ</span>
          <h1>Milestones & Báo cáo đề tài</h1>
          <p>Theo dõi các mốc tiến độ, nộp báo cáo và phản hồi feedback từ Giảng viên.</p>
        </div>
        {isLecturerOrAdmin && selectedAssignmentId && (
          <button
            type="button"
            className="btn btn-primary"
            onClick={() => setIsCreateModalOpen(true)}
          >
            + Tạo Milestone mới
          </button>
        )}
      </div>

      {/* Select Assignment Selector */}
      <div className="page-card mb-6">
        <div className="form-group mb-0">
          <label className="form-label">Chọn Đề tài / Dự án Phân công:</label>
          <select
            className="form-select font-semibold"
            value={selectedAssignmentId ?? ''}
            onChange={(e) => setSelectedAssignmentId(Number(e.target.value) || null)}
            disabled={loadingAssignments}
          >
            {assignments.length === 0 ? (
              <option value="">-- Không có đề tài nào --</option>
            ) : (
              assignments.map((a: ProjectAssignmentResponse) => (
                <option key={a.id} value={a.id}>
                  [{a.topicCode}] {a.topicTitle} - Nhóm: {a.groupName} ({a.periodName})
                </option>
              ))
            )}
          </select>
        </div>
      </div>

      {/* Milestone List & Timeline View */}
      {loadingMilestones ? (
        <div className="page-card text-center py-12">
          <div className="spinner" />
          <p className="text-secondary mt-2">Đang tải danh sách milestone...</p>
        </div>
      ) : milestones.length === 0 ? (
        <div className="page-card text-center py-12">
          <h3 className="font-semibold text-lg">Chưa có milestone nào được tạo</h3>
          <p className="text-secondary mt-1">
            {isLecturerOrAdmin
              ? 'Bấm nút "Tạo Milestone mới" ở trên để thiết lập mốc tiến độ cho đề tài này.'
              : 'Giảng viên chưa thiết lập milestone tiến độ cho đề tài này.'}
          </p>
        </div>
      ) : (
        <div className="timeline-container page-card">
          <h2 className="font-semibold text-lg mb-4">Danh sách Tiến độ Milestone ({milestones.length})</h2>
          <div className="timeline">
            {milestones.map((m: MilestoneResponse) => (
              <div key={m.id} className="timeline-item page-card mb-4">
                <div className="timeline-dot" />
                <div className="flex justify-between items-start">
                  <div>
                    <div className="flex items-center gap-2">
                      <h3 className="font-bold text-primary text-base">{m.title}</h3>
                      <span className="badge badge-info">{m.milestoneType}</span>
                      <StatusBadge status={m.status} />
                    </div>
                    <p className="text-sm text-secondary mt-1">{m.description}</p>
                    <div className="text-xs text-secondary mt-2">
                      <strong>Hạn nộp (Deadline):</strong>{' '}
                      {new Date(m.dueAt).toLocaleString('vi-VN')}
                    </div>
                  </div>
                </div>

                {/* Submissions Section */}
                <div className="mt-4 pt-3 border-t border-gray-200">
                  <h4 className="font-semibold text-sm mb-2">
                    Báo cáo đã nộp ({m.submissions?.length || 0})
                  </h4>
                  {(!m.submissions || m.submissions.length === 0) ? (
                    <p className="text-xs text-secondary italic">Chưa có bản nộp báo cáo nào từ sinh viên.</p>
                  ) : (
                    <div className="space-y-3">
                      {m.submissions.map((sub: SubmissionResponse) => (
                        <div key={sub.id} className="p-3 bg-gray-50 rounded-lg border border-gray-100">
                          <div className="flex justify-between items-start">
                            <div>
                              <span className="font-semibold text-sm">
                                Phiên bản #{sub.versionNo} - Nộp bởi: {sub.submittedByName}
                              </span>
                              <span className="text-xs text-secondary block mt-1">
                                Thời gian nộp: {new Date(sub.submittedAt).toLocaleString('vi-VN')}
                              </span>
                              {sub.note && (
                                <p className="text-xs text-gray-700 mt-1 italic">Ghi chú: "{sub.note}"</p>
                              )}
                              {sub.file && (
                                <div className="mt-2 text-xs">
                                  📄 Tệp đính kèm: <strong>{sub.file.originalName}</strong> (
                                  {(sub.file.sizeBytes / 1024).toFixed(1)} KB)
                                </div>
                              )}
                            </div>

                            {isLecturerOrAdmin && (
                              <button
                                type="button"
                                className="btn btn-outline btn-sm"
                                onClick={() => handleOpenFeedback(sub)}
                              >
                                💬 Nhập Feedback
                              </button>
                            )}
                          </div>

                          {/* Existing Feedbacks list */}
                          {sub.feedback && sub.feedback.length > 0 && (
                            <div className="mt-3 pt-2 border-t border-gray-200">
                              <span className="text-xs font-semibold text-primary">Lịch sử Feedback từ Giảng viên:</span>
                              <div className="mt-1 space-y-1">
                                {sub.feedback.map((fb: FeedbackResponse) => (
                                  <div key={fb.id} className="text-xs p-2 bg-blue-50 text-blue-900 rounded">
                                    <strong>{fb.lecturerName}:</strong> {fb.content}
                                    <span className="block text-[10px] text-blue-600 mt-0.5">
                                      {new Date(fb.createdAt).toLocaleString('vi-VN')}
                                    </span>
                                  </div>
                                ))}
                              </div>
                            </div>
                          )}
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Modal Create Milestone */}
      {isCreateModalOpen && (
        <div className="modal-backdrop fade-in" onClick={() => setIsCreateModalOpen(false)}>
          <div className="modal-content scale-in" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3 className="font-semibold text-lg">Tạo Milestone Mới</h3>
              <button
                type="button"
                className="btn-close"
                onClick={() => setIsCreateModalOpen(false)}
              >
                &times;
              </button>
            </div>
            <form onSubmit={handleCreateMilestone}>
              <div className="modal-body py-4 space-y-4">
                <div className="form-group">
                  <label className="form-label">Têu đề Milestone <span className="text-red">*</span></label>
                  <input
                    type="text"
                    className="form-input"
                    placeholder="Ví dụ: Báo cáo giữa kỳ..."
                    value={newMilestone.title}
                    onChange={(e) => setNewMilestone({ ...newMilestone, title: e.target.value })}
                    required
                  />
                </div>

                <div className="form-group">
                  <label className="form-label">Loại Milestone <span className="text-red">*</span></label>
                  <select
                    className="form-select"
                    value={newMilestone.milestoneType}
                    onChange={(e) =>
                      setNewMilestone({
                        ...newMilestone,
                        milestoneType: e.target.value as MilestoneType,
                      })
                    }
                  >
                    <option value="PROPOSAL">PROPOSAL (Đề cương)</option>
                    <option value="PROGRESS">PROGRESS (Tiến độ)</option>
                    <option value="FINAL">FINAL (Báo cáo tổng kết)</option>
                  </select>
                </div>

                <div className="form-group">
                  <label className="form-label">Mô tả yêu cầu <span className="text-red">*</span></label>
                  <textarea
                    className="form-textarea"
                    rows={3}
                    placeholder="Mô tả chi tiết nội dung sinh viên cần nộp..."
                    value={newMilestone.description}
                    onChange={(e) => setNewMilestone({ ...newMilestone, description: e.target.value })}
                    required
                  />
                </div>

                <div className="form-group">
                  <label className="form-label">Hạn nộp (Due Date) <span className="text-red">*</span></label>
                  <input
                    type="datetime-local"
                    className="form-input"
                    value={newMilestone.dueAt}
                    onChange={(e) => setNewMilestone({ ...newMilestone, dueAt: e.target.value })}
                    required
                  />
                </div>
              </div>

              <div className="modal-footer flex gap-3 justify-end">
                <button
                  type="button"
                  className="btn btn-secondary"
                  onClick={() => setIsCreateModalOpen(false)}
                  disabled={submittingMilestone}
                >
                  Hủy
                </button>
                <button type="submit" className="btn btn-primary" disabled={submittingMilestone}>
                  {submittingMilestone ? 'Đang tạo...' : 'Tạo Milestone'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal Feedback Report Submission */}
      {isFeedbackModalOpen && selectedSubmission && (
        <div className="modal-backdrop fade-in" onClick={() => setIsFeedbackModalOpen(false)}>
          <div className="modal-content scale-in" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3 className="font-semibold text-lg">Gửi Feedback Báo Cáo</h3>
              <button
                type="button"
                className="btn-close"
                onClick={() => setIsFeedbackModalOpen(false)}
              >
                &times;
              </button>
            </div>
            <form onSubmit={handleSubmitFeedback}>
              <div className="modal-body py-4 space-y-4">
                <div className="p-3 bg-blue-50 text-blue-900 rounded-lg text-sm">
                  <span className="font-semibold block">
                    Bản nộp #{selectedSubmission.versionNo} - Sinh viên: {selectedSubmission.submittedByName}
                  </span>
                  {selectedSubmission.note && (
                    <span className="block text-xs mt-1">Ghi chú: "{selectedSubmission.note}"</span>
                  )}
                </div>

                <div className="form-group">
                  <label className="form-label">Nội dung Nhận xét / Feedback <span className="text-red">*</span></label>
                  <textarea
                    className="form-textarea"
                    rows={4}
                    placeholder="Nhập ý kiến đánh giá, yêu cầu chỉnh sửa hoặc hướng dẫn sinh viên..."
                    value={feedbackContent}
                    onChange={(e) => setFeedbackContent(e.target.value)}
                    required
                  />
                </div>

                <div className="form-group">
                  <label className="form-label">Kết quả đánh giá <span className="text-red">*</span></label>
                  <select
                    className="form-select"
                    value={feedbackAccepted ? 'true' : 'false'}
                    onChange={(e) => setFeedbackAccepted(e.target.value === 'true')}
                  >
                    <option value="true">Chấp nhận (ACCEPTED)</option>
                    <option value="false">Yêu cầu chỉnh sửa lại (REVISION REQUIRED)</option>
                  </select>
                </div>
              </div>

              <div className="modal-footer flex gap-3 justify-end">
                <button
                  type="button"
                  className="btn btn-secondary"
                  onClick={() => setIsFeedbackModalOpen(false)}
                  disabled={submittingFeedback}
                >
                  Hủy
                </button>
                <button type="submit" className="btn btn-primary" disabled={submittingFeedback}>
                  {submittingFeedback ? 'Đang gửi...' : 'Gửi Feedback'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
