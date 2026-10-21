import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import { isAxiosError } from 'axios'
import { Eye, MailCheck, RefreshCw, Save, Send } from 'lucide-react'
import { emailNotificationsApi } from '../api/emailNotifications'
import { PageHeader } from '../components/PageHeader'
import { useToast } from '../context/ToastContext'
import type {
  EmailLogResponse,
  EmailNotificationEventType,
  EmailNotificationSettingResponse,
  EmailOutboxStatus,
  EmailPreviewResponse,
  EmailRecipientGroup,
  UpdateEmailNotificationSettingRequest,
} from '../types/domain'
import { formatDateTime } from '../utils/dateTime'
import './EmailSettingsPage.css'

type EmailForm = {
  enabled: boolean
  senderName: string
  fromEmail: string
  replyToEmail: string
  subjectTemplate: string
  bodyTemplate: string
  recipientGroups: EmailRecipientGroup[]
  reminderHours: string
}

type EventDefinition = {
  eventType: EmailNotificationEventType
  label: string
  description: string
  recipientGroups: EmailRecipientGroup[]
}

const eventDefinitions: EventDefinition[] = [
  {
    eventType: 'TASK_ASSIGNED',
    label: 'Giao nhiệm vụ',
    description: 'Gửi khi giảng viên giao nhiệm vụ, mốc báo cáo mới.',
    recipientGroups: ['DIRECT_PARTICIPANTS', 'GROUP_STUDENTS', 'TOPIC_STUDENTS', 'SUPERVISORS'],
  },
  {
    eventType: 'TOPIC_APPROVED',
    label: 'Đề tài được duyệt',
    description: 'Gửi khi đề tài hoặc đăng ký đề tài được phê duyệt.',
    recipientGroups: ['DIRECT_PARTICIPANTS', 'GROUP_STUDENTS', 'TOPIC_STUDENTS', 'SUPERVISORS'],
  },
  {
    eventType: 'TOPIC_REJECTED',
    label: 'Đề tài bị từ chối',
    description: 'Gửi khi đề tài hoặc đăng ký đề tài chưa được chấp thuận.',
    recipientGroups: ['DIRECT_PARTICIPANTS', 'GROUP_STUDENTS', 'TOPIC_STUDENTS', 'SUPERVISORS'],
  },
  {
    eventType: 'DEADLINE_REMINDER',
    label: 'Nhắc hạn báo cáo',
    description: 'Tự động nhắc trước hạn nộp của nhiệm vụ và mốc báo cáo.',
    recipientGroups: ['DIRECT_PARTICIPANTS', 'GROUP_STUDENTS', 'TOPIC_STUDENTS', 'SUPERVISORS'],
  },
  {
    eventType: 'REGISTRATION_OPENED',
    label: 'Mở đợt đăng ký',
    description: 'Gửi khi một đợt đăng ký đề tài được kích hoạt.',
    recipientGroups: ['FACULTY_STUDENTS', 'FACULTY_LECTURERS', 'ADMINS'],
  },
]

const recipientLabels: Record<EmailRecipientGroup, string> = {
  DIRECT_PARTICIPANTS: 'Người tham gia trực tiếp',
  FACULTY_STUDENTS: 'Sinh viên thuộc khoa',
  FACULTY_LECTURERS: 'Giảng viên thuộc khoa',
  GROUP_STUDENTS: 'Thành viên nhóm',
  TOPIC_STUDENTS: 'Sinh viên của đề tài',
  SUPERVISORS: 'Giảng viên hướng dẫn',
  ADMINS: 'Quản trị viên',
}

const statusLabels: Record<EmailOutboxStatus, string> = {
  PENDING: 'Đang chờ',
  SENDING: 'Đang gửi',
  SENT: 'Đã gửi',
  FAILED: 'Thất bại',
}

const defaultForm: EmailForm = {
  enabled: false,
  senderName: '',
  fromEmail: '',
  replyToEmail: '',
  subjectTemplate: '',
  bodyTemplate: '',
  recipientGroups: [],
  reminderHours: '24',
}

function formFromSetting(setting: EmailNotificationSettingResponse): EmailForm {
  return {
    enabled: setting.enabled,
    senderName: setting.senderName,
    fromEmail: setting.fromEmail,
    replyToEmail: setting.replyToEmail ?? '',
    subjectTemplate: setting.subjectTemplate,
    bodyTemplate: setting.bodyTemplate,
    recipientGroups: setting.recipientGroups,
    reminderHours: String(setting.reminderHours ?? 24),
  }
}

function emailError(error: unknown): string {
  const message = isAxiosError(error) ? error.response?.data?.message : undefined
  switch (message) {
    case 'Save this email setting before sending a test message.':
      return 'Hãy lưu cấu hình trước khi gửi thư kiểm tra.'
    case 'The current account does not have an active email address for testing.':
      return 'Tài khoản đang đăng nhập chưa có email hợp lệ để nhận thư kiểm tra.'
    case 'MAIL_FROM must be configured before email settings can be saved.':
      return 'Chưa cấu hình địa chỉ gửi mặc định trong biến môi trường MAIL_FROM.'
    case 'From email is not in the deployment\'s verified sender allow-list.':
      return 'Địa chỉ gửi chưa có trong danh sách MAIL_ALLOWED_FROM đã xác thực.'
    default:
      return message || 'Không thể xử lý cấu hình email. Vui lòng thử lại.'
  }
}

export function EmailSettingsPage() {
  const { showError, showSuccess } = useToast()
  const [settings, setSettings] = useState<EmailNotificationSettingResponse[]>([])
  const [logs, setLogs] = useState<EmailLogResponse[]>([])
  const [selectedEvent, setSelectedEvent] = useState<EmailNotificationEventType>('TASK_ASSIGNED')
  const [form, setForm] = useState<EmailForm>(defaultForm)
  const [preview, setPreview] = useState<EmailPreviewResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [logsLoading, setLogsLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [saving, setSaving] = useState(false)
  const [previewing, setPreviewing] = useState(false)
  const [testing, setTesting] = useState(false)

  const selectedSetting = useMemo(
    () => settings.find((setting) => setting.eventType === selectedEvent),
    [selectedEvent, settings],
  )
  const selectedDefinition = useMemo(
    () => eventDefinitions.find((definition) => definition.eventType === selectedEvent),
    [selectedEvent],
  )

  const loadSettings = useCallback(async () => {
    setLoading(true)
    setLoadError('')
    try {
      const items = await emailNotificationsApi.listSettings()
      setSettings(items)
    } catch (error) {
      setLoadError(emailError(error))
    } finally {
      setLoading(false)
    }
  }, [])

  const loadLogs = useCallback(async () => {
    setLogsLoading(true)
    try {
      const result = await emailNotificationsApi.listLogs()
      setLogs(result.content)
    } catch (error) {
      showError(emailError(error))
    } finally {
      setLogsLoading(false)
    }
  }, [showError])

  useEffect(() => {
    void loadSettings()
    void loadLogs()
  }, [loadLogs, loadSettings])

  useEffect(() => {
    if (selectedSetting) {
      setForm(formFromSetting(selectedSetting))
      setPreview(null)
    }
  }, [selectedSetting])

  const updateField = <Field extends keyof EmailForm>(field: Field, value: EmailForm[Field]) => {
    setForm((current) => ({ ...current, [field]: value }))
  }

  const toggleRecipient = (recipientGroup: EmailRecipientGroup) => {
    setForm((current) => ({
      ...current,
      recipientGroups: current.recipientGroups.includes(recipientGroup)
        ? current.recipientGroups.filter((item) => item !== recipientGroup)
        : [...current.recipientGroups, recipientGroup],
    }))
  }

  const requestPayload = (): UpdateEmailNotificationSettingRequest | null => {
    if (!form.senderName.trim() || !form.subjectTemplate.trim() || !form.bodyTemplate.trim()) {
      showError('Vui lòng nhập tên người gửi, tiêu đề và nội dung thư.')
      return null
    }
    if (form.recipientGroups.length === 0) {
      showError('Chọn ít nhất một nhóm người nhận.')
      return null
    }
    return {
      scopeType: 'SYSTEM',
      scopeId: 0,
      enabled: form.enabled,
      senderName: form.senderName.trim(),
      fromEmail: form.fromEmail.trim() || undefined,
      replyToEmail: form.replyToEmail.trim() || undefined,
      subjectTemplate: form.subjectTemplate.trim(),
      bodyTemplate: form.bodyTemplate.trim(),
      recipientGroups: form.recipientGroups,
      reminderHours: selectedEvent === 'DEADLINE_REMINDER' ? Number(form.reminderHours) : undefined,
    }
  }

  const save = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (saving || !selectedSetting) return
    const payload = requestPayload()
    if (!payload) return

    setSaving(true)
    try {
      const saved = await emailNotificationsApi.updateSetting(selectedEvent, payload)
      setSettings((items) => items.map((item) => item.eventType === saved.eventType ? saved : item))
      setForm(formFromSetting(saved))
      showSuccess(`Đã lưu cấu hình “${selectedDefinition?.label ?? 'email'}”.`)
    } catch (error) {
      showError(emailError(error))
    } finally {
      setSaving(false)
    }
  }

  const showPreview = async () => {
    if (previewing || !selectedSetting) return
    const payload = requestPayload()
    if (!payload) return

    setPreviewing(true)
    try {
      const result = await emailNotificationsApi.preview(selectedEvent, {
        scopeType: payload.scopeType,
        scopeId: payload.scopeId,
        senderName: payload.senderName,
        fromEmail: payload.fromEmail,
        replyToEmail: payload.replyToEmail,
        subjectTemplate: payload.subjectTemplate,
        bodyTemplate: payload.bodyTemplate,
      })
      setPreview(result)
    } catch (error) {
      showError(emailError(error))
    } finally {
      setPreviewing(false)
    }
  }

  const sendTest = async () => {
    if (testing || !selectedSetting?.id) {
      if (!selectedSetting?.id) showError('Hãy lưu cấu hình trước khi gửi thư kiểm tra.')
      return
    }
    setTesting(true)
    try {
      const result = await emailNotificationsApi.sendTest(selectedEvent)
      showSuccess(`Đã xếp hàng thư kiểm tra tới ${result.recipientEmail}.`)
      void loadLogs()
    } catch (error) {
      showError(emailError(error))
    } finally {
      setTesting(false)
    }
  }

  if (loading) {
    return <div className="content-state" role="status"><span className="spinner" /><p>Đang tải cấu hình email...</p></div>
  }

  if (loadError || !selectedSetting || !selectedDefinition) {
    return (
      <div className="content-state" role="alert">
        <strong>Không thể tải cấu hình email</strong>
        <p>{loadError || 'Không tìm thấy cấu hình email phù hợp.'}</p>
        <button type="button" className="btn btn-secondary" onClick={() => void loadSettings()}>Thử lại</button>
      </div>
    )
  }

  return (
    <div className="email-settings-page">
      <PageHeader
        title="Cấu hình email"
        description="Thiết lập nội dung và người nhận cho các thông báo email của toàn hệ thống."
      />

      <section className="page-card email-settings__notice" aria-label="Trạng thái cấu hình SMTP">
        <MailCheck size={24} aria-hidden="true" />
        <div>
          <strong>SMTP được cấu hình bằng biến môi trường</strong>
          <p>Thông tin nhạy cảm chỉ nằm trong file <code>.env</code>. Lưu mẫu thư, bật loại thông báo rồi gửi thư kiểm tra để xác nhận kết nối.</p>
        </div>
      </section>

      <div className="email-settings__layout">
        <nav className="page-card email-settings__events" aria-label="Loại thông báo email">
          <h2 className="section-title">Loại thông báo</h2>
          <div className="email-settings__event-list">
            {eventDefinitions.map((definition) => {
              const setting = settings.find((item) => item.eventType === definition.eventType)
              const active = definition.eventType === selectedEvent
              return (
                <button
                  key={definition.eventType}
                  type="button"
                  className={`email-settings__event${active ? ' email-settings__event--active' : ''}`}
                  aria-pressed={active}
                  onClick={() => setSelectedEvent(definition.eventType)}
                >
                  <span>
                    <strong>{definition.label}</strong>
                    <small>{definition.description}</small>
                  </span>
                  <em className={setting?.enabled ? 'email-settings__state email-settings__state--on' : 'email-settings__state'}>
                    {setting?.enabled ? 'Đang bật' : 'Đang tắt'}
                  </em>
                </button>
              )
            })}
          </div>
        </nav>

        <form className="page-card email-settings__form" onSubmit={save}>
          <div className="email-settings__form-header">
            <div>
              <h2 className="section-title">{selectedDefinition.label}</h2>
              <p>{selectedDefinition.description}</p>
            </div>
            <label className="email-settings__switch">
              <input
                type="checkbox"
                checked={form.enabled}
                onChange={(event) => updateField('enabled', event.target.checked)}
              />
              <span aria-hidden="true" />
              <b>{form.enabled ? 'Đang bật' : 'Đang tắt'}</b>
            </label>
          </div>

          <div className="email-settings__grid">
            <div className="form-group">
              <label className="form-label" htmlFor="email-sender-name">Tên người gửi</label>
              <input
                id="email-sender-name"
                className="form-input"
                value={form.senderName}
                onChange={(event) => updateField('senderName', event.target.value)}
                maxLength={150}
                required
              />
            </div>
            <div className="form-group">
              <label className="form-label" htmlFor="email-from-address">Địa chỉ gửi</label>
              <input
                id="email-from-address"
                className="form-input"
                type="email"
                value={form.fromEmail}
                onChange={(event) => updateField('fromEmail', event.target.value)}
                placeholder="Dùng MAIL_FROM nếu để trống"
                maxLength={254}
              />
            </div>
            <div className="form-group">
              <label className="form-label" htmlFor="email-reply-to">Địa chỉ phản hồi</label>
              <input
                id="email-reply-to"
                className="form-input"
                type="email"
                value={form.replyToEmail}
                onChange={(event) => updateField('replyToEmail', event.target.value)}
                placeholder="Không bắt buộc"
                maxLength={254}
              />
            </div>
            {selectedEvent === 'DEADLINE_REMINDER' && (
              <div className="form-group">
                <label className="form-label" htmlFor="email-reminder-hours">Nhắc trước hạn</label>
                <select
                  id="email-reminder-hours"
                  className="form-input"
                  value={form.reminderHours}
                  onChange={(event) => updateField('reminderHours', event.target.value)}
                >
                  <option value="6">6 giờ</option>
                  <option value="12">12 giờ</option>
                  <option value="24">24 giờ</option>
                  <option value="48">48 giờ</option>
                </select>
              </div>
            )}
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="email-subject">Tiêu đề thư</label>
            <input
              id="email-subject"
              className="form-input"
              value={form.subjectTemplate}
              onChange={(event) => updateField('subjectTemplate', event.target.value)}
              maxLength={500}
              required
            />
          </div>
          <div className="form-group">
            <label className="form-label" htmlFor="email-body">Nội dung thư (HTML)</label>
            <textarea
              id="email-body"
              className="form-input email-settings__body"
              value={form.bodyTemplate}
              onChange={(event) => updateField('bodyTemplate', event.target.value)}
              rows={10}
              maxLength={30000}
              required
            />
          </div>

          <fieldset className="email-settings__recipients">
            <legend className="form-label">Nhóm người nhận</legend>
            <div>
              {selectedDefinition.recipientGroups.map((recipientGroup) => (
                <label key={recipientGroup} className="email-settings__recipient">
                  <input
                    type="checkbox"
                    checked={form.recipientGroups.includes(recipientGroup)}
                    onChange={() => toggleRecipient(recipientGroup)}
                  />
                  <span>{recipientLabels[recipientGroup]}</span>
                </label>
              ))}
            </div>
          </fieldset>

          <div className="email-settings__variables">
            <strong>Biến có thể dùng</strong>
            <div>
              {selectedSetting.allowedVariables.map((variable) => <code key={variable}>{`{{${variable}}}`}</code>)}
            </div>
          </div>

          <div className="email-settings__actions">
            <button
              type="button"
              className="btn btn-secondary"
              onClick={() => setForm(formFromSetting(selectedSetting))}
              disabled={saving || previewing || testing}
            >
              <RefreshCw size={17} aria-hidden="true" /> Khôi phục
            </button>
            <button type="button" className="btn btn-secondary" onClick={() => void showPreview()} disabled={saving || previewing || testing}>
              <Eye size={17} aria-hidden="true" /> {previewing ? 'Đang tạo xem trước...' : 'Xem trước'}
            </button>
            <button type="button" className="btn btn-secondary" onClick={() => void sendTest()} disabled={saving || previewing || testing || !selectedSetting.id}>
              <Send size={17} aria-hidden="true" /> {testing ? 'Đang xếp hàng...' : 'Gửi thử'}
            </button>
            <button type="submit" className="btn btn-primary" disabled={saving || previewing || testing}>
              <Save size={17} aria-hidden="true" /> {saving ? 'Đang lưu...' : 'Lưu cấu hình'}
            </button>
          </div>
        </form>
      </div>

      {preview && (
        <section className="page-card email-settings__preview" aria-live="polite">
          <div className="email-settings__section-heading">
            <div>
              <h2 className="section-title">Xem trước email</h2>
              <p>Mẫu dùng dữ liệu minh họa, không gửi thư thật.</p>
            </div>
            <button type="button" className="btn-close" onClick={() => setPreview(null)} aria-label="Đóng xem trước">×</button>
          </div>
          <dl className="email-settings__preview-meta">
            <div><dt>Từ</dt><dd>{preview.senderName} &lt;{preview.fromEmail}&gt;</dd></div>
            {preview.replyToEmail && <div><dt>Phản hồi</dt><dd>{preview.replyToEmail}</dd></div>}
            <div><dt>Tiêu đề</dt><dd>{preview.subject}</dd></div>
          </dl>
          <iframe
            className="email-settings__preview-frame"
            sandbox=""
            srcDoc={preview.body}
            title="Nội dung email xem trước"
          />
        </section>
      )}

      <section className="page-card email-settings__logs">
        <div className="email-settings__section-heading">
          <div>
            <h2 className="section-title">Nhật ký gửi email</h2>
            <p>Theo dõi trạng thái giao hàng của 10 thư gần nhất.</p>
          </div>
          <button type="button" className="btn btn-secondary" onClick={() => void loadLogs()} disabled={logsLoading}>
            <RefreshCw size={17} aria-hidden="true" /> Làm mới
          </button>
        </div>
        {logsLoading ? (
          <div className="content-state" role="status"><span className="spinner" /><p>Đang tải nhật ký email...</p></div>
        ) : logs.length === 0 ? (
          <div className="content-state"><strong>Chưa có email nào trong hàng đợi</strong><p>Gửi thử một mẫu thư để kiểm tra cấu hình SMTP.</p></div>
        ) : (
          <div className="table-container">
            <table className="data-table email-settings__log-table">
              <thead>
                <tr>
                  <th>Thời gian</th>
                  <th>Loại</th>
                  <th>Người nhận</th>
                  <th>Tiêu đề</th>
                  <th>Trạng thái</th>
                  <th>Lần thử</th>
                </tr>
              </thead>
              <tbody>
                {logs.map((log) => (
                  <tr key={log.id}>
                    <td>{formatDateTime(log.createdAt)}</td>
                    <td>{eventDefinitions.find((item) => item.eventType === log.eventType)?.label ?? log.eventType}</td>
                    <td className="email-settings__recipient-cell"><strong>{log.recipientName}</strong><span>{log.recipientEmail}</span></td>
                    <td>{log.subject}</td>
                    <td><span className={`email-settings__log-status email-settings__log-status--${log.status.toLowerCase()}`} title={log.lastError}>{statusLabels[log.status]}</span></td>
                    <td>{log.attemptCount}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  )
}
