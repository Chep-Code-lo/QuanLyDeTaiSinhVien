import { PageControls } from '../components/PageControls'
import { EmailScopePicker, type EmailScopeSelection } from '../components/EmailScopePicker'
import { EmailDeliveryDetail } from '../components/EmailDeliveryDetail'
import { ConfirmModal } from '../components/ConfirmModal'
import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import { isAxiosError } from 'axios'
import { Eye, Plus, RefreshCw, Save, Send } from 'lucide-react'
import { emailNotificationsApi } from '../api/emailNotifications'
import { PageHeader } from '../components/PageHeader'
import { useToast } from '../context/ToastContext'
import { useRequestLoading } from '../hooks/useRequestLoading'
import { useAuth } from '../context/AuthContext'
import type {
  EmailLogResponse,
  EmailNotificationEventType,
  EmailNotificationSettingResponse,
  EmailOutboxStatus,
  EmailPreviewResponse,
  EmailRecipientGroup,
  EmailRecipientResponse,
  EmailTemplateResponse,
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
}

const eventDefinitions: EventDefinition[] = [
  {
    eventType: 'RESULT_PUBLISHED',
    label: 'Công bố kết quả điểm',
    description:
      'Tự động gửi điểm tổng kết cho sinh viên có tên trong kết quả khi kết quả được công bố.',
  },
  {
    eventType: 'TASK_ASSIGNED',
    label: 'Giao nhiệm vụ',
    description: 'Gửi khi giảng viên giao nhiệm vụ, mốc báo cáo mới.',
  },
  {
    eventType: 'TOPIC_APPROVED',
    label: 'Đề tài được duyệt',
    description: 'Gửi khi đề tài hoặc đăng ký đề tài được phê duyệt.',
  },
  {
    eventType: 'TOPIC_REJECTED',
    label: 'Đề tài bị từ chối',
    description: 'Gửi khi đề tài hoặc đăng ký đề tài chưa được chấp thuận.',
  },
  {
    eventType: 'DEADLINE_REMINDER',
    label: 'Nhắc hạn báo cáo',
    description: 'Tự động nhắc trước hạn nộp của nhiệm vụ và mốc báo cáo.',
  },
  {
    eventType: 'REGISTRATION_OPENED',
    label: 'Mở đợt đăng ký',
    description: 'Gửi khi một đợt đăng ký đề tài được kích hoạt.',
  },
]

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

function settingFromTemplate(
  template: EmailTemplateResponse,
  scopeType: 'SYSTEM' | 'LECTURER' | 'FACULTY',
  scopeId: number,
): EmailNotificationSettingResponse {
  return {
    ...template,
    eventType: 'TASK_ASSIGNED',
    enabled: false,
    recipientGroups: ['DIRECT_PARTICIPANTS'],
    scopeType,
    scopeId,
    allowedVariables: [],
  }
}

function emailError(error: unknown): string {
  const message = isAxiosError(error) ? error.response?.data?.message : undefined
  switch (message) {
    case 'Save this email setting before sending a test message.':
    case 'Save this email setting before sending email.':
      return 'Hãy lưu cấu hình trước khi gửi email.'
    case 'MAIL_FROM must be configured before email settings can be saved.':
      return 'Chưa cấu hình địa chỉ gửi mặc định trong biến môi trường MAIL_FROM.'
    case "From email is not in the deployment's verified sender allow-list.":
      return 'Địa chỉ gửi chưa có trong danh sách MAIL_ALLOWED_FROM đã xác thực.'
    case 'One or more selected recipients are unavailable. Reload the recipient list.':
      return 'Một số người nhận không còn khả dụng. Vui lòng chọn lại.'
    case 'No recipients matched the selected group.':
      return 'Không có người nhận phù hợp trong nhóm đã chọn.'
    case 'The email template was changed. Reload and try again.':
      return 'Mẫu email đã được cập nhật. Vui lòng tải lại trước khi sửa.'
    case 'Email template was not found.':
      return 'Không tìm thấy mẫu email của bạn.'
    default:
      return message || 'Không thể xử lý cấu hình email. Vui lòng thử lại.'
  }
}

export function EmailSettingsPage() {
  const { showError, showSuccess } = useToast()
  const { user } = useAuth()
  const isAdmin = user?.roles.includes('ADMIN') ?? false
  const [selectedScope, setSelectedScope] = useState<EmailScopeSelection | null>(null)
  const [pendingScope, setPendingScope] = useState<EmailScopeSelection | null>(null)
  const scopeType = selectedScope?.scopeType ?? (isAdmin ? 'SYSTEM' : 'LECTURER')
  const scopeId = selectedScope?.scopeId ?? (isAdmin ? 0 : (user?.id ?? 0))
  const [settings, setSettings] = useState<EmailNotificationSettingResponse[]>([])
  const [logs, setLogs] = useState<EmailLogResponse[]>([])
  const [selectedEvent, setSelectedEvent] = useState<EmailNotificationEventType>('TASK_ASSIGNED')
  const [form, setForm] = useState<EmailForm>(defaultForm)
  const [preview, setPreview] = useState<EmailPreviewResponse | null>(null)
  const [loading, setLoading] = useRequestLoading(JSON.stringify([scopeType, scopeId]))
  const [logError, setLogError] = useState('')
  const [logPage, setLogPage] = useState(0)
  const [logPages, setLogPages] = useState(0)
  const [logsLoading, setLogsLoading] = useRequestLoading(JSON.stringify([user?.id, logPage]))
  const logGeneration = useRef(0)
  const invalidateLogs = useCallback(() => {
    logGeneration.current++
  }, [])
  const [loadError, setLoadError] = useState('')
  const [saving, setSaving] = useState(false)
  const [previewing, setPreviewing] = useState(false)
  const [sending, setSending] = useState(false)
  const [testOpen, setTestOpen] = useState(false)
  const [testing, setTesting] = useState(false)
  const testSending = useRef(false)
  const [logId, setLogId] = useState<number | null>(null)
  const [recipientType, setRecipientType] = useState<'STUDENT' | 'LECTURER' | 'SPECIFIC'>(
    'SPECIFIC',
  )
  const [recipientSearch, setRecipientSearch] = useState('')
  const [recipientPage, setRecipientPage] = useState(0)
  const [recipients, setRecipients] = useState<EmailRecipientResponse[]>([])
  const [selectedRecipients, setSelectedRecipients] = useState<EmailRecipientResponse[]>([])
  const [recipientTotal, setRecipientTotal] = useState(0)
  const [recipientPages, setRecipientPages] = useState(0)
  const [recipientsLoading, setRecipientsLoading] = useState(true)
  const [recipientError, setRecipientError] = useState('')
  const [customTemplates, setCustomTemplates] = useState<EmailTemplateResponse[]>([])
  const [selectedCustom, setSelectedCustom] = useState<EmailTemplateResponse | null>(null)
  const [creatingTemplate, setCreatingTemplate] = useState(false)
  const [templateName, setTemplateName] = useState('')
  const [templatePage, setTemplatePage] = useState(0)
  const [templatePages, setTemplatePages] = useState(0)
  const [templatesLoading, setTemplatesLoading] = useState(true)
  const resultEmail = !creatingTemplate && !selectedCustom && selectedEvent === 'RESULT_PUBLISHED'

  const selectedSetting = useMemo(
    () =>
      selectedCustom
        ? settingFromTemplate(selectedCustom, scopeType, scopeId)
        : settings.find((setting) => setting.eventType === selectedEvent),
    [selectedEvent, settings, selectedCustom, scopeType, scopeId],
  )
  const selectedDefinition = useMemo(
    () =>
      creatingTemplate
        ? { label: 'Tạo mẫu email', description: 'Soạn và lưu mẫu email mới để sử dụng lại.' }
        : selectedCustom
          ? { label: selectedCustom.name, description: 'Mẫu email của bạn.' }
          : eventDefinitions.find((definition) => definition.eventType === selectedEvent),
    [selectedEvent, selectedCustom, creatingTemplate],
  )

  const settingsGeneration = useRef(0)
  const invalidateSettings = useCallback(() => {
    settingsGeneration.current++
  }, [])
  const loadSettings = useCallback(() => {
    const generation = ++settingsGeneration.current
    return emailNotificationsApi
      .listSettings({ scopeType, scopeId })
      .then((items) => {
        if (generation !== settingsGeneration.current) return
        setSettings(items)
        setLoadError('')
      })
      .catch((error) => {
        if (generation === settingsGeneration.current) setLoadError(emailError(error))
      })
      .finally(() => {
        if (generation === settingsGeneration.current) setLoading(false)
      })
  }, [scopeType, scopeId, setLoading])

  const loadTemplates = useCallback(
    (page = 0) =>
      emailNotificationsApi
        .listTemplates(page)
        .then((result) => {
          setCustomTemplates(result.content)
          setTemplatePage(page)
          setTemplatePages(result.totalPages)
        })
        .catch((error) => {
          showError(emailError(error))
        })
        .finally(() => {
          setTemplatesLoading(false)
        }),
    [showError],
  )

  const loadLogs = useCallback(() => {
    const generation = ++logGeneration.current
    return emailNotificationsApi
      .listLogs({ page: logPage, size: 10 })
      .then((result) => {
        if (generation !== logGeneration.current) return
        setLogs(result.content)
        setLogPages(result.totalPages)
        setLogError('')
      })
      .catch((error) => {
        if (generation === logGeneration.current) {
          setLogs([])
          setLogError(emailError(error))
        }
      })
      .finally(() => {
        if (generation === logGeneration.current) setLogsLoading(false)
      })
  }, [logPage, setLogsLoading])

  useEffect(() => {
    void loadSettings()
    return invalidateSettings
  }, [loadSettings, invalidateSettings])
  useEffect(() => {
    void loadLogs()
    return invalidateLogs
  }, [loadLogs, invalidateLogs])
  useEffect(() => {
    void loadTemplates()
  }, [loadTemplates])

  const [formSetting, setFormSetting] = useState(selectedSetting)
  const [formCreating, setFormCreating] = useState(creatingTemplate)
  if (formSetting !== selectedSetting || formCreating !== creatingTemplate) {
    setFormSetting(selectedSetting)
    setFormCreating(creatingTemplate)
    if (selectedSetting && !creatingTemplate) {
      setForm(formFromSetting(selectedSetting))
      setPreview(null)
    }
  }

  const updateField = <Field extends keyof EmailForm>(field: Field, value: EmailForm[Field]) => {
    setForm((current) => ({ ...current, [field]: value }))
  }

  const hasUnsavedChanges = selectedSetting
    ? creatingTemplate ||
      JSON.stringify(form) !== JSON.stringify(formFromSetting(selectedSetting)) ||
      Boolean(selectedCustom && templateName.trim() !== selectedCustom.name)
    : true

  useEffect(() => {
    let cancelled = false
    const timer = window.setTimeout(() => {
      void emailNotificationsApi
        .listRecipients({
          role: recipientType === 'SPECIFIC' ? undefined : recipientType,
          search: recipientType === 'SPECIFIC' ? recipientSearch : undefined,
          page: recipientType === 'SPECIFIC' ? recipientPage : 0,
        })
        .then((result) => {
          if (cancelled) return
          setRecipients(result.content)
          setRecipientTotal(result.totalElements)
          setRecipientPages(result.totalPages)
        })
        .catch((error) => {
          if (!cancelled) setRecipientError(emailError(error))
        })
        .finally(() => {
          if (!cancelled) setRecipientsLoading(false)
        })
    }, 250)
    return () => {
      cancelled = true
      window.clearTimeout(timer)
    }
  }, [recipientType, recipientSearch, recipientPage])

  const togglePerson = (recipient: EmailRecipientResponse) => {
    setSelectedRecipients((current) =>
      current.some((person) => person.userId === recipient.userId)
        ? current.filter((person) => person.userId !== recipient.userId)
        : [...current, recipient],
    )
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
      scopeType: selectedSetting?.scopeType ?? scopeType,
      scopeId: selectedSetting?.scopeId ?? scopeId,
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
    if ((creatingTemplate || selectedCustom) && !templateName.trim()) {
      showError('Vui lòng nhập tên mẫu email.')
      return
    }
    const payload = requestPayload()
    if (!payload) return

    setSaving(true)
    try {
      if (creatingTemplate || selectedCustom) {
        const templatePayload = {
          name: templateName.trim(),
          senderName: payload.senderName,
          fromEmail: payload.fromEmail,
          replyToEmail: payload.replyToEmail,
          subjectTemplate: payload.subjectTemplate,
          bodyTemplate: payload.bodyTemplate,
          version: selectedCustom?.version,
        }
        const saved = selectedCustom
          ? await emailNotificationsApi.updateTemplate(selectedCustom.id, templatePayload)
          : await emailNotificationsApi.createTemplate(templatePayload)
        setSelectedCustom(saved)
        setCreatingTemplate(false)
        setTemplateName(saved.name)
        setSelectedEvent('TASK_ASSIGNED')
        setForm(formFromSetting(settingFromTemplate(saved, scopeType, scopeId)))
        void loadTemplates()
        showSuccess('Đã lưu mẫu email.')
        return
      }
      const saved = await emailNotificationsApi.updateSetting(selectedEvent, payload)
      setSettings((items) =>
        items.map((item) => (item.eventType === saved.eventType ? saved : item)),
      )
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

  const sendEmail = async () => {
    if (sending) return
    if (!selectedSetting?.id || hasUnsavedChanges) {
      showError('Hãy lưu cấu hình trước khi gửi email.')
      return
    }
    if (recipientType === 'SPECIFIC' && selectedRecipients.length === 0) {
      showError('Chọn ít nhất một người nhận.')
      return
    }
    setSending(true)
    try {
      const payload = {
        recipientRoles: recipientType === 'SPECIFIC' ? [] : [recipientType],
        recipientUserIds:
          recipientType === 'SPECIFIC' ? selectedRecipients.map((person) => person.userId) : [],
      }
      const result = selectedCustom
        ? await emailNotificationsApi.sendTemplate(selectedCustom.id, payload)
        : await emailNotificationsApi.send(selectedEvent, payload)
      showSuccess(`Đã xếp hàng gửi email tới ${result.recipientCount} người nhận.`)
      void loadLogs()
    } catch (error) {
      showError(emailError(error))
    } finally {
      setSending(false)
    }
  }

  const beginTemplate = () => {
    setSelectedScope(null)
    setSelectedCustom(null)
    setCreatingTemplate(true)
    setSelectedEvent('TASK_ASSIGNED')
    setTemplateName('')
    setPreview(null)
    setForm({
      ...defaultForm,
      senderName: form.senderName,
      fromEmail: form.fromEmail,
      replyToEmail: form.replyToEmail,
      recipientGroups: ['DIRECT_PARTICIPANTS'],
    })
  }

  if (loading) {
    return (
      <div className="content-state" role="status">
        <span className="spinner" />
        <p>Đang tải cấu hình email...</p>
      </div>
    )
  }

  if (loadError || !selectedSetting || !selectedDefinition) {
    return (
      <div className="content-state" role="alert">
        <strong>Không thể tải cấu hình email</strong>
        <p>{loadError || 'Không tìm thấy cấu hình email phù hợp.'}</p>
        {selectedScope && (
          <button
            type="button"
            className="btn btn-secondary"
            onClick={() => {
              setSelectedScope(null)
              setSelectedEvent('TASK_ASSIGNED')
            }}
          >
            Quay về phạm vi mặc định
          </button>
        )}
        <button
          type="button"
          className="btn btn-secondary"
          onClick={() => {
            setLoading(true)
            setLoadError('')
            void loadSettings()
          }}
        >
          Thử lại
        </button>
      </div>
    )
  }

  return (
    <div className="email-settings-page">
      <PageHeader title="Cấu hình email" />

      <EmailScopePicker
        value={{ scopeType, scopeId }}
        disabled={saving || sending || testing || previewing}
        onChange={(next) => {
          if (hasUnsavedChanges) setPendingScope(next)
          else {
            setSelectedScope(next)
            setSelectedCustom(null)
            setCreatingTemplate(false)
            setSelectedEvent('TASK_ASSIGNED')
          }
        }}
      />
      <ConfirmModal
        isOpen={!!pendingScope}
        title="Đổi phạm vi email"
        message="Bỏ các thay đổi chưa lưu và chuyển phạm vi cấu hình email?"
        onCancel={() => setPendingScope(null)}
        onConfirm={() => {
          setSelectedScope(pendingScope)
          setPendingScope(null)
          setSelectedCustom(null)
          setCreatingTemplate(false)
          setSelectedEvent('TASK_ASSIGNED')
        }}
      />
      {logId && <EmailDeliveryDetail key={logId} id={logId} onClose={() => setLogId(null)} />}
      <ConfirmModal
        isOpen={testOpen}
        title="Gửi email thử"
        message="Gửi email thử từ cấu hình đã lưu đến chính tài khoản đang đăng nhập?"
        loading={testing}
        onCancel={() => {
          if (!testSending.current) setTestOpen(false)
        }}
        onConfirm={async () => {
          if (testSending.current || !selectedSetting?.id || hasUnsavedChanges) return
          testSending.current = true
          setTesting(true)
          try {
            const result = await emailNotificationsApi.sendTest(selectedEvent, {
              scopeType,
              scopeId,
            })
            showSuccess(`Đã xếp hàng email thử đến ${result.recipientEmail}.`)
          } catch (error) {
            showError(emailError(error))
          } finally {
            testSending.current = false
            setTesting(false)
            setTestOpen(false)
          }
        }}
      />
      <div className="email-settings__layout">
        <nav className="page-card email-settings__events" aria-label="Loại thông báo email">
          <div className="email-settings__template-heading">
            <h2 className="section-title">Mẫu email</h2>
            <button
              type="button"
              className="btn btn-primary"
              onClick={beginTemplate}
              disabled={saving || sending}
            >
              <Plus size={16} aria-hidden="true" /> Tạo mẫu mới
            </button>
          </div>
          <h3 className="email-settings__template-subtitle">Mẫu mặc định</h3>
          <div className="email-settings__event-list">
            {eventDefinitions
              .filter((definition) =>
                settings.some((setting) => setting.eventType === definition.eventType),
              )
              .map((definition) => {
                const setting = settings.find((item) => item.eventType === definition.eventType)
                const active =
                  !creatingTemplate && !selectedCustom && definition.eventType === selectedEvent
                return (
                  <button
                    key={definition.eventType}
                    type="button"
                    className={`email-settings__event${active ? ' email-settings__event--active' : ''}`}
                    aria-pressed={active}
                    disabled={saving || sending}
                    onClick={() => {
                      setSelectedEvent(definition.eventType)
                      setSelectedCustom(null)
                      setCreatingTemplate(false)
                    }}
                  >
                    <span>
                      <strong>{definition.label}</strong>
                      <small>{definition.description}</small>
                    </span>
                    <em
                      className={
                        setting?.enabled
                          ? 'email-settings__state email-settings__state--on'
                          : 'email-settings__state'
                      }
                    >
                      {setting?.enabled ? 'Đang bật' : 'Đang tắt'}
                    </em>
                  </button>
                )
              })}
          </div>
          <h3 className="email-settings__template-subtitle">Mẫu của tôi</h3>
          <div className="email-settings__event-list" aria-label="Mẫu email của tôi">
            {customTemplates.map((template) => (
              <button
                key={template.id}
                type="button"
                className={`email-settings__event${!creatingTemplate && selectedCustom?.id === template.id ? ' email-settings__event--active' : ''}`}
                aria-pressed={!creatingTemplate && selectedCustom?.id === template.id}
                disabled={saving || sending}
                onClick={() => {
                  setSelectedScope(null)
                  setSelectedCustom(template)
                  setCreatingTemplate(false)
                  setSelectedEvent('TASK_ASSIGNED')
                  setTemplateName(template.name)
                }}
              >
                <span>
                  <strong>{template.name}</strong>
                  <small>{template.subjectTemplate}</small>
                </span>
              </button>
            ))}
            {customTemplates.length === 0 && !templatesLoading && (
              <p className="email-settings__field-help">
                Chưa có mẫu riêng. Chọn Tạo mẫu mới để bắt đầu.
              </p>
            )}
            {templatesLoading && <p role="status">Đang tải mẫu email...</p>}
          </div>
          {templatePages > 1 && (
            <div className="email-settings__recipient-pagination">
              <button
                type="button"
                className="btn btn-secondary"
                disabled={templatePage === 0 || templatesLoading}
                onClick={() => {
                  setTemplatesLoading(true)
                  void loadTemplates(templatePage - 1)
                }}
              >
                Trước
              </button>
              <span>
                {templatePage + 1} / {templatePages}
              </span>
              <button
                type="button"
                className="btn btn-secondary"
                disabled={templatePage + 1 >= templatePages || templatesLoading}
                onClick={() => {
                  setTemplatesLoading(true)
                  void loadTemplates(templatePage + 1)
                }}
              >
                Sau
              </button>
            </div>
          )}
        </nav>

        <form className="page-card email-settings__form" onSubmit={save}>
          <div className="email-settings__form-header">
            <div>
              <h2 className="section-title">{selectedDefinition.label}</h2>
              <p>{selectedDefinition.description}</p>
            </div>
            {!creatingTemplate && !selectedCustom && (
              <label className="email-settings__switch">
                <input
                  type="checkbox"
                  checked={form.enabled}
                  onChange={(event) => updateField('enabled', event.target.checked)}
                />
                <span aria-hidden="true" />
                <b>{form.enabled ? 'Đang bật' : 'Đang tắt'}</b>
              </label>
            )}
          </div>

          {(creatingTemplate || selectedCustom) && (
            <div className="form-group">
              <label className="form-label" htmlFor="email-template-name">
                Tên mẫu email
              </label>
              <input
                id="email-template-name"
                className="form-input"
                value={templateName}
                maxLength={150}
                required
                onChange={(event) => setTemplateName(event.target.value)}
                placeholder="Ví dụ: Thông báo họp nhóm"
              />
            </div>
          )}
          <div className="email-settings__grid">
            <div className="form-group">
              <label className="form-label" htmlFor="email-sender-name">
                Tên người gửi
              </label>
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
              <label className="form-label" htmlFor="email-from-address">
                Địa chỉ gửi
              </label>
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
              <label className="form-label" htmlFor="email-reply-to">
                Địa chỉ phản hồi
              </label>
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
            {!selectedCustom && !creatingTemplate && selectedEvent === 'DEADLINE_REMINDER' && (
              <div className="form-group">
                <label className="form-label" htmlFor="email-reminder-hours">
                  Nhắc trước hạn
                </label>
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
            <label className="form-label" htmlFor="email-subject">
              Tiêu đề thư
            </label>
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
            <label className="form-label" htmlFor="email-body">
              Nội dung thư
            </label>
            <p className="email-settings__field-help">
              Soạn nội dung email bằng văn bản thường, có thể xuống dòng.
            </p>
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

          {!creatingTemplate && !selectedCustom && (
            <fieldset
              disabled={saving || sending || testing}
              className="email-settings__recipients"
            >
              <legend>Nhóm nhận email tự động</legend>
              {(selectedEvent === 'REGISTRATION_OPENED'
                ? ['FACULTY_STUDENTS', 'FACULTY_LECTURERS', 'ADMINS']
                : selectedEvent === 'RESULT_PUBLISHED'
                  ? ['DIRECT_PARTICIPANTS']
                  : ['DIRECT_PARTICIPANTS', 'GROUP_STUDENTS', 'TOPIC_STUDENTS', 'SUPERVISORS']
              ).map((value) => {
                const group = value as EmailRecipientGroup
                const label = {
                  DIRECT_PARTICIPANTS: 'Người trực tiếp tham gia',
                  FACULTY_STUDENTS: 'Sinh viên trong khoa',
                  FACULTY_LECTURERS: 'Giảng viên trong khoa',
                  ADMINS: 'Quản trị viên',
                  GROUP_STUDENTS: 'Sinh viên trong nhóm',
                  TOPIC_STUDENTS: 'Sinh viên của đề tài',
                  SUPERVISORS: 'Giảng viên hướng dẫn',
                }[group]
                return (
                  <label key={group}>
                    <input
                      type="checkbox"
                      checked={form.recipientGroups.includes(group)}
                      onChange={(event) =>
                        updateField(
                          'recipientGroups',
                          event.target.checked
                            ? [...form.recipientGroups, group]
                            : form.recipientGroups.filter((item) => item !== group),
                        )
                      }
                    />{' '}
                    {label}
                  </label>
                )
              })}
            </fieldset>
          )}
          {resultEmail || scopeType === 'FACULTY' ? (
            <p className="email-settings__field-help">
              {resultEmail
                ? 'Người nhận là sinh viên có tên trong kết quả đã chốt. Email tự động được xếp hàng khi công bố kết quả; không gửi từ biểu mẫu này.'
                : 'Cấu hình của khoa áp dụng cho email tự động theo sự kiện. Có thể gửi thử đến tài khoản của bạn trước khi bật.'}
            </p>
          ) : (
            <fieldset className="email-settings__recipients" disabled={sending}>
              <legend className="form-label">Người nhận email</legend>
              <label className="form-label" htmlFor="email-recipient-type">
                Gửi đến
              </label>
              <select
                id="email-recipient-type"
                className="form-input"
                value={recipientType}
                onChange={(event) => {
                  setRecipientsLoading(true)
                  setRecipientError('')
                  setRecipientType(event.target.value as typeof recipientType)
                  setRecipientPage(0)
                  setRecipientSearch('')
                }}
              >
                <option value="SPECIFIC">Chọn người cụ thể</option>
                <option value="STUDENT">Sinh viên</option>
                {isAdmin && <option value="LECTURER">Giảng viên</option>}
              </select>
              {recipientType === 'SPECIFIC' && (
                <div className="email-settings__people">
                  <label className="form-label" htmlFor="email-recipient-search">
                    Tìm người nhận
                  </label>
                  <input
                    id="email-recipient-search"
                    className="form-input"
                    type="search"
                    value={recipientSearch}
                    placeholder="Tìm theo tên hoặc email"
                    onChange={(event) => {
                      setRecipientsLoading(true)
                      setRecipientError('')
                      setRecipientSearch(event.target.value)
                      setRecipientPage(0)
                    }}
                  />
                  {selectedRecipients.length > 0 && (
                    <div className="email-settings__selected" aria-label="Người nhận đã chọn">
                      {selectedRecipients.map((person) => (
                        <button
                          key={person.userId}
                          type="button"
                          className="email-settings__selected-person"
                          onClick={() => togglePerson(person)}
                          aria-label={`Bỏ chọn ${person.fullName} (${person.email})`}
                        >
                          {person.fullName} <span>{person.email}</span> ×
                        </button>
                      ))}
                    </div>
                  )}
                  {!recipientsLoading && !recipientError && (
                    <div className="email-settings__person-list">
                      {recipients.length === 0 ? (
                        <p>Không tìm thấy người nhận phù hợp.</p>
                      ) : (
                        recipients.map((person) => (
                          <label key={person.userId} className="email-settings__person">
                            <input
                              type="checkbox"
                              checked={selectedRecipients.some(
                                (selected) => selected.userId === person.userId,
                              )}
                              disabled={
                                selectedRecipients.length >= 500 &&
                                !selectedRecipients.some(
                                  (selected) => selected.userId === person.userId,
                                )
                              }
                              onChange={() => togglePerson(person)}
                            />
                            <span>
                              <strong>{person.fullName}</strong>
                              <small>{person.email}</small>
                            </span>
                          </label>
                        ))
                      )}
                    </div>
                  )}
                  {recipientPages > 1 && (
                    <div className="email-settings__recipient-pagination">
                      <button
                        type="button"
                        className="btn btn-secondary"
                        disabled={recipientPage === 0 || recipientsLoading}
                        onClick={() => {
                          setRecipientsLoading(true)
                          setRecipientPage((page) => page - 1)
                        }}
                      >
                        Trước
                      </button>
                      <span>
                        Trang {recipientPage + 1} / {recipientPages}
                      </span>
                      <button
                        type="button"
                        className="btn btn-secondary"
                        disabled={recipientPage + 1 >= recipientPages || recipientsLoading}
                        onClick={() => {
                          setRecipientsLoading(true)
                          setRecipientPage((page) => page + 1)
                        }}
                      >
                        Sau
                      </button>
                    </div>
                  )}
                </div>
              )}
              {recipientsLoading ? (
                <p role="status">Đang tải người nhận...</p>
              ) : recipientError ? (
                <p role="alert">{recipientError}</p>
              ) : (
                <p className="email-settings__field-help">
                  {recipientType === 'SPECIFIC'
                    ? `Đã chọn ${selectedRecipients.length} người nhận.`
                    : `Gửi tới toàn bộ ${recipientTotal} ${recipientType === 'STUDENT' ? 'sinh viên' : 'giảng viên'}${isAdmin ? '' : ' đang hướng dẫn'}.`}
                </p>
              )}
              {hasUnsavedChanges && (
                <p className="email-settings__field-help">Lưu thay đổi trước khi gửi email.</p>
              )}
            </fieldset>
          )}

          <div className="email-settings__actions">
            <button
              type="button"
              className="btn btn-secondary"
              onClick={() => {
                if (creatingTemplate) {
                  setCreatingTemplate(false)
                  return
                }
                setForm(formFromSetting(selectedSetting))
                if (selectedCustom) setTemplateName(selectedCustom.name)
              }}
              disabled={saving || previewing || sending}
            >
              <RefreshCw size={17} aria-hidden="true" /> Khôi phục
            </button>
            <button
              type="button"
              className="btn btn-secondary"
              onClick={() => void showPreview()}
              disabled={saving || previewing || sending}
            >
              <Eye size={17} aria-hidden="true" />{' '}
              {previewing ? 'Đang tạo xem trước...' : 'Xem trước'}
            </button>
            {!creatingTemplate && !selectedCustom && (
              <button
                type="button"
                className="btn btn-secondary"
                disabled={
                  testing ||
                  saving ||
                  sending ||
                  previewing ||
                  hasUnsavedChanges ||
                  !selectedSetting.id
                }
                onClick={() => setTestOpen(true)}
              >
                Gửi thử đến tài khoản của tôi
              </button>
            )}
            {!resultEmail && scopeType !== 'FACULTY' && (
              <button
                type="button"
                className="btn btn-secondary"
                onClick={() => void sendEmail()}
                disabled={
                  saving ||
                  previewing ||
                  sending ||
                  !selectedSetting.id ||
                  hasUnsavedChanges ||
                  recipientsLoading ||
                  Boolean(recipientError) ||
                  (recipientType === 'SPECIFIC'
                    ? selectedRecipients.length === 0
                    : recipientTotal === 0)
                }
              >
                <Send size={17} aria-hidden="true" /> {sending ? 'Đang xếp hàng...' : 'Gửi email'}
              </button>
            )}
            <button
              type="submit"
              className="btn btn-primary"
              disabled={saving || previewing || sending}
            >
              <Save size={17} aria-hidden="true" />{' '}
              {saving
                ? 'Đang lưu...'
                : creatingTemplate
                  ? 'Tạo mẫu'
                  : selectedCustom
                    ? 'Lưu mẫu'
                    : 'Lưu cấu hình'}
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
            <button
              type="button"
              className="btn-close"
              onClick={() => setPreview(null)}
              aria-label="Đóng xem trước"
            >
              ×
            </button>
          </div>
          <dl className="email-settings__preview-meta">
            <div>
              <dt>Từ</dt>
              <dd>
                {preview.senderName} &lt;{preview.fromEmail}&gt;
              </dd>
            </div>
            {preview.replyToEmail && (
              <div>
                <dt>Phản hồi</dt>
                <dd>{preview.replyToEmail}</dd>
              </div>
            )}
            <div>
              <dt>Tiêu đề</dt>
              <dd>{preview.subject}</dd>
            </div>
          </dl>
          <pre className="email-settings__preview-body">{preview.body}</pre>
        </section>
      )}

      <section className="page-card email-settings__logs">
        <div className="email-settings__section-heading">
          <div>
            <h2 className="section-title">Nhật ký gửi email</h2>
            <p>Theo dõi trạng thái giao hàng của 10 thư gần nhất.</p>
          </div>
          <button
            type="button"
            className="btn btn-secondary"
            onClick={() => {
              setLogsLoading(true)
              void loadLogs()
            }}
            disabled={logsLoading}
          >
            <RefreshCw size={17} aria-hidden="true" /> Làm mới
          </button>
        </div>
        {logsLoading ? (
          <div className="content-state" role="status">
            <span className="spinner" />
            <p>Đang tải nhật ký email...</p>
          </div>
        ) : logError ? (
          <p role="alert">{logError}</p>
        ) : logs.length === 0 ? (
          <div className="content-state">
            <strong>Chưa có email nào trong hàng đợi</strong>
            <p>Email đã gửi sẽ xuất hiện tại đây.</p>
          </div>
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
                  <th>Chi tiết</th>
                </tr>
              </thead>
              <tbody>
                {logs.map((log) => (
                  <tr key={log.id}>
                    <td>{formatDateTime(log.createdAt)}</td>
                    <td>
                      {log.eventKey.startsWith('email-manual:')
                        ? 'Email thủ công'
                        : (eventDefinitions.find((item) => item.eventType === log.eventType)
                            ?.label ?? log.eventType)}
                    </td>
                    <td className="email-settings__recipient-cell">
                      <strong>{log.recipientName}</strong>
                      <span>{log.recipientEmail}</span>
                    </td>
                    <td>{log.subject}</td>
                    <td>
                      <span
                        className={`email-settings__log-status email-settings__log-status--${log.status.toLowerCase()}`}
                        title={log.lastError}
                      >
                        {statusLabels[log.status]}
                      </span>
                    </td>
                    <td>{log.attemptCount}</td>
                    <td>
                      <button
                        type="button"
                        className="btn btn-secondary"
                        onClick={() => setLogId(log.id)}
                      >
                        Xem lần gửi #{log.id}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <PageControls
          label="Phân trang lịch sử email"
          page={logPage}
          totalPages={logPages}
          loading={logsLoading || !!logError}
          onPage={setLogPage}
        />
      </section>
    </div>
  )
}
