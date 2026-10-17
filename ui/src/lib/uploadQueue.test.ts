import { describe, expect, it, vi } from 'vitest';
import { sessionsApi } from '@/api/client';
import { chunkFiles, uploadInChunks, type FileOutcome } from './uploadQueue';

vi.mock('@/api/client', () => ({ sessionsApi: { uploadResumes: vi.fn() } }));

const MB = 1024 * 1024;
const make = (count: number, size: number) =>
  Array.from({ length: count }, (_, i) => new File([new Uint8Array(size)], `r${i}.pdf`));

describe('chunkFiles', () => {
  it('splits at 25 files', () => expect(chunkFiles(make(60, 1)).map((c) => c.length)).toEqual([25, 25, 10]));
  it('splits at 100 MB', () => expect(chunkFiles(make(3, 40 * MB)).map((c) => c.length)).toEqual([2, 1]));
  it('puts an oversized file alone', () =>
    expect(chunkFiles([...make(1, 1), ...make(1, 150 * MB), ...make(1, 1)]).map((c) => c.length)).toEqual([1, 1, 1]));
  it('returns no chunks for no files', () => expect(chunkFiles([])).toEqual([]));
});

describe('uploadInChunks', () => {
  it('reports rejected entries, adds the rest, and keeps going after a failed chunk', async () => {
    const upload = vi.mocked(sessionsApi.uploadResumes);
    upload
      .mockResolvedValueOnce({
        data: { sessionId: 's1', uploadedCount: 23, candidateIds: [], duplicates: ['r1.pdf'],
          rejected: [{ fileName: 'r3.pdf', reason: 'Not a PDF' }, { fileName: 'notes.txt', reason: 'Not a PDF' }] },
      } as never)
      .mockRejectedValueOnce(new Error('Cannot reach the server'));
    const seen = new Map<string, FileOutcome>();

    const summary = await uploadInChunks('s1', make(30, 1), (name, outcome) => seen.set(name, outcome));

    expect(summary).toEqual({ added: 23, duplicates: 1 });
    expect(seen.get('r1.pdf')).toBe('duplicate');
    expect(seen.get('r3.pdf')).toBe('rejected');
    expect(seen.get('notes.txt')).toBe('rejected');
    expect(seen.get('r0.pdf')).toBe('added');
    expect(seen.get('r29.pdf')).toBe('failed');
  });
});
