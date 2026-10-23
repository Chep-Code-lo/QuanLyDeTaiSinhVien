import { parseUtcDateTime, toLocalDateTimeInput } from '../utils/dateTime'
import { DateTimeInput } from '../components/DateTimeInput'
import { AnimatedSelect } from '../components/AnimatedSelect'
import React, { useCallback, useEffect, useState } from 'react'
import { useRemoteData } from '../hooks/useRemoteData'
import { topicApi } from '../api/topics'
import { canManageDepartment, hasAcademicLeadership } from '../utils/managementAccess'
import { academicApi } from '../api/academic'
import { allPages } from '../api/student'
import { milestoneApi } from '../api/milestones'
import { MilestoneBoard } from '../components/MilestoneBoard'
import { ConfirmModal } from '../components/ConfirmModal'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import { useRequestLoading } from '../hooks/useRequestLoading'
import './MilestoneManagementPage.css'
import type {
  MilestoneResponse,
  MilestoneType,
  ProjectAssignmentResponse,
  SubmissionResponse,
} from '../types/domain'

export const MilestoneManagementPage: React.FC = () => {
  const { user } = useAuth()
  const { showSuccess, showError } = useToast()

  const [assignments, setAssignments] = useState<ProjectAssignmentResponse[]>([])
  const [selectedAssignmentId, setSelectedAssignmentId] = useState<number | null>(null)
  const [milestones, setMilestones] = useState<MilestoneResponse[]>([])
  const [loadingAssignments, setLoadingAssignments] = useState(true)
  const [milestoneError, setMilestoneError] = useState<string | null>(null)
  const [milestoneReload, setMilestoneReload] = useState(0)
  const [loadingMilestones, setLoadingMilestones] = useRequestLoading(
    JSON.stringify([selectedAssignmentId, milestoneReload]),
    !!selectedAssignmentId,
  )

  // Create Milestone Modal State
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false)
  const [submittingMilestone, setSubmittingMilestone] = useState(false)
  const [editingMilestone, setEditingMilestone] = useState<MilestoneResponse | null>(null)
  const [reminderOpen, setReminderOpen] = useState(false)
  const [reminding, setReminding] = useState(false)
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
    let active = true
    allPages((page) => academicApi.getProjectAssignments(undefined, page, 100))
      .then((res) => {
        if (!active) return
        setAssignments(res)
        if (res.length > 0) {
          setSelectedAssignmentId(res[0].id)
        }
      })
      .catch((err) => {
        if (!active) return
        showError('Không thể tải danh sách dự án: ' + (err.response?.data?.message || err.message))
      })
      .finally(() => {
        if (active) setLoadingAssignments(false)
      })
    return () => {
      active = false
    }
  }, [showError])

  // Load milestones when project assignment selection changes
  useEffect(() => {
    let active = true
    if (!selectedAssignmentId) return
    milestoneApi
      .listByAssignment(selectedAssignmentId)
      .then((res) => {
        if (active) {
          setMilestones(res)
          setMilestoneError(null)
        }
      })
      .catch((err) => {
        if (!active) return
        const detail = err.response?.data?.message || err.message || 'Vui lòng thử lại.'
        setMilestoneError(detail)
        showError('Không thể tải danh sách mốc tiến độ: ' + detail)
      })
      .finally(() => {
        if (active) setLoadingMilestones(false)
      })
    return () => {
      active = false
    }
  }, [selectedAssignmentId, showError, milestoneReload, setLoadingMilestones])

  const handleCreateMilestone = async (e: React.FormEvent) => {
    e.preventDefault()
    if (submittingMilestone) return
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
      const dueAt =
        editingMilestone &&
        newMilestone.dueAt === toLocalDateTimeInput(parseUtcDateTime(editingMilestone.dueAt))
          ? parseUtcDateTime(editingMilestone.dueAt).toISOString()
          : new Date(newMilestone.dueAt).toISOString()
      const milestoneDetails = {
        title: newMilestone.title.trim(),
        milestoneType: newMilestone.milestoneType,
        description: newMilestone.description.trim(),
        dueAt,
      }
      const saved = editingMilestone
        ? await milestoneApi.update(editingMilestone.id, {
            version: editingMilestone.version,
            ...milestoneDetails,
          })
        : await milestoneApi.create({ assignmentId: selectedAssignmentId, ...milestoneDetails })
      showSuccess(editingMilestone ? 'Đã cập nhật mốc báo cáo.' : 'Tạo mốc tiến độ thành công!')
      setMilestones((prev) =>
        editingMilestone
          ? prev.map((milestone) => (milestone.id === saved.id ? saved : milestone))
          : [...prev, saved],
      )
      setIsCreateModalOpen(false)
      setEditingMilestone(null)
      setNewMilestone({ title: '', milestoneType: 'PROGRESS', description: '', dueAt: '' })
    } catch (err: any) {
      showError(
        (editingMilestone ? 'Lỗi cập nhật mốc báo cáo: ' : 'Lỗi tạo mốc tiến độ: ') +
          (err.response?.data?.message || err.message),
      )
    } finally {
      setSubmittingMilestone(false)
    }
  }

  const handleEditMilestone = (milestone: MilestoneResponse) => {
    setEditingMilestone(milestone)
    setNewMilestone({
      title: milestone.title,
      milestoneType: milestone.milestoneType,
      description: milestone.description,
      dueAt: toLocalDateTimeInput(parseUtcDateTime(milestone.dueAt)),
    })
    setIsCreateModalOpen(true)
  }

  const handleRemind = async () => {
    if (!selectedAssignmentId || reminding) return
    setReminding(true)
    try {
      const result = await milestoneApi.remind(selectedAssignmentId)
      showSuccess(
        result.milestoneCount === 0
          ? 'Không có mốc báo cáo sắp tới chưa nộp cần nhắc hạn.'
          : result.recipientCount === 0
            ? 'Nhóm chưa có thành viên đang hoạt động để nhận nhắc hạn.'
            : `Đã gửi nhắc hạn ${result.milestoneCount} mốc báo cáo cho ${result.recipientCount} sinh viên.`,
      )
      setReminderOpen(false)
    } catch (error: any) {
      showError(error.response?.data?.message || 'Không thể gửi nhắc hạn. Vui lòng thử lại.')
    } finally {
      setReminding(false)
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
    if (!selectedSubmission || submittingFeedback) return
    if (!feedbackContent.trim()) {
      showError('Vui lòng nhập nội dung nhận xét')
      return
    }

    try {
      setSubmittingFeedback(true)
      const updatedSubmission = await milestoneApi.submitFeedback(selectedSubmission.id, {
        content: feedbackContent.trim(),
        accepted: feedbackAccepted,
      })
      showSuccess('Gửi nhận xét thành công!')

      // Update local state with new feedback
      setMilestones((prev: MilestoneResponse[]) =>
        prev.map((m: MilestoneResponse) => ({
          ...m,
          status: m.submissions.some((sub) => sub.id === updatedSubmission.id)
            ? feedbackAccepted
              ? 'ACCEPTED'
              : 'REVISION_REQUIRED'
            : m.status,
          submissions: m.submissions.map((sub: SubmissionResponse) =>
            sub.id === updatedSubmission.id ? updatedSubmission : sub,
          ),
        })),
      )
      setIsFeedbackModalOpen(false)
      setSelectedSubmission(null)
    } catch (err: any) {
      showError('Lỗi gửi nhận xét: ' + (err.response?.data?.message || err.message))
    } finally {
      setSubmittingFeedback(false)
    }
  }

  const selectedAssignment = assignments.find(
    (assignment) => assignment.id === selectedAssignmentId,
  )

  const loadTopic = useCallback(
    () => (selectedAssignment ? topicApi.get(selectedAssignment.topicId) : Promise.resolve(null)),
    [selectedAssignment],
  )
  const { data: selectedTopic } = useRemoteData(loadTopic)
  const isLecturerOrAdmin =
    !!selectedTopic &&
    canManageDepartment(user, selectedTopic.departmentId, selectedTopic.facultyId)
  const canFeedback =
    !!user?.roles.includes('LECTURER') &&
    !!selectedTopic?.supervisors.some((supervisor) => supervisor.lecturerId === user.lecturer?.id)

  return (
    <div className="milestone-page">
      <div className="page-header">
        <div>
          <h1>
            {user?.roles.includes('LECTURER') && !hasAcademicLeadership(user)
              ? 'Tiến độ & Báo cáo'
              : 'Tiến độ & Mốc báo cáo'}
          </h1>
        </div>
      </div>

      {/* Select Assignment Selector */}
      <div className="page-card mb-6">
        <div className="milestone-assignment-card__header">
          <label className="section-title section-title--label" htmlFor="milestone-assignment">
            Chọn Đề tài / Dự án Phân công:
          </label>
          {isLecturerOrAdmin && selectedAssignmentId && (
            <button
              type="button"
              className="btn btn-primary"
              disabled={selectedAssignment?.status !== 'IN_PROGRESS'}
              onClick={() => {
                setEditingMilestone(null)
                setNewMilestone({
                  title: '',
                  milestoneType: 'PROGRESS',
                  description: '',
                  dueAt: '',
                })
                setIsCreateModalOpen(true)
              }}
            >
              + Tạo mốc tiến độ mới
            </button>
          )}
        </div>
        <div className="form-group mb-0">
          <AnimatedSelect
            id="milestone-assignment"
            className="form-select font-semibold"
            value={selectedAssignmentId ?? ''}
            onChange={(e) => {
              setMilestones([])
              setSelectedAssignmentId(Number(e.target.value) || null)
            }}
            disabled={loadingAssignments || !assignments.length}
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
          </AnimatedSelect>
        </div>
      </div>

      <MilestoneBoard
        key={selectedAssignmentId ?? 'no-assignment'}
        milestones={milestones.filter((item) => item.assignmentId === selectedAssignmentId)}
        loading={loadingAssignments || loadingMilestones}
        error={selectedAssignmentId ? milestoneError : null}
        onRetry={() => setMilestoneReload((value) => value + 1)}
        hasAssignment={!!selectedAssignmentId}
        canManage={isLecturerOrAdmin}
        canFeedback={canFeedback}
        canEdit={selectedAssignment?.status === 'IN_PROGRESS'}
        reminding={reminding}
        onEdit={handleEditMilestone}
        onFeedback={handleOpenFeedback}
        onRemind={() => setReminderOpen(true)}
      />

      {/* Modal Create Milestone */}
      {isCreateModalOpen && (
        <div
          className="modal-backdrop fade-in"
          onClick={() => {
            if (!submittingMilestone) setIsCreateModalOpen(false)
          }}
        >
          <div
            className="modal-content scale-in"
            role="dialog"
            aria-modal="true"
            aria-labelledby="milestone-editor-title"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="modal-header" style={{ borderBottom: 'none' }}>
              <h3 id="milestone-editor-title" className="font-semibold text-lg">
                {editingMilestone ? 'Chỉnh sửa mốc báo cáo' : 'Tạo mốc tiến độ mới'}
              </h3>
              <button
                type="button"
                className="btn-close"
                aria-label="Đóng hộp thoại mốc báo cáo"
                disabled={submittingMilestone}
                onClick={() => setIsCreateModalOpen(false)}
              >
                &times;
              </button>
            </div>
            <form onSubmit={handleCreateMilestone}>
              <div className="modal-body py-4 space-y-4">
                <div className="form-group">
                  <label className="form-label">
                    Tiêu đề mốc tiến độ <span className="text-red">*</span>
                  </label>
                  <input
                    type="text"
                    className="form-input"
                    placeholder="Ví dụ: Báo cáo giữa kỳ..."
                    maxLength={200}
                    value={newMilestone.title}
                    onChange={(e) => setNewMilestone({ ...newMilestone, title: e.target.value })}
                    required
                  />
                </div>

                <div className="form-group">
                  <label className="form-label">
                    Loại mốc tiến độ <span className="text-red">*</span>
                  </label>
                  <AnimatedSelect
                    className="form-select"
                    value={newMilestone.milestoneType}
                    onChange={(e) =>
                      setNewMilestone({
                        ...newMilestone,
                        milestoneType: e.target.value as MilestoneType,
                      })
                    }
                  >
                    <option value="PROPOSAL">Đề cương</option>
                    <option value="PROGRESS">Báo cáo tiến độ</option>
                    <option value="FINAL">Báo cáo tổng kết</option>
                  </AnimatedSelect>
                </div>

                <div className="form-group">
                  <label className="form-label">
                    Mô tả yêu cầu <span className="text-red">*</span>
                  </label>
                  <textarea
                    className="form-textarea"
                    rows={3}
                    placeholder="Mô tả chi tiết nội dung sinh viên cần nộp..."
                    value={newMilestone.description}
                    onChange={(e) =>
                      setNewMilestone({ ...newMilestone, description: e.target.value })
                    }
                    required
                  />
                </div>

                <div className="form-group">
                  <label className="form-label">
                    Hạn nộp <span className="text-red">*</span>
                  </label>
                  <DateTimeInput
                    type="datetime-local"
                    className="form-input"
                    value={newMilestone.dueAt}
                    onValueChange={(value) => setNewMilestone({ ...newMilestone, dueAt: value })}
                    required
                  />
                </div>
              </div>

              <div
                className="modal-footer"
                style={{ display: 'flex', justifyContent: 'flex-end', gap: 12 }}
              >
                <button
                  type="button"
                  className="btn btn-secondary"
                  onClick={() => setIsCreateModalOpen(false)}
                  disabled={submittingMilestone}
                >
                  Hủy
                </button>
                <button type="submit" className="btn btn-primary" disabled={submittingMilestone}>
                  {submittingMilestone
                    ? 'Đang lưu...'
                    : editingMilestone
                      ? 'Lưu thay đổi'
                      : 'Tạo mốc tiến độ'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal Feedback Report Submission */}
      {isFeedbackModalOpen && selectedSubmission && (
        <div
          className="modal-backdrop fade-in"
          onClick={() => {
            if (!submittingFeedback) setIsFeedbackModalOpen(false)
          }}
        >
          <div className="modal-content scale-in" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3 className="font-semibold text-lg">Gửi nhận xét báo cáo</h3>
              <button
                type="button"
                className="btn-close"
                onClick={() => {
                  if (!submittingFeedback) setIsFeedbackModalOpen(false)
                }}
              >
                &times;
              </button>
            </div>
            <form onSubmit={handleSubmitFeedback}>
              <div className="modal-body py-4 space-y-4">
                <div className="p-3 bg-blue-50 text-blue-900 rounded-lg text-sm">
                  <span className="font-semibold block">
                    Bản nộp #{selectedSubmission.versionNo} - Sinh viên:{' '}
                    {selectedSubmission.submittedByName}
                  </span>
                  {selectedSubmission.note && (
                    <span className="block text-xs mt-1">Ghi chú: "{selectedSubmission.note}"</span>
                  )}
                </div>

                <div className="form-group">
                  <label className="form-label">
                    Nội dung nhận xét <span className="text-red">*</span>
                  </label>
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
                  <label className="form-label">
                    Kết quả đánh giá <span className="text-red">*</span>
                  </label>
                  <AnimatedSelect
                    className="form-select"
                    value={feedbackAccepted ? 'true' : 'false'}
                    onChange={(e) => setFeedbackAccepted(e.target.value === 'true')}
                  >
                    <option value="true">Chấp nhận</option>
                    <option value="false">Yêu cầu chỉnh sửa</option>
                  </AnimatedSelect>
                </div>
              </div>

              <div className="modal-footer flex gap-3 justify-end">
                <button
                  type="button"
                  className="btn btn-secondary"
                  onClick={() => {
                    if (!submittingFeedback) setIsFeedbackModalOpen(false)
                  }}
                  disabled={submittingFeedback}
                >
                  Hủy
                </button>
                <button type="submit" className="btn btn-primary" disabled={submittingFeedback}>
                  {submittingFeedback ? 'Đang gửi...' : 'Gửi nhận xét'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
      <ConfirmModal
        isOpen={reminderOpen}
        title="Gửi nhắc hạn báo cáo"
        message={`Gửi thông báo cho các thành viên nhóm “${selectedAssignment?.groupName ?? ''}” về những mốc sắp tới chưa nộp báo cáo?`}
        confirmText="Gửi nhắc hạn"
        variant="primary"
        loading={reminding}
        onConfirm={handleRemind}
        onCancel={() => {
          if (!reminding) setReminderOpen(false)
        }}
      />
    </div>
  )
}
