import { describe, expect, it } from 'vitest';
import { refreshesUser } from './client';

describe('refreshesUser', () => {
  it('reloads the user after a permission change or a forced password change', () => {
    expect(refreshesUser('ACCESS_DENIED')).toBe(true);
    expect(refreshesUser('PASSWORD_CHANGE_REQUIRED')).toBe(true);
  });
  it('leaves the user alone for other errors', () => {
    expect(refreshesUser('VALIDATION_ERROR')).toBe(false);
    expect(refreshesUser('CONFLICT')).toBe(false);
  });
});
