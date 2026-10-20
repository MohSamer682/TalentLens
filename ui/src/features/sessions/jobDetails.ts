import { z } from 'zod';

// Mirrors CreateSessionRequest on the API, so a bad value is caught before anything is sent.
export const jobSchema = z.object({
  jobTitle: z.string().trim().min(1, 'Job title is required').max(160, 'Keep the job title under 160 characters'),
  positionCode: z
    .string()
    .trim()
    .min(1, 'Position code is required')
    .pipe(z.string().regex(/^[A-Za-z0-9][A-Za-z0-9-]{1,39}$/, 'Position code can use letters, numbers and dashes'))
    .transform((code) => code.toUpperCase()),
  jobDescription: z.string().max(20000, 'Keep the job description under 20,000 characters').optional(),
  requiredSkills: z.array(z.string().max(80)).min(1, 'Add at least one required skill').max(30),
});

export type JobForm = z.input<typeof jobSchema>;
