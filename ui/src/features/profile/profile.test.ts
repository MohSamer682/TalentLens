import { describe, it, expect } from 'vitest';
import { passwordSchema, profileSchema } from './profile';

const valid = { fullName: 'Karim Adel', phone: '+20 100 123 4567', headline: '', department: '', location: '', bio: '' };

describe('profileSchema', () => {
  it('accepts a full profile and an empty phone', () => {
    expect(profileSchema.safeParse(valid).success).toBe(true);
    expect(profileSchema.safeParse({ ...valid, phone: '' }).success).toBe(true);
  });
  it('mirrors the server limits', () => {
    expect(profileSchema.safeParse({ ...valid, fullName: ' ' }).success).toBe(false);
    expect(profileSchema.safeParse({ ...valid, phone: 'call me' }).error?.issues[0].message).toBe('Use digits, spaces, +, - and brackets');
    expect(profileSchema.safeParse({ ...valid, bio: 'x'.repeat(1001) }).success).toBe(false);
    expect(profileSchema.safeParse({ ...valid, headline: 'x'.repeat(121) }).success).toBe(false);
  });
});

describe('passwordSchema', () => {
  it('needs 10 characters, a match, and a change', () => {
    expect(passwordSchema.safeParse({ currentPassword: 'old', newPassword: 'short', confirm: 'short' }).success).toBe(false);
    expect(passwordSchema.safeParse({ currentPassword: 'old', newPassword: 'LongEnough1', confirm: 'LongEnough2' }).error?.issues[0].message)
      .toBe("The passwords don't match");
    expect(passwordSchema.safeParse({ currentPassword: 'SamePass123', newPassword: 'SamePass123', confirm: 'SamePass123' }).success).toBe(false);
    expect(passwordSchema.safeParse({ currentPassword: 'old', newPassword: 'LongEnough1', confirm: 'LongEnough1' }).success).toBe(true);
  });
});
