import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';
import type { Role, Weights } from '@/types';

export const ROLE_LABEL: Record<Role, string> = {
  ADMIN: 'Admin',
  HR_CHIEF: 'HR chief',
  RECRUITER: 'Recruiter',
  TECHNICAL_INTERVIEWER: 'Technical interviewer',
  HR_INTERVIEWER: 'HR interviewer',
};

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

export function formatDate(date: string) {
  return new Date(date).toLocaleDateString('en-GB', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  });
}

export function formatRelativeDate(date: string) {
  const days = Math.floor((Date.now() - new Date(date).getTime()) / 86_400_000);
  if (days <= 0) return 'Today';
  if (days === 1) return 'Yesterday';
  if (days < 7) return `${days} days ago`;
  if (days < 30) return `${Math.floor(days / 7)} wk ago`;
  if (days < 365) return `${Math.floor(days / 30)} mo ago`;
  return `${Math.floor(days / 365)} yr ago`;
}

export function getInitials(name: string) {
  return name
    .split(' ')
    .filter(Boolean)
    .map((part) => part[0])
    .join('')
    .toUpperCase()
    .slice(0, 2);
}

/** Scores arrive as 0–100 floats; one decimal, trailing .0 dropped. */
export function formatScore(value: number | null | undefined) {
  if (typeof value !== 'number' || !Number.isFinite(value)) return '–';
  return value.toFixed(1).replace(/\.0$/, '');
}

export const DIMENSIONS: { key: keyof Weights; label: string; short: string }[] = [
  { key: 'technicalSkills', label: 'Technical skills', short: 'Tech' },
  { key: 'experience', label: 'Experience', short: 'Exp' },
  { key: 'education', label: 'Education', short: 'Edu' },
  { key: 'softSkills', label: 'Soft skills', short: 'Soft' },
  { key: 'cultureFit', label: 'Culture fit', short: 'Culture' },
];

export const DEFAULT_WEIGHTS: Weights = {
  technicalSkills: 30,
  experience: 25,
  education: 15,
  softSkills: 20,
  cultureFit: 10,
};

