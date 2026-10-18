import axios from 'axios';
import { useAuthStore } from '@/store/authStore';
import type {
  AnalyticsOverview,
  ApiError,
  ApiResponse,
  AuthResponse,
  CandidateNote,
  CandidateResultDto,
  CandidateScore,
  CreateSessionRequest,
  ExportResponse,
  LoginRequest,
  Page,
  ProgressResponse,
  RegisterRequest,
  ResumeDownloadResponse,
  ResumeFile,
  ReviewResult,
  Permission,
  ReviewStatus,
  Role,
  RoleInfo,
  RolesCatalog,
  ScoringScope,
  ScreeningSession,
  SessionActionResponse,
  UploadJdResponse,
  UploadResumesResponse,
  User,
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

api.interceptors.response.use(
  (response) => {
    if (isApiResponse(response.data) && response.data.success) {
      response.data = response.data.data;
    }
    return response;
  },
  (error) => {
    if (error.response?.status === 401) {
      useAuthStore.getState().logout();
      window.location.href = '/login';
    }

    const body = error.response?.data;
    if (isApiResponse(body)) {
      const apiErr = new ApiRequestError(
          body.message || 'Request failed',
          body.error?.code ?? 'INTERNAL_ERROR',
          body.error?.details,
          error.response?.status
        );
      reportClientError(apiErr);
      // A 403 usually means an admin changed this role's permissions; pick up the new set.
      if (apiErr.code === 'ACCESS_DENIED') void refreshCurrentUser();
      return Promise.reject(apiErr);
    }

    // No envelope: the request never reached the API, or a proxy answered instead
    return Promise.reject(
      new ApiRequestError(
        error.response
          ? `Unexpected response from server (HTTP ${error.response.status})`
          : 'Cannot reach the server. Check your connection and try again.',
        'NETWORK_ERROR',
        error.message,
        error.response?.status
      )
    );
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
  register: (data: RegisterRequest) => api.post<AuthResponse>('/auth/register', data),
  me: () => api.get<User>('/auth/me'),
};

/** Replaces the stored user with the server's current role and permissions. */
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

export const analyticsApi = {
  overview: (days: 30 | 90 | 365) => api.get<AnalyticsOverview>('/analytics/overview', { params: { days } }),
};

export const adminApi = {
  listUsers: (page = 0, size = 20) =>
    api.get<Page<User>>('/admin/users', { params: { page, size } }),
  updateRole: (userId: string, role: Role) =>
    api.patch<User>(`/admin/users/${userId}/role`, { role }),
  roles: () => api.get<RolesCatalog>('/admin/roles'),
  updatePermissions: (role: Role, permissions: Permission[]) =>
    api.put<RoleInfo>(`/admin/roles/${role}/permissions`, { permissions }),
};

function reportClientError(error: ApiRequestError) {
  if (error.code === 'NETWORK_ERROR') return;
  try {
    const payload = {
      code: error.code,
      message: error.message,
      url: window.location.href,
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
