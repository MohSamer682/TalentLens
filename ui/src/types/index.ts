export type ApiErrorCode =
  | 'VALIDATION_ERROR'
  | 'AUTHENTICATION_FAILED'
  | 'ACCESS_DENIED'
  | 'NOT_FOUND'
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

export type Role = 'ADMIN' | 'HR_CHIEF' | 'RECRUITER';

export type Permission =
  | 'SESSION_RUN'
  | 'SESSION_VIEW_ALL'
  | 'CANDIDATE_REVIEW'
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
}

export interface RoleInfo {
  role: Role;
  permissions: Permission[];
  editable: boolean;
  userCount: number;
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

export interface ScreeningSession {
  id: string;
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

export type ReviewStatus = 'NONE' | 'SHORTLISTED';

export interface ReviewResult {
  changed: number;
  shortlistedTotal: number;
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
}

export interface AnalyticsOverview {
  from: string;
  to: string;
  totals: AnalyticsTotals;
  weeks: { weekStart: string; sessionsCreated: number; resumesScored: number; shortlisted: number }[];
  scoreBands: { label: string; count: number }[];
  recruiters: {
    userId: string;
    fullName: string;
    sessions: number;
    resumesScored: number;
    shortlisted: number;
    medianHoursToRanked: number | null;
  }[];
}
