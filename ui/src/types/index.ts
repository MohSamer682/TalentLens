export type ApiErrorCode =
  | 'VALIDATION_ERROR'
  | 'AUTHENTICATION_FAILED'
  | 'ACCESS_DENIED'
  | 'NOT_FOUND'
  | 'CONFLICT'
  | 'GONE'
  | 'RATE_LIMITED'
  | 'WRONG_PORTAL'
  | 'PASSWORD_CHANGE_REQUIRED'
  | 'MINIO_UNAVAILABLE'
  | 'BATCH_WORKER_UNAVAILABLE'
  | 'LLM_CONFIGURATION_ERROR'
  | 'LLM_PROVIDER_ERROR'
  | 'LLM_RESPONSE_ERROR'
  | 'INTERNAL_ERROR';

export interface ApiError {
  code: ApiErrorCode;
  details: string;
}

/** Every backend endpoint returns this envelope; the Axios interceptor unwraps `data`. */
export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data?: T;
  error?: ApiError;
}

export interface UploadJdResponse {
  sessionId: string;
  jdFileKey: string;
  fileName: string;
  sizeBytes: number;
}

export interface UploadResumesResponse {
  sessionId: string;
  uploadedCount: number;
  candidateIds: string[];
  duplicates: string[];
  rejected: { fileName: string; reason: string }[];
}

export type ScoringScope = 'UNSCORED' | 'ALL';

export interface ResumeFile {
  id: string;
  name: string;
  originalFileName: string | null;
  status: 'PENDING' | 'SCORED' | 'FAILED';
  failureReason: string | null;
  createdAt: string;
}

export interface SessionActionResponse {
  sessionId: string;
  status?: 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED';
  message: string;
}

export interface ExportResponse {
  sessionId: string;
  url: string;
  expiresInMinutes: number;
}

export interface ResumeDownloadResponse {
  candidateId: string;
  fileName: string;
  url: string;
  expiresInMinutes: number;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export type Role =
  | 'ADMIN'
  | 'HR_CHIEF'
  | 'RECRUITER'
  | 'TECHNICAL_INTERVIEWER'
  | 'HR_INTERVIEWER'
  | 'DEPARTMENT_DIRECTOR'
  | 'CEO'
  | 'CHAIRMAN'
  | 'CANDIDATE';

export type Permission =
  | 'SESSION_RUN'
  | 'SESSION_VIEW_ALL'
  | 'CANDIDATE_REVIEW'
  | 'PIPELINE_MANAGE'
  | 'OFFER_APPROVE'
  | 'INTERVIEW_FEEDBACK'
  | 'ANALYTICS_VIEW'
  | 'USER_MANAGE'
  | 'ROLE_MANAGE'
  | 'AUDIT_VIEW';

export interface User {
  id: string;
  email: string;
  fullName: string;
  role: Role;
  createdAt: string;
  /** Only on the signed-in user (login, register, /auth/me) */
  permissions?: Permission[];
  mustChangePassword?: boolean;
}

export type UserStatus = 'ACTIVE' | 'HIDDEN';

export interface ProfileFields {
  phone?: string;
  headline?: string;
  department?: string;
  location?: string;
  bio?: string;
}

/** A user with their profile, as the profile and admin user endpoints return it */
export interface AdminUser extends ProfileFields {
  id: string;
  email: string;
  fullName: string;
  role: Role;
  status: UserStatus;
  mustChangePassword: boolean;
  createdAt: string;
  version: number;
}

export interface UserRow {
  id: string;
  email: string;
  fullName: string;
  role: Role;
  status: UserStatus;
  headline?: string;
  createdAt: string;
}

export interface UserFormValues extends ProfileFields {
  email: string;
  fullName: string;
  role: Role;
  temporaryPassword?: string;
}

export interface RoleInfo {
  role: Role;
  name: string;
  description: string | null;
  kind: 'STAFF' | 'CANDIDATE';
  permissions: Permission[];
  /** False for Admin (every permission) and Candidate (none) */
  editable: boolean;
  /** Active accounts only */
  memberCount: number;
  /** What the workflow relies on this role for; fixed in code */
  uses: string[];
  version: number;
  updatedAt: string | null;
}

export interface RoleHistoryItem {
  id: string;
  action: 'ROLE_UPDATE' | 'ROLE_PERMISSIONS_CHANGE' | 'ROLE_MEMBERS_MOVED' | string;
  actorEmail: string | null;
  details: string | null;
  occurredAt: string;
}

export interface RolesCatalog {
  permissions: { name: Permission; description: string; adminOnly: boolean }[];
  roles: RoleInfo[];
}

export interface Weights {
  technicalSkills: number;
  experience: number;
  education: number;
  softSkills: number;
  cultureFit: number;
}

export interface JobDetails {
  title: string;
  positionCode?: string;
  description?: string;
  /** Absent on session lists */
  requiredSkills?: string[];
}

export interface ScreeningSession {
  id: string;
  job?: JobDetails;
  title: string;
  description?: string;
  status: 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED';
  jdFileKey?: string;
  createdAt: string;
  completedAt?: string;
  weights: Weights;
  candidateCount: number;
  reportFileKey?: string;
  shortlistedCount?: number;
  ownerId?: string;
  /** Only when listing everyone's sessions */
  ownerName?: string;
  lastErrorMessage?: string;
  /** Present on the session detail response only */
  llm?: LlmStatus;
}

export type LlmState =
  | 'AVAILABLE'
  | 'REACHABLE'
  | 'MODEL_NOT_FOUND'
  | 'AUTH_FAILED'
  | 'UNREACHABLE'
  | 'NOT_CONFIGURED'
  | 'UNKNOWN';

export interface LlmStatus {
  model?: string;
  provider?: string;
  state: LlmState;
  available: boolean;
  message?: string;
  checkedAt?: string;
}

export interface Candidate {
  id: string;
  name: string;
  email: string;
  resumeFileKey: string;
  status: 'PENDING' | 'SCORED' | 'FAILED';
  skills: string[];
  compositeScore?: number;
  rank?: number;
}

export interface CandidateScore {
  id: string;
  candidateId: string;
  name: string;
  email: string;
  technicalSkills: number;
  experience: number;
  education: number;
  softSkills: number;
  cultureFit: number;
  compositeScore: number;
  rank: number;
  skills: string[];
  justification: string;
  strengths: string;
  gaps: string;
  weights?: Weights;
  reviewStatus: ReviewStatus;
}

export type ReviewStatus =
  | 'NONE'
  | 'SHORTLISTED'
  | 'CONTACTED'
  | 'INTERVIEW_SCHEDULED'
  | 'INTERVIEWED'
  | 'OFFER'
  | 'OFFER_APPROVED'
  | 'OFFER_SENT'
  | 'ACCEPTED'
  | 'DECLINED'
  | 'JOINING_SET';

export type InterviewKind = 'HR' | 'TECHNICAL' | 'MANAGEMENT';
export type InterviewPlaceType = 'HQ' | 'ONLINE' | 'OTHER';
export type InterviewStatus = 'SCHEDULED' | 'COMPLETED' | 'CANCELLED';
export type InterviewRecommendation = 'STRONG_HIRE' | 'HIRE' | 'NO_HIRE' | 'STRONG_NO_HIRE';

export interface CandidateInterview {
  id: string;
  candidateId: string;
  kind: InterviewKind;
  interviewerId: string;
  interviewerName: string;
  interviewerRole: string | null;
  scheduledStart: string;
  durationMinutes: number;
  placeType: InterviewPlaceType;
  location: string | null;
  noteToInterviewer: string | null;
  status: InterviewStatus;
  recommendation: InterviewRecommendation | null;
  rating: number | null;
  feedbackSubmittedAt: string | null;
  feedbackAssessment: string | null;
  createdById: string | null;
  createdAt: string;
}

export interface CandidateOffer {
  id: string;
  candidateId: string;
  fileName: string;
  sizeBytes: number;
  uploadedById: string | null;
  uploadedByName: string;
  uploadedAt: string;
  approvedById: string | null;
  approvedByName: string | null;
  approvedAt: string | null;
  isApproved: boolean;
  requiresDirectorApproval: boolean;
  directorApprovedByName: string | null;
  directorApprovedAt: string | null;
  rejectedByName: string | null;
  rejectedAt: string | null;
  rejectionNote: string | null;
  sentAt: string | null;
  candidateResponse: 'ACCEPTED' | 'DECLINED' | null;
  respondedAt: string | null;
  joiningDate: string | null;
  joiningNotes: string | null;
}

export interface CandidateStageEvent {
  id: string;
  candidateId: string;
  stage: string;
  actorId: string | null;
  actorName: string | null;
  summary: string;
  createdAt: string;
}

export interface PipelineCandidate {
  id: string;
  sessionId: string;
  sessionTitle: string;
  name: string;
  email: string;
  score: number | null;
  stage: ReviewStatus;
  inStageDays: number;
  nextStep: string;
  stageChangedAt: string;
  offerFileName?: string;
  offerUploaderName?: string;
  offerUploadedById?: string;
  /** Who must act on the offer next; null when no approval is pending */
  offerStep?: 'DIRECTOR' | 'HR_CHIEF' | 'REJECTED' | null;
  /** The candidate asked to move an interview and nobody has answered yet */
  rescheduleRequested?: boolean;
}

export interface MyInterviewItem {
  id: string;
  candidateId: string;
  candidateName: string;
  role: string;
  kind: InterviewKind;
  scheduledStart: string;
  durationMinutes: number;
  placeType: InterviewPlaceType;
  location: string | null;
  status: InterviewStatus;
  recommendation: InterviewRecommendation | null;
  rating: number | null;
  feedbackSubmittedAt: string | null;
}

export interface MyInterviewsResponse {
  waiting: MyInterviewItem[];
  upcoming: MyInterviewItem[];
  done: MyInterviewItem[];
}

export interface InterviewDetail {
  id: string;
  candidateId: string;
  candidateName: string;
  candidateEmail: string;
  sessionId: string;
  sessionTitle: string;
  recruiterName: string | null;
  kind: InterviewKind;
  interviewerId: string;
  interviewerName: string;
  scheduledStart: string;
  durationMinutes: number;
  placeType: InterviewPlaceType;
  location: string | null;
  noteToInterviewer: string | null;
  status: InterviewStatus;
  recommendation: InterviewRecommendation | null;
  rating: number | null;
  feedbackAssessment: string | null;
  feedbackSubmittedAt: string | null;
  compositeScore: number | null;
  rank: number | null;
  strengths: string | null;
  gaps: string | null;
  resumeUrl: string | null;
}

export interface ReviewResult {
  changed: number;
  shortlistedTotal: number;
}

export interface CandidateNote {
  id: string;
  candidateId: string;
  authorId: string | null;
  authorName: string;
  authorRole: Role | string | null;
  content: string;
  createdAt: string;
  deletable: boolean;
}

export interface ProgressResponse {
  total: number;
  processed: number;
  failed: number;
  status: string;
  percent: number;
}

export interface CandidateResultDto {
  id: string;
  rank: number;
  name: string;
  email: string;
  compositeScore: number;
  reviewStatus: ReviewStatus;
  technicalSkills: number;
  experience: number;
  education: number;
  softSkills: number;
  cultureFit: number;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  fullName: string;
}

export interface AuthResponse {
  token: string;
  user: User;
}

export interface CreateSessionRequest {
  title: string;
  description?: string;
  weights: Weights;
  jobTitle: string;
  positionCode: string;
  jobDescription?: string;
  requiredSkills: string[];
}

export interface AnalyticsTotals {
  sessions: number;
  sessionsByStatus: Record<ScreeningSession['status'], number>;
  resumesUploaded: number;
  resumesScored: number;
  resumesUnreadable: number;
  shortlisted: number;
  shortlistRate: number | null;
  medianHoursToRanked: number | null;
  interviewsCompleted?: number;
  offersApproved?: number;
  medianDaysShortlistToOffer?: number | null;
}

export interface AnalyticsPriorTotals {
  resumesScored: number;
  shortlistRate: number | null;
  interviewsCompleted: number;
  offersApproved: number;
  medianDaysShortlistToOffer: number | null;
}

export interface FunnelStep {
  stage: string;
  label: string;
  count: number;
  conversionRate: number | null;
  dropOff: number;
}

export interface StageWait {
  stage: string;
  label: string;
  medianDays: number | null;
  p90Days: number | null;
}

export interface InterviewerStat {
  userId: string;
  fullName: string;
  role: string;
  interviewsDone: number;
  medianHoursToFeedback: number | null;
  meanRating: number | null;
  strongHireCount: number;
  hireCount: number;
  noHireCount: number;
  strongNoHireCount: number;
}

export interface AnalyticsOverview {
  from: string;
  to: string;
  totals: AnalyticsTotals;
  priorTotals?: AnalyticsPriorTotals | null;
  funnel?: FunnelStep[];
  stageWaits?: StageWait[];
  weeks: {
    weekStart: string;
    sessionsCreated: number;
    resumesScored: number;
    shortlisted: number;
    interviewsCompleted?: number;
    offersApproved?: number;
  }[];
  scoreBands: {
    label: string;
    count: number;
    shortlistRate?: number | null;
    hireRate?: number | null;
    interviewed?: number;
  }[];
  interviewers?: InterviewerStat[];
  recruiters: {
    userId: string;
    fullName: string;
    sessions: number;
    resumesScored: number;
    shortlisted: number;
    medianHoursToRanked: number | null;
    contacted?: number;
    offers?: number;
    shortlistToOfferRate?: number | null;
  }[];
}

export interface PortalAccess {
  account?: { email: string; createdAt: string; mustChangePassword: boolean; active: boolean };
  invite?: { createdAt: string; expiresAt: string };
}

export interface IssuedInvite {
  url: string;
  expiresAt: string;
}

export interface InvitePreview {
  jobTitle: string;
  candidateName: string;
  accountExists: boolean;
}

export type PortalStage = 'APPLIED' | 'SHORTLISTED' | 'INTERVIEWING' | 'DECISION_PENDING' | 'OFFER' | 'ACCEPTED' | 'JOINING' | 'CLOSED';
export type StepState = 'PLANNED' | 'SCHEDULED' | 'DONE' | 'CANCELLED';

export interface StageStep {
  kind: InterviewKind;
  state: StepState;
}

export interface PortalInterview {
  id: string;
  kind: InterviewKind;
  interviewerName: string;
  interviewerHeadline: string | null;
  scheduledStart: string;
  durationMinutes: number;
  placeType: InterviewPlaceType;
  location: string | null;
  status: InterviewStatus;
  /** The latest reschedule request on this interview, if the candidate ever made one */
  reschedule: PortalReschedule | null;
}

export type RescheduleStatus = 'PENDING' | 'ACCEPTED' | 'DECLINED' | 'WITHDRAWN';

export interface PortalReschedule {
  id: string;
  status: RescheduleStatus;
  proposedStarts: string[];
  resolutionNote: string | null;
  createdAt: string;
}

export interface RescheduleRequest {
  id: string;
  interviewId: string;
  status: RescheduleStatus;
  reason: string;
  proposedStarts: string[];
  resolutionNote: string | null;
  createdAt: string;
  resolvedAt: string | null;
  version: number;
  requestedByName: string;
}

export interface ApplicationSummary {
  id: string;
  jobTitle: string;
  positionCode: string | null;
  stage: PortalStage;
  nextInterviewAt: string | null;
}

export interface ApplicationView {
  id: string;
  job: JobDetails;
  stage: PortalStage;
  steps: StageStep[];
  interviews: PortalInterview[];
  recruiter: { name: string; headline: string | null };
}
