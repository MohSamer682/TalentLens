import { describe, it, expect } from 'vitest';
import { jobSchema } from './jobDetails';

const valid = { jobTitle: 'Senior Backend Engineer', positionCode: 'eng-be-104', jobDescription: '', requiredSkills: ['Java'] };

describe('jobSchema', () => {
  it('uppercases and trims the position code', () => {
    expect(jobSchema.parse({ ...valid, positionCode: ' eng-be-104 ' }).positionCode).toBe('ENG-BE-104');
  });

  it('rejects a code with spaces or other symbols', () => {
    const r = jobSchema.safeParse({ ...valid, positionCode: 'BE 104' });
    expect(r.success).toBe(false);
    expect(r.error?.issues[0].message).toBe('Position code can use letters, numbers and dashes');
  });

  it('requires a code and at least one skill', () => {
    const r = jobSchema.safeParse({ ...valid, positionCode: '', requiredSkills: [] });
    expect(r.error?.issues.map((i) => i.message)).toEqual(['Position code is required', 'Add at least one required skill']);
  });
});
