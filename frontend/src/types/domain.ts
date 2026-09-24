export type RoleCode = 'STUDENT' | 'LECTURER' | 'ADMIN'
export type PositionCode = 'DEAN' | 'DEPARTMENT_HEAD'

export type UserStatus = 'ACTIVE' | 'INACTIVE' | 'LOCKED'

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
  expectedEvaluators: number
}

export interface AssessmentComponentResponse {
  id: number
  periodId: number
  periodName: string
  code: string
  name: string
  componentType: AssessmentComponentType
  expectedEvaluators: number
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
  id: number
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
  id: number
  code: string
  name: string
}

export interface DepartmentResponse {
  id: number
  facultyId: number
  facultyName: string
  code: string
  name: string
}

export interface SemesterResponse {
  id: number
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
  minGroupMembers: number
  maxGroupMembers: number
  invitationExpiryHours: number
  minCommitteeMembers: number
  maxCommitteeMembers: number
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

export interface NotificationResponse {
  id: number
  title: string
  content: string
  targetPath?: string
  readAt?: string
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

export type TopicStatus = 'DRAFT' | 'PENDING' | 'APPROVED' | 'PUBLISHED' | 'REJECTED' | 'WITHDRAWN' | string
export interface TopicSupervisorResponse { lecturerId: number; lecturerCode: string; lecturerName: string; slotNo: number }
export interface TopicResponse {
  id: number; periodId: number; periodName: string; facultyId: number; facultyName: string; departmentId: number; departmentName: string
  code: string; title: string; description: string; objectives: string; expectedOutcomes: string; prerequisites?: string; proposerName: string
  visibilityType: 'PUBLIC' | 'PRIVATE' | string; status: TopicStatus; createdAt: string; updatedAt: string; version: number; supervisors: TopicSupervisorResponse[]
}
