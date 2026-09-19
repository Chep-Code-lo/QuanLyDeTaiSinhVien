import { Link } from 'react-router-dom'
import { PageHeader } from '../components/PageHeader'
import { useAuth } from '../context/AuthContext'

export function DashboardPage() {
  const { user, canAccessAcademicManagement, hasRole } = useAuth()

  return (
    <>
      <PageHeader
        title="Tổng quan hệ thống"
        description="Chào mừng bạn đến với Hệ thống Quản lý Đề tài Sinh viên. Quản lý toàn bộ tiến độ, đánh giá và học thuật."
      />

      {/* User info banner */}
      <div className="page-card mb-6 bg-gradient-to-r from-blue-50 to-indigo-50 border-blue-200">
        <div className="flex justify-between items-center">
          <div>
            <h2 className="font-bold text-xl text-primary mb-1">
              Xin chào, {user?.fullName || 'Người dùng'} 👋
            </h2>
            <p className="text-sm text-secondary">
              Tài khoản: <strong>{user?.username}</strong> | Vai trò:{' '}
              {user?.roles?.map((r) => (
                <span key={r} className="badge badge-info ml-1 font-semibold">
                  {r}
                </span>
              ))}
            </p>
            {user?.activeAppointments && user.activeAppointments.length > 0 && (
              <div className="mt-2 text-xs flex items-center gap-2">
                <span className="font-semibold text-green-700">Chức vụ đảm nhiệm:</span>
                {user.activeAppointments.map((apt) => (
                  <span key={apt.id} className="badge badge-success">
                    {apt.positionCode === 'DEAN'
                      ? `Trưởng Khoa (ID Khoa: #${apt.facultyId})`
                      : `Trưởng Bộ Môn (ID Bộ môn: #${apt.departmentId})`}
                  </span>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>

      <section className="dashboard-grid mb-6">
        <article className="metric-card">
          <span className="metric-card__label">Vai trò hiện tại</span>
          <strong className="metric-card__value text-primary">{user?.roles?.join(', ') || 'STUDENT'}</strong>
          <span className="metric-card__note">Quyền hạn hệ thống</span>
        </article>
        <article className="metric-card">
          <span className="metric-card__label">Vị trí quản lý</span>
          <strong className="metric-card__value text-green-600">
            {user?.activeAppointments?.length ? `${user.activeAppointments.length} vị trí` : 'Không'}
          </strong>
          <span className="metric-card__note">Scope Trưởng Khoa / Bộ môn</span>
        </article>
        <article className="metric-card">
          <span className="metric-card__label">Phiên làm việc</span>
          <strong className="metric-card__value text-indigo-600">Active</strong>
          <span className="metric-card__note">Spring Security Session</span>
        </article>
        <article className="metric-card">
          <span className="metric-card__label">REST API Status</span>
          <strong className="metric-card__value text-emerald-600">Connected</strong>
          <span className="metric-card__note">Base URL: /api/v1</span>
        </article>
      </section>

      <section className="dashboard-sections">
        <article className="content-card">
          <div className="content-card__header">
            <h2>Chức năng chính của bạn</h2>
            <span>Lựa chọn nghiệp vụ</span>
          </div>
          <div className="quick-links">
            <Link className="quick-link" to="/progress">
              <div>
                <div className="font-semibold">Tiến độ & Milestone</div>
                <div className="text-xs text-secondary font-normal mt-0.5">Tạo milestone, nộp báo cáo & nhập feedback</div>
              </div>
              <span className="quick-link__arrow">→</span>
            </Link>

            {hasRole('LECTURER') || hasRole('ADMIN') ? (
              <>
                <Link className="quick-link" to="/evaluations">
                  <div>
                    <div className="font-semibold">Setup Đánh giá & Rubrics</div>
                    <div className="text-xs text-secondary font-normal mt-0.5">Wizard 4 bước (Component, Rubric, Lock)</div>
                  </div>
                  <span className="quick-link__arrow">→</span>
                </Link>

                <Link className="quick-link" to="/evaluator-assignment">
                  <div>
                    <div className="font-semibold">Gán Evaluator / Người Chấm</div>
                    <div className="text-xs text-secondary font-normal mt-0.5">Autocomplete giảng viên & chọn Defense</div>
                  </div>
                  <span className="quick-link__arrow">→</span>
                </Link>
              </>
            ) : null}

            {canAccessAcademicManagement() ? (
              <Link className="quick-link" to="/academic-management">
                <div>
                  <div className="font-semibold">Quản lý Học thuật</div>
                  <div className="text-xs text-secondary font-normal mt-0.5">Quản lý Đợt đăng ký & Danh mục Khoa / Bộ môn</div>
                </div>
                <span className="quick-link__arrow">→</span>
              </Link>
            ) : null}

            {hasRole('ADMIN') ? (
              <Link className="quick-link" to="/admin-appointments">
                <div>
                  <div className="font-semibold">Bổ nhiệm Chức vụ (Admin)</div>
                  <div className="text-xs text-secondary font-normal mt-0.5">Gán vị trí DEAN & DEPARTMENT_HEAD</div>
                </div>
                <span className="quick-link__arrow">→</span>
              </Link>
            ) : null}
          </div>
        </article>

        <article className="content-card">
          <div className="content-card__header">
            <h2>Trạng thái tích hợp Backend</h2>
            <span>Source of Truth</span>
          </div>
          <ul className="status-list">
            <li>
              <span className="status-dot status-dot--success" />
              Authentication & Session Cookie (JSESSIONID, XSRF-TOKEN).
            </li>
            <li>
              <span className="status-dot status-dot--success" />
              Searchable Lecturers Autocomplete (`GET /api/v1/lecturers`).
            </li>
            <li>
              <span className="status-dot status-dot--success" />
              Milestones & Submission Feedback (`POST /api/v1/report-submissions/{'{id}'}/feedback`).
            </li>
            <li>
              <span className="status-dot status-dot--success" />
              Wizard Assessment Setup (10.0 points check & Lock dialogs).
            </li>
            <li>
              <span className="status-dot status-dot--success" />
              Dynamic Evaluator Assignment (COMMITTEE defense requirement vs REVIEW defense = null).
            </li>
          </ul>
        </article>
      </section>
    </>
  )
}
