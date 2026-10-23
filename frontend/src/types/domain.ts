export type RoleCode = 'STUDENT' | 'LECTURER' | 'ADMIN'
export type PositionCode = 'DEAN' | 'DEPARTMENT_HEAD'

export type UserStatus = 'ACTIVE' | 'DISABLED' | 'LOCKED'

export interface StudentProfileResponse {
  id: number
  studentCode: string
  className: string
  cohort: string
  departmentId: number
}

export interface LecturerProfileResponse {
  id: number
  lecturerCode: string
  academicDegree?: string
  specialization?: string
  departmentId: number
}

export interface UserResponse {
  id: number
  email: string
  fullName: string
  phone?: string
  status: UserStatus
  roles: RoleCode[]
  student?: StudentProfileResponse
  lecturer?: LecturerProfileResponse
  lastLoginAt?: string
  lastLoginIp?: string
  createdAt: string
  version: number
}

export type CreateUserRequest = {
  email: string
  password: string
  fullName: string
  phone?: string
} & (
  | { roles: ['STUDENT']; student: Omit<StudentProfileResponse, 'id'>; lecturer?: never }
  | { roles: ['LECTURER']; lecturer: Omit<LecturerProfileResponse, 'id'>; student?: never }
  | { roles: ['ADMIN']; student?: never; lecturer?: never }
)

export interface UpdateUserRequest {
  version: number
  email: string
  fullName: string
  phone?: string
  student?: Omit<StudentProfileResponse, 'id'>
  lecturer?: Omit<LecturerProfileResponse, 'id'>
}

export interface AppointmentResponse {
  id: number
  lecturerId: number
  lecturerCode: string
  lecturerName: string
  facultyId: number
  departmentId?: number
  positionCode: PositionCode
  startsAt: string
  endsAt?: string
}

export interface CurrentUser {
  id: number
  fullName: string
  username: string
  roles: RoleCode[]
  student?: StudentProfileResponse
  lecturer?: LecturerProfileResponse
  activeAppointments: AppointmentResponse[]
}

export interface LecturerDirectoryResponse {
  id: number
  userId: number
  lecturerCode: string
  fullName: string
  academicDegree?: string
  specialization?: string
  facultyId: number
  facultyName: string
  departmentId: number
  departmentName: string
}

// Milestone & Progress
export type MilestoneType = 'PROPOSAL' | 'PROGRESS' | 'FINAL'
export type MilestoneStatus = 'OPEN' | 'SUBMITTED' | 'ACCEPTED' | 'REVISION_REQUIRED'

export interface CreateMilestoneRequest {
  assignmentId: number
  title: string
  milestoneType: MilestoneType
  description: string
  dueAt: string
}

export interface FileResponse {
  id: number
  originalName: string
  mimeType: string
  sizeBytes: number
  sha256: string
}

export interface UpdateMilestoneRequest {
  version: number
  title: string
  milestoneType: MilestoneType
  description: string
  dueAt: string
}

export interface MilestoneReminderResponse {
  recipientCount: number
  milestoneCount: number
}

export interface FeedbackResponse {
  id: number
  lecturerId: number
  lecturerName: string
  content: string
  createdAt: string
}

export interface FeedbackRequest {
  content: string
  accepted: boolean
}

export interface SubmissionResponse {
  id: number
  versionNo: number
  file?: FileResponse
  submittedBy: number
  submittedByName: string
  note?: string
  submittedAt: string
  feedback: FeedbackResponse[]
}

export interface MilestoneResponse {
  id: number
  assignmentId: number
  title: string
  milestoneType: MilestoneType
  description: string
  dueAt: string
  status: MilestoneStatus
  version: number
  submissions: SubmissionResponse[]
}

// Assessment & Rubrics
export type AssessmentComponentType = 'REVIEW' | 'COMMITTEE' | 'OTHER'

export interface CreateAssessmentComponentRequest {
  periodId: number
  code: string
  name: string
  componentType: AssessmentComponentType
}

export interface AssessmentComponentResponse {
  id: number
  periodId: number
  periodName: string
  code: string
  name: string
  componentType: AssessmentComponentType
  status: 'DRAFT' | 'LOCKED' | string
}

export interface CriterionRequest {
  name: string
  description: string
  maxScore: number
  sortOrder: number
}

export interface CreateRubricRequest {
  componentId: number
  name: string
  criteria: CriterionRequest[]
}

export interface CriterionResponse {
  id: number
  name: string
  description: string
  maxScore: number
  sortOrder: number
}

export interface RubricResponse {
  id: number
  componentId: number
  componentCode: string
  componentName: string
  name: string
  versionNo: number
  status: 'DRAFT' | 'LOCKED' | string
  criteria: CriterionResponse[]
}

// Evaluation Assignments
export type EvaluationStatus = 'ACTIVE' | 'CANCELLED'
export type ScoreSheetStatus = 'DRAFT' | 'SUBMITTED' | 'LOCKED'
export type DefenseStatus = 'SCHEDULED' | 'COMPLETED' | 'CANCELLED'

export interface CreateEvaluationRequest {
  assignmentId: number
  rubricId: number
  lecturerId: number
  defenseId?: number | null
  deadlineAt: string
}

export interface CreateDefenseRequest {
  assignmentId: number
  committeeId: number
  location: string
  startsAt: string
  endsAt: string
}

export interface EvaluationAssignmentResponse {
  id: number
  assignmentId: number
  periodId: number
  periodName: string
  componentId: number
  componentCode: string
  componentName: string
  rubricId: number
  rubricName: string
  rubricVersionNo: number
  lecturerId: number
  lecturerCode: string
  lecturerName: string
  evaluatorRole: string
  defenseId?: number
  groupId?: number
  groupName?: string
  topicId?: number
  topicCode?: string
  topicTitle?: string
  status: EvaluationStatus
  deadlineAt: string
  scoreSheetId?: number
  scoreSheetStatus?: ScoreSheetStatus
  submittedScore?: number | null
}

export interface ScoreRequest {
  criterionId: number
  score: number
  comment?: string
}

export interface SaveSheetRequest {
  version: number
  overallComment?: string
  scores: ScoreRequest[]
  submit: boolean
}

export interface ScoreSheetResponse {
  id: number | null
  evaluationId: number
  status: ScoreSheetStatus
  version: number
  totalScore: number
  overallComment?: string
  scores: ScoreRequest[]
}

export interface DefenseSessionResponse {
  id: number
  assignmentId: number
  groupId: number
  groupName: string
  topicId: number
  topicCode: string
  topicTitle: string
  committeeId: number
  committeeName: string
  periodId: number
  periodName: string
  location: string
  startsAt: string
  endsAt: string
  status: DefenseStatus
  version: number
}

// Academic Catalog & Periods
export interface FacultyResponse {
  active?: boolean
  id: number
  code: string
  name: string
}

export interface DepartmentResponse {
  active?: boolean
  id: number
  facultyId: number
  facultyName: string
  code: string
  name: string
}

export interface SemesterResponse {
  startsOn?: string
  endsOn?: string
  id: number
  semesterNo: number
  code: string
  name: string
  academicYear: string
}

export interface RegistrationPeriodResponse {
  id: number
  facultyId: number
  facultyName: string
  semesterId: number
  academicYear: string
  semesterNo: number
  name: string
  projectType: ProjectType
  status: 'DRAFT' | 'ACTIVE' | 'CLOSED' | string
  proposalStartAt: string
  proposalEndAt: string
  registrationStartAt: string
  registrationEndAt: string
  reviewDeadlineAt?: string
  defenseDate?: string
  minGroupMembers: number
  maxGroupMembers: number
  invitationExpiryHours: number
  minCommitteeMembers: number
  maxCommitteeMembers: number
  version: number
}

export type ProjectType = 'COURSE' | 'RESEARCH' | 'SPECIALIZED' | 'GRADUATION'
export interface CreateRegistrationPeriodRequest {
  facultyId: number
  semesterId: number
  name: string
  projectType: ProjectType
  proposalStartAt: string
  proposalEndAt: string
  registrationStartAt: string
  registrationEndAt: string
  reviewDeadlineAt?: string | null
  defenseDate?: string | null
  minGroupMembers?: number
  maxGroupMembers?: number
  invitationExpiryHours?: number
  minCommitteeMembers?: number
  maxCommitteeMembers?: number
}

export interface UpdateRegistrationPeriodRequest {
  version: number
  semesterId: number
  name: string
  projectType: ProjectType
  proposalStartAt: string
  proposalEndAt: string
  registrationStartAt: string
  registrationEndAt: string
  reviewDeadlineAt?: string | null
  defenseDate?: string | null
  minGroupMembers?: number
  maxGroupMembers?: number
  invitationExpiryHours?: number
  minCommitteeMembers?: number
  maxCommitteeMembers?: number
}

export interface CreateAppointmentRequest {
  lecturerId: number
  facultyId: number
  departmentId?: number | null
  positionCode: PositionCode
  startsAt: string
  endsAt?: string | null
}

export interface ProjectAssignmentResponse {
  version?: number
  id: number
  periodId: number
  periodName: string
  groupId: number
  groupName: string
  topicId: number
  topicCode: string
  topicTitle: string
  status: 'ASSIGNED' | 'IN_PROGRESS' | 'SUBMITTED' | 'COMPLETED' | 'CANCELLED' | string
}

export interface SupervisedStudentResponse {
  studentId: number
  studentCode: string
  fullName: string
  className: string
}

export interface SupervisedProjectResponse {
  project: ProjectAssignmentResponse
  students: SupervisedStudentResponse[]
}

export interface NotificationResponse {
  id: number
  title: string
  content: string
  targetPath?: string
  readAt?: string
  createdAt: string
}

export type NotificationScopeType = 'SYSTEM' | 'FACULTY' | 'LECTURER'
export type EmailNotificationEventType =
  | 'TASK_ASSIGNED'
  | 'TOPIC_APPROVED'
  | 'TOPIC_REJECTED'
  | 'DEADLINE_REMINDER'
  | 'REGISTRATION_OPENED'
  | 'RESULT_PUBLISHED'
export type EmailRecipientGroup =
  | 'DIRECT_PARTICIPANTS'
  | 'FACULTY_STUDENTS'
  | 'FACULTY_LECTURERS'
  | 'GROUP_STUDENTS'
  | 'TOPIC_STUDENTS'
  | 'SUPERVISORS'
  | 'ADMINS'
export type EmailOutboxStatus = 'PENDING' | 'SENDING' | 'SENT' | 'FAILED'

export interface EmailNotificationSettingResponse {
  id?: number
  eventType: EmailNotificationEventType
  enabled: boolean
  senderName: string
  fromEmail: string
  replyToEmail?: string
  subjectTemplate: string
  bodyTemplate: string
  recipientGroups: EmailRecipientGroup[]
  reminderHours?: number
  scopeType: NotificationScopeType
  scopeId: number
  updatedBy?: number
  updatedByName?: string
  updatedAt?: string
  version: number
  allowedVariables: string[]
}

export interface UpdateEmailNotificationSettingRequest {
  scopeType: NotificationScopeType
  scopeId: number
  enabled: boolean
  senderName: string
  fromEmail?: string
  replyToEmail?: string
  subjectTemplate: string
  bodyTemplate: string
  recipientGroups: EmailRecipientGroup[]
  reminderHours?: number
}

export interface PreviewEmailNotificationSettingRequest {
  scopeType: NotificationScopeType
  scopeId: number
  senderName?: string
  fromEmail?: string
  replyToEmail?: string
  subjectTemplate?: string
  bodyTemplate?: string
}

export interface EmailPreviewResponse {
  eventType: EmailNotificationEventType
  senderName: string
  fromEmail: string
  replyToEmail?: string
  subject: string
  body: string
  allowedVariables: string[]
  sampleDataUsed: boolean
}

export interface EmailTestQueuedResponse {
  outboxId: number
  recipientEmail: string
  status: EmailOutboxStatus
}

export interface EmailRecipientResponse {
  userId: number
  fullName: string
  email: string
}

export interface SendEmailRequest {
  recipientRoles: ('STUDENT' | 'LECTURER')[]
  recipientUserIds: number[]
}

export interface EmailQueuedResponse {
  recipientCount: number
}

export interface EmailTemplateResponse {
  id: number
  name: string
  senderName: string
  fromEmail: string
  replyToEmail?: string
  subjectTemplate: string
  bodyTemplate: string
  version: number
  updatedAt: string
}

export interface SaveEmailTemplateRequest {
  name: string
  senderName: string
  fromEmail?: string
  replyToEmail?: string
  subjectTemplate: string
  bodyTemplate: string
  version?: number
}

export interface EmailLogResponse {
  id: number
  eventType: EmailNotificationEventType
  eventKey: string
  recipientName: string
  recipientEmail: string
  subject: string
  status: EmailOutboxStatus
  attemptCount: number
  nextAttemptAt?: string
  sentAt?: string
  lastError?: string
  facultyId?: number
  relatedTopicId?: number
  relatedGroupId?: number
  relatedAssignmentId?: number
  createdAt: string
}

export type AnnouncementStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'

export interface AnnouncementResponse {
  id: number
  facultyId: number
  facultyName: string
  title: string
  content: string
  attachmentFileId?: number
  attachmentName?: string
  attachmentPath?: string
  createdBy: number
  createdByName: string
  status: AnnouncementStatus
  createdAt: string
  publishedAt?: string
}

export interface GroupMemberResponse {
  studentId: number
  userId: number
  studentCode: string
  fullName: string
  className: string
  cohort: string
  slotNo: number
  memberRole: 'LEADER' | 'MEMBER'
  joinedAt: string
}

export interface StudentGroupResponse {
  id: number
  periodId: number
  periodName: string
  name: string
  status: 'FORMING' | 'ACTIVE' | 'LOCKED' | 'CLOSED' | string
  createdBy: number
  creatorName: string
  createdAt: string
  version: number
  members: GroupMemberResponse[]
}

export type GroupInvitationStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'CANCELLED' | 'EXPIRED'

export interface GroupInvitationResponse {
  id: number
  groupId: number
  groupName: string
  periodId: number
  periodName: string
  invitedStudentId: number
  invitedStudentCode: string
  invitedStudentName: string
  invitedByStudentId: number
  invitedByName: string
  status: GroupInvitationStatus
  createdAt: string
  expiresAt: string
  respondedAt?: string
}

export interface TopicRegistrationResponse {
  departmentId?: number
  facultyId?: number
  id: number
  periodId: number
  periodName: string
  groupId: number
  groupName: string
  topicId: number
  topicCode: string
  topicTitle: string
  registeredBy: number
  registeredByName: string
  motivation?: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN' | string
  registeredAt: string
  reviewedBy?: number
  reviewedByName?: string
  reviewedAt?: string
  decisionReason?: string
  version: number
}

export type TopicStatus =
  'DRAFT' | 'PENDING' | 'APPROVED' | 'PUBLISHED' | 'REJECTED' | 'WITHDRAWN' | string
export interface TopicSupervisorResponse {
  lecturerId: number
  lecturerCode: string
  lecturerName: string
  slotNo: number
}
export interface TopicResponse {
  id: number
  periodId: number
  periodName: string
  facultyId: number
  facultyName: string
  departmentId: number
  departmentName: string
  availableForRegistration: boolean
  proposedBy: number
  reservedGroupId?: number
  latestReview?: { comment: string; decision: string }
  code: string
  title: string
  description: string
  objectives: string
  expectedOutcomes: string
  prerequisites?: string
  proposerName: string
  visibilityType: 'OPEN' | 'RESERVED' | string
  status: TopicStatus
  publishedAt?: string
  createdAt: string
  updatedAt: string
  version: number
  supervisors: TopicSupervisorResponse[]
}

export interface CreateRegistrationTopicRequest {
  periodId: number
  departmentId: number
  code: string
  title: string
  description: string
  objectives: string
  expectedOutcomes: string
  prerequisites?: string
  primaryLecturerId: number
  secondaryLecturerId?: number
}

export interface CommitteeMemberResponse {
  id: number
  lecturerId: number
  lecturerCode: string
  lecturerName: string
  slotNo: number
  role: 'CHAIR' | 'SECRETARY' | 'MEMBER'
  roleName?: string | null
}

export interface EvaluationCommitteeResponse {
  id: number
  periodId: number
  name: string
  status: 'DRAFT' | 'CONFIRMED' | 'COMPLETED'
  members: CommitteeMemberResponse[]
  rubricId?: number | null
  version: number
}

export interface SaveCommitteeRequest {
  periodId: number
  name: string
  rubricId: number
  members: { lecturerId: number; roleName: string }[]
  version?: number
}

export interface ReviewProgressResponse {
  assignedCount: number
  submittedCount: number
  draftCount: number
  notStartedCount: number
}

export type ResultStatus = 'FINALIZED' | 'PUBLISHED' | 'SUPERSEDED'

export interface ResultResponse {
  id: number
  assignmentId: number
  versionNo: number
  finalScore: number
  status: ResultStatus
}

export interface ResultDetailResponse extends ResultResponse {
  groupId: number
  groupName: string
  topicId: number
  topicCode: string
  topicTitle: string
  periodId: number
  periodName: string
  finalizedBy: number
  finalizedByName: string
  finalizedAt: string
  publishedBy?: number | null
  publishedByName?: string | null
  publishedAt?: string | null
  inputs: {
    id: number
    sheetId: number
    componentId: number
    componentCode: string
    sheetVersion: number
    scoreSnapshot: number
    rubricSnapshot: string
  }[]
  members: { studentId: number; studentCodeSnapshot: string; fullNameSnapshot: string }[]
}

export interface TopicInput {
  title: string
  description: string
  objectives: string
  expectedOutcomes: string
  prerequisites?: string
  visibilityType: 'OPEN' | 'RESERVED'
  reservedGroupId?: number | null
}
export interface CreateTopicRequest extends TopicInput {
  periodId: number
  code: string
}
export type TopicReviewDecision = 'APPROVED' | 'REVISION_REQUIRED' | 'REJECTED'
export interface GradeChangeResponse {
  id: number
  sheetId: number
  evaluationId: number
  assignmentId: number
  requestedBy: number
  requestedByName: string
  reason: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'APPLIED'
  reviewedBy?: number | null
  reviewedByName?: string | null
  reviewComment?: string | null
  createdAt: string
  reviewedAt?: string | null
  appliedAt?: string | null
}
export interface AuditLogResponse {
  id: number
  actorId?: number | null
  actorName?: string | null
  action: string
  entityType: string
  entityId?: number | null
  outcome: 'SUCCESS' | 'FAILURE'
  requestId?: string | null
  durationMs?: number | null
  createdAt: string
}
export interface EmailLogDetailResponse {
  log: EmailLogResponse
  attempts: {
    id: number
    attemptNo: number
    status: EmailOutboxStatus
    errorMessage?: string | null
    createdAt: string
  }[]
}
