import { z } from 'zod';

// Mirrors UpdateProfileRequest and ChangePasswordRequest on the API.
const optional = (max: number, message: string) => z.string().trim().max(max, message).optional();

export const profileSchema = z.object({
  fullName: z.string().trim().min(1, 'Your name is required').max(120, 'Keep your name under 120 characters'),
  phone: z
    .string()
    .trim()
    .refine((v) => v === '' || /^[+0-9 ()-]{6,32}$/.test(v), 'Use digits, spaces, +, - and brackets')
    .optional(),
  headline: optional(120, 'Keep the job title under 120 characters'),
  department: optional(80, 'Keep the department under 80 characters'),
  location: optional(120, 'Keep the location under 120 characters'),
  bio: optional(1000, 'Keep the bio under 1,000 characters'),
});

export type ProfileForm = z.infer<typeof profileSchema>;

export const passwordSchema = z
  .object({
    currentPassword: z.string().min(1, 'Enter your current password'),
    newPassword: z.string().min(10, 'Use at least 10 characters').max(128),
    confirm: z.string(),
  })
  .refine((v) => v.newPassword === v.confirm, { message: "The passwords don't match", path: ['confirm'] })
  .refine((v) => v.newPassword !== v.currentPassword, {
    message: 'Choose a password you have not used here',
    path: ['newPassword'],
  });

export type PasswordForm = z.infer<typeof passwordSchema>;
