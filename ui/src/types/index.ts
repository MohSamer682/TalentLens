export interface User {
  id: string;
  email: string;
  fullName: string;
  role: 'ADMIN' | 'RECRUITER';
  createdAt: string;
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
  lastErrorMessage?: string;
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
