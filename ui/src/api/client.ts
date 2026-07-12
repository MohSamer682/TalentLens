import axios from 'axios';
import { useAuthStore } from '@/store/authStore';
import type { User } from '@/types';
import type {
  AuthResponse,
  LoginRequest,
  RegisterRequest,
  ScreeningSession,
  CreateSessionRequest,
  ProgressResponse,
  CandidateResultDto,
  CandidateScore,
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

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      useAuthStore.getState().logout();
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export const authApi = {
  login: (data: LoginRequest) => api.post<AuthResponse>('/auth/login', data),
  register: (data: RegisterRequest) => api.post<AuthResponse>('/auth/register', data),
};

export const sessionsApi = {
  list: (page = 0, size = 20) =>
    api.get<{ content: ScreeningSession[]; totalElements: number }>('/sessions', {
      params: { page, size },
    }),
  get: (id: string) => api.get<ScreeningSession>(`/sessions/${id}`),
  create: (data: CreateSessionRequest) => api.post<ScreeningSession>('/sessions', data),
  update: (id: string, data: Partial<CreateSessionRequest>) =>
    api.patch<ScreeningSession>(`/sessions/${id}`, data),
  delete: (id: string) => api.delete(`/sessions/${id}`),
  uploadJd: (id: string, file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    return api.post(`/sessions/${id}/jd`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
  uploadResumes: (id: string, files: File[]) => {
    const formData = new FormData();
    files.forEach((file) => formData.append('files', file));
    return api.post(`/sessions/${id}/resumes`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
  start: (id: string) => api.post(`/sessions/${id}/start`),
  cancel: (id: string) => api.post(`/sessions/${id}/cancel`),
  progress: (id: string) => api.get<ProgressResponse>(`/sessions/${id}/progress`),
  candidates: (id: string, page = 0, size = 20) =>
    api.get<{ content: CandidateResultDto[]; totalElements: number }>(
      `/sessions/${id}/candidates`,
      { params: { page, size } }
    ),
  candidateDetail: (sessionId: string, candidateId: string) =>
    api.get<CandidateScore>(`/sessions/${sessionId}/candidates/${candidateId}`),
  export: (id: string) =>
    api.get<{ url: string }>(`/sessions/${id}/export`),
};

export const adminApi = {
  listUsers: (page = 0, size = 20) =>
    api.get<{ content: User[]; totalElements: number }>('/admin/users', {
      params: { page, size },
    }),
  updateRole: (userId: string, role: 'ADMIN' | 'RECRUITER') =>
    api.patch(`/admin/users/${userId}/role`, { role }),
};