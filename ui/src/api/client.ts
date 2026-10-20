import axios from 'axios';
import { useAuthStore } from '@/features/auth/authStore';
import type {
  ApplicationSummary,
  ApplicationView,
  RescheduleRequest,
  InvitePreview,
  IssuedInvite,
  PortalAccess,
  AnalyticsOverview,
  ApiError,
  ApiResponse,
  AuthResponse,
  CandidateInterview,
  CandidateNote,
  CandidateOffer,
  CandidateResultDto,
  CandidateScore,
  CandidateStageEvent,
  CreateSessionRequest,
  ExportResponse,
  InterviewKind,
  InterviewPlaceType,
  InterviewRecommendation,
  InterviewDetail,
  LoginRequest,
  MyInterviewsResponse,
  Page,
  PipelineCandidate,
  ProgressResponse,
  RegisterRequest,
  ResumeDownloadResponse,
  ResumeFile,
  ReviewResult,
  Permission,
  ReviewStatus,
  Role,
  RoleInfo,
  RoleHistoryItem,
  RolesCatalog,
  ScoringScope,
  ScreeningSession,
  SessionActionResponse,
  UploadJdResponse,
  UploadResumesResponse,
  User,
  AdminUser,
  ProfileFields,
  UserFormValues,
  UserRow,
  UserStatus,
} from '@/types';

const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

export const api = axios.create({
  baseURL: `${API_URL}/api`,
  headers: {
    'Content-Type': 'application/json',
  },
});

api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().token;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

function isApiResponse(value: unknown): value is ApiResponse<unknown> {
  return (
    typeof value === 'object' &&
    value !== null &&
    'success' in value &&
    'message' in value
  );
}

/**
 * Backend responses are always `{ success, message, data?, error? }`. Callers should never have
 * to reach through the envelope, so successful payloads are unwrapped to `response.data` here
 * and failures are normalized into a single ApiRequestError.
 */
export class ApiRequestError extends Error {
  readonly code: ApiError['code'] | 'NETWORK_ERROR';
  readonly details?: string;
  readonly status?: number;

  constructor(
    message: string,
    code: ApiError['code'] | 'NETWORK_ERROR',
    details?: string,
    status?: number
  ) {
    super(message);
    this.name = 'ApiRequestError';
    this.code = code;
    this.details = details;
    this.status = status;
  }
}

/** Console trace of every call; a text/html body means a proxy or the SPA answered instead of the API. */
function logExchange(
  config: { method?: string; baseURL?: string; url?: string } | undefined,
  response: { status: number; headers?: unknown; data: unknown } | undefined,
  error?: string
) {
  const url = `${config?.baseURL ?? ''}${config?.url ?? ''}`;
  const type = String((response?.headers as { 'content-type'?: string } | undefined)?.['content-type'] ?? '');
  const html = type.includes('text/html');
  const line = `[api] ${config?.method?.toUpperCase()} ${url} -> ${response?.status ?? 'NO RESPONSE'}`;
  if (error || html || (response && response.status >= 400)) {
    const body = typeof response?.data === 'string' ? response.data.slice(0, 300) : response?.data;
    console.error(line, html ? 'NON-JSON (text/html) RESPONSE' : '', error ?? '', body);
  } else {
    console.debug(line);
  }
}

api.interceptors.response.use(
  (response) => {
    logExchange(response.config, response);
    if (isApiResponse(response.data) && response.data.success) {
      response.data = response.data.data;
    }
    return response;
  },
  (error) => {
    logExchange(error.config, error.response, error.message);
    // A failed sign-in is a 401 too; only an expired or revoked session sends the user back to their sign-in page
    if (error.response?.status === 401 && useAuthStore.getState().token) {
      const { door, logout } = useAuthStore.getState();
      logout();
      window.location.href = door === 'candidate' ? '/login' : '/admins';
    }

    const body = error.response?.data;
    if (isApiResponse(body)) {
      const apiErr = new ApiRequestError(
          body.message || 'Request failed',
          body.error?.code ?? 'INTERNAL_ERROR',
          body.error?.details,
          error.response?.status
        );
      reportClientError(apiErr, requestSummary(error));
      if (refreshesUser(apiErr.code)) void refreshCurrentUser();
      return Promise.reject(apiErr);
    }

    // No envelope: the request never reached the API, or a proxy answered instead
    const nonApi = new ApiRequestError(
        error.response
          ? `Unexpected non-API response from server (HTTP ${error.response.status}, ${error.response.headers?.['content-type'] ?? 'unknown type'})`
          : 'Cannot reach the server. Check your connection and try again.',
        'NETWORK_ERROR',
        error.message,
        error.response?.status
      );
    reportClientError(nonApi, requestSummary(error));
    return Promise.reject(nonApi);
  }
);

/** Safe to call with anything a catch block hands you. */
export function apiErrorMessage(error: unknown, fallback = 'Something went wrong'): string {
  if (error instanceof ApiRequestError) {
    return error.message;
  }
  if (error instanceof Error && error.message) {
    return error.message;
  }
  return fallback;
}

export function apiErrorDetails(error: unknown): string | undefined {
  return error instanceof ApiRequestError ? error.details : undefined;
}

export const authApi = {
  login: (data: LoginRequest) => api.post<AuthResponse>('/auth/login', data),
  staffLogin: (data: LoginRequest) => api.post<AuthResponse>('/admins/auth/login', data),
  register: (data: RegisterRequest) => api.post<AuthResponse>('/auth/register', data),
  me: () => api.get<User>('/auth/me'),
};

/** Replaces the stored user with the server's current role and permissions. */
/** A 403 usually means an admin changed this role's permissions or set a temporary password; pick up the new state. */
export const refreshesUser = (code: string) => code === 'ACCESS_DENIED' || code === 'PASSWORD_CHANGE_REQUIRED';

export async function refreshCurrentUser() {
  if (!useAuthStore.getState().token) return;
  try {
    const { data } = await authApi.me();
    useAuthStore.getState().setUser(data);
  } catch {
    // a 401 signs out in the interceptor; anything else keeps the stored user until the next try
  }
}

export const sessionsApi = {
  list: (page = 0, size = 20, everyone = false) =>
    api.get<Page<ScreeningSession>>('/sessions', { params: { page, size, everyone } }),
  get: (id: string) => api.get<ScreeningSession>(`/sessions/${id}`),
  create: (data: CreateSessionRequest) => api.post<ScreeningSession>('/sessions', data),
  update: (id: string, data: Partial<CreateSessionRequest>) =>
    api.patch<ScreeningSession>(`/sessions/${id}`, data),
  delete: (id: string) => api.delete<SessionActionResponse>(`/sessions/${id}`),
  uploadJd: (id: string, file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    return api.post<UploadJdResponse>(`/sessions/${id}/jd`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
  uploadResumes: (id: string, files: File[]) => {
    const formData = new FormData();
    files.forEach((file) => formData.append('files', file));
    return api.post<UploadResumesResponse>(`/sessions/${id}/resumes`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
  start: (id: string, scope: ScoringScope = 'UNSCORED') =>
    api.post<SessionActionResponse>(`/sessions/${id}/start`, null, { params: { scope } }),
  resumes: (id: string, status?: ResumeFile['status'], page = 0, size = 50) =>
    api.get<Page<ResumeFile>>(`/sessions/${id}/resumes`, { params: { status, page, size } }),
  removeResume: (id: string, candidateId: string) =>
    api.delete<void>(`/sessions/${id}/candidates/${candidateId}`),
  cancel: (id: string) => api.post<SessionActionResponse>(`/sessions/${id}/cancel`),
  progress: (id: string) => api.get<ProgressResponse>(`/sessions/${id}/progress`),
  candidates: (id: string, page = 0, size = 20, review?: ReviewStatus) =>
    api.get<Page<CandidateResultDto>>(`/sessions/${id}/candidates`, {
      params: { page, size, review },
    }),
  candidateDetail: (sessionId: string, candidateId: string) =>
    api.get<CandidateScore>(`/sessions/${sessionId}/candidates/${candidateId}`),
  candidateResume: (sessionId: string, candidateId: string) =>
    api.get<ResumeDownloadResponse>(
      `/sessions/${sessionId}/candidates/${candidateId}/resume`
    ),
  export: (id: string) => api.get<ExportResponse>(`/sessions/${id}/export`),
};

export const candidatesApi = {
  review: (sessionId: string, candidateId: string, status: ReviewStatus) =>
    api.patch<ReviewResult>(`/sessions/${sessionId}/candidates/${candidateId}/review`, { status }),
  reviewMany: (sessionId: string, candidateIds: string[], status: ReviewStatus) =>
    api.post<ReviewResult>(`/sessions/${sessionId}/candidates/review`, { candidateIds, status }),
  shortlistTop: (sessionId: string, count: number) =>
    api.post<ReviewResult>(`/sessions/${sessionId}/shortlist/top`, { count }),
  notes: (sessionId: string, candidateId: string) =>
    api.get<CandidateNote[]>(`/sessions/${sessionId}/candidates/${candidateId}/notes`),
  addNote: (sessionId: string, candidateId: string, content: string) =>
    api.post<CandidateNote>(`/sessions/${sessionId}/candidates/${candidateId}/notes`, { content }),
  deleteNote: (sessionId: string, candidateId: string, noteId: string) =>
    api.delete<void>(`/sessions/${sessionId}/candidates/${candidateId}/notes/${noteId}`),
};

export const pipelineApi = {
  getTimeline: (sessionId: string, candidateId: string) =>
    api.get<CandidateStageEvent[]>(`/sessions/${sessionId}/candidates/${candidateId}/timeline`),
  getInterviews: (sessionId: string, candidateId: string) =>
    api.get<CandidateInterview[]>(`/sessions/${sessionId}/candidates/${candidateId}/interviews`),
  markContacted: (sessionId: string, candidateId: string) =>
    api.post<void>(`/sessions/${sessionId}/candidates/${candidateId}/contact`),
  scheduleInterview: (
    sessionId: string,
    candidateId: string,
    req: {
      kind: InterviewKind;
      interviewerId: string;
      scheduledStart: string;
      durationMinutes?: number;
      placeType: InterviewPlaceType;
      location?: string;
      noteToInterviewer?: string;
    }
  ) => api.post<CandidateInterview>(`/sessions/${sessionId}/candidates/${candidateId}/interviews`, req),
  rescheduleInterview: (
    sessionId: string,
    candidateId: string,
    interviewId: string,
    req: { scheduledStart: string; durationMinutes?: number; placeType?: InterviewPlaceType; location?: string }
  ) => api.patch<CandidateInterview>(`/sessions/${sessionId}/candidates/${candidateId}/interviews/${interviewId}`, req),
  cancelInterview: (sessionId: string, candidateId: string, interviewId: string) =>
    api.delete<void>(`/sessions/${sessionId}/candidates/${candidateId}/interviews/${interviewId}`),
  submitFeedback: (
    interviewId: string,
    req: { recommendation: InterviewRecommendation; rating: number; assessment: string }
  ) => api.post<CandidateInterview>(`/interviews/${interviewId}/feedback`, req),
  getInterview: (interviewId: string) => api.get<InterviewDetail>(`/interviews/${interviewId}`),
  myInterviews: () => api.get<MyInterviewsResponse>('/my-interviews'),
  moveToOffer: (sessionId: string, candidateId: string) =>
    api.post<void>(`/sessions/${sessionId}/candidates/${candidateId}/move-to-offer`),
  getInterviewPlan: (sessionId: string, candidateId: string) =>
    api.get<InterviewKind[]>(`/sessions/${sessionId}/candidates/${candidateId}/interview-plan`),
  setInterviewPlan: (sessionId: string, candidateId: string, kinds: InterviewKind[]) =>
    api.put<InterviewKind[]>(`/sessions/${sessionId}/candidates/${candidateId}/interview-plan`, { kinds }),
  rejectOffer: (sessionId: string, candidateId: string, note?: string) =>
    api.post<CandidateOffer>(`/sessions/${sessionId}/candidates/${candidateId}/offer/reject`, { note }),
  markOfferSent: (sessionId: string, candidateId: string) =>
    api.post<CandidateOffer>(`/sessions/${sessionId}/candidates/${candidateId}/offer/sent`),
  answerOffer: (sessionId: string, candidateId: string, accepted: boolean) =>
    api.post<CandidateOffer>(`/sessions/${sessionId}/candidates/${candidateId}/offer/answer`, { accepted }),
  saveJoining: (sessionId: string, candidateId: string, joiningDate: string, notes?: string) =>
    api.put<CandidateOffer>(`/sessions/${sessionId}/candidates/${candidateId}/joining`, { joiningDate, notes }),
  uploadOffer: (sessionId: string, candidateId: string, file: File, requiresDirectorApproval = false) => {
    const data = new FormData();
    data.append('file', file);
    data.append('requiresDirectorApproval', String(requiresDirectorApproval));
    return api.post<CandidateOffer>(`/sessions/${sessionId}/candidates/${candidateId}/offer`, data, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
  getOffer: (sessionId: string, candidateId: string) =>
    api.get<CandidateOffer | null>(`/sessions/${sessionId}/candidates/${candidateId}/offer`),
  downloadOffer: (sessionId: string, candidateId: string) =>
    api.get<{ url: string }>(`/sessions/${sessionId}/candidates/${candidateId}/offer/download`),
  approveOffer: (sessionId: string, candidateId: string) =>
    api.post<CandidateOffer>(`/sessions/${sessionId}/candidates/${candidateId}/offer/approve`),
  rescheduleRequests: (sessionId: string, candidateId: string) =>
    api.get<RescheduleRequest[]>(`/sessions/${sessionId}/candidates/${candidateId}/reschedule-requests`),
  acceptReschedule: (sessionId: string, candidateId: string, requestId: string, scheduledStart: string, version: number) =>
    api.post<RescheduleRequest>(`/sessions/${sessionId}/candidates/${candidateId}/reschedule-requests/${requestId}/accept`, {
      scheduledStart,
      version,
    }),
  declineReschedule: (sessionId: string, candidateId: string, requestId: string, note: string, version: number) =>
    api.post<RescheduleRequest>(`/sessions/${sessionId}/candidates/${candidateId}/reschedule-requests/${requestId}/decline`, {
      note,
      version,
    }),
  getPipeline: (awaitingApprovalOnly?: boolean) =>
    api.get<PipelineCandidate[]>('/pipeline', { params: { awaitingApprovalOnly } }),
  getInterviewers: (kind?: InterviewKind) =>
    api.get<Array<{ id: string; name: string; email: string; role: Role }>>('/interviewers', {
      params: { kind },
    }),
};

export const analyticsApi = {
  overview: (days: 30 | 90 | 365) => api.get<AnalyticsOverview>('/analytics/overview', { params: { days } }),
};

export const profileApi = {
  get: () => api.get<AdminUser>('/me/profile'),
  update: (data: ProfileFields & { fullName: string; version: number }) => api.put<AdminUser>('/me/profile', data),
  changePassword: (currentPassword: string, newPassword: string) =>
    api.put<null>('/me/password', { currentPassword, newPassword }),
};

export const portalApi = {
  access: (sessionId: string, candidateId: string) =>
    api.get<PortalAccess>(`/sessions/${sessionId}/candidates/${candidateId}/portal`),
  issueInvite: (sessionId: string, candidateId: string) =>
    api.post<IssuedInvite>(`/sessions/${sessionId}/candidates/${candidateId}/portal/invites`),
  revokeInvite: (sessionId: string, candidateId: string) =>
    api.delete<null>(`/sessions/${sessionId}/candidates/${candidateId}/portal/invites`),
  createAccount: (sessionId: string, candidateId: string, temporaryPassword: string) =>
    api.post<PortalAccess>(`/sessions/${sessionId}/candidates/${candidateId}/portal/account`, { temporaryPassword }),
  // The token goes in the body so it never shows up in a proxy or access log
  preview: (token: string) => api.post<InvitePreview>('/portal/invites/preview', { token }),
  accept: (token: string, password: string) => api.post<AuthResponse>('/portal/invites/accept', { token, password }),
  claim: (token: string) => api.post<null>('/portal/invites/claim', { token }),
  applications: (page = 0) => api.get<Page<ApplicationSummary>>('/portal/applications', { params: { page, size: 20 } }),
  application: (id: string) => api.get<ApplicationView>(`/portal/applications/${encodeURIComponent(id)}`),
  requestReschedule: (interviewId: string, reason: string, proposedStarts: string[]) =>
    api.post<RescheduleRequest>(`/portal/interviews/${encodeURIComponent(interviewId)}/reschedule-requests`, { reason, proposedStarts }),
  withdrawReschedule: (requestId: string) =>
    api.post<RescheduleRequest>(`/portal/reschedule-requests/${encodeURIComponent(requestId)}/withdraw`),
};

export const adminApi = {
  listUsers: (params: { page?: number; size?: number; q?: string; status?: UserStatus; role?: Role } = {}) =>
    api.get<Page<UserRow>>('/admin/users', { params: { size: 20, ...params } }),
  getUser: (id: string) => api.get<AdminUser>(`/admin/users/${id}`),
  createUser: (data: UserFormValues) => api.post<AdminUser>('/admin/users', data),
  updateUser: (id: string, data: UserFormValues & { version: number }) => api.put<AdminUser>(`/admin/users/${id}`, data),
  setStatus: (id: string, status: UserStatus) => api.patch<AdminUser>(`/admin/users/${id}/status`, { status }),
  resetPassword: (id: string, temporaryPassword: string) =>
    api.post<AdminUser>(`/admin/users/${id}/password`, { temporaryPassword }),
  deleteUser: (id: string) => api.delete<null>(`/admin/users/${id}`),
  updateRole: (userId: string, role: Role) =>
    api.patch<User>(`/admin/users/${userId}/role`, { role }),
  roles: () => api.get<RolesCatalog>('/admin/roles'),
  role: (role: Role) => api.get<RoleInfo>(`/admin/roles/${role}`),
  renameRole: (role: Role, data: { name: string; description: string; version: number }) =>
    api.patch<RoleInfo>(`/admin/roles/${role}`, data),
  updatePermissions: (role: Role, permissions: Permission[], version: number) =>
    api.put<RoleInfo>(`/admin/roles/${role}/permissions`, { permissions, version }),
  roleMembers: (role: Role, params: { q?: string; page?: number; size?: number } = {}) =>
    api.get<Page<UserRow>>(`/admin/roles/${role}/members`, { params: { size: 20, ...params } }),
  moveMembers: (role: Role, userIds: string[]) => api.post<RoleInfo>(`/admin/roles/${role}/members`, { userIds }),
  roleHistory: (role: Role, page = 0) => api.get<Page<RoleHistoryItem>>(`/admin/roles/${role}/history`, { params: { page, size: 20 } }),
};

/** "METHOD url -> status body" so the server log shows what the browser actually received. */
function requestSummary(error: { config?: { method?: string; baseURL?: string; url?: string }; response?: { status: number; data: unknown } }) {
  const { config, response } = error;
  const body = typeof response?.data === 'string' ? response.data : JSON.stringify(response?.data ?? '');
  return `${config?.method?.toUpperCase()} ${config?.baseURL ?? ''}${config?.url ?? ''} -> ${response?.status ?? 'no response'} ${body.slice(0, 300)}`;
}

function reportClientError(error: ApiRequestError, request: string) {
  // Nothing reached the API, so there is nothing to deliver the report to
  if (error.code === 'NETWORK_ERROR' && error.status === undefined) return;
  try {
    const payload = {
      code: error.code,
      message: error.message,
      url: window.location.href,
      request,
      stack: error.stack,
    };
    navigator.sendBeacon(
      `${API_URL}/api/client-errors`,
      new Blob([JSON.stringify(payload)], { type: 'application/json' })
    );
  } catch {
    // best-effort
  }
}
