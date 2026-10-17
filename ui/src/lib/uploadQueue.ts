import { sessionsApi } from '@/api/client';

export type FileOutcome = 'queued' | 'uploading' | 'added' | 'duplicate' | 'rejected' | 'failed';

const MB = 1024 * 1024;

/** Groups files so each request stays under the server's 110 MB request limit. */
export function chunkFiles(files: File[], maxFiles = 25, maxBytes = 100 * MB): File[][] {
  const chunks: File[][] = [];
  let current: File[] = [];
  let bytes = 0;
  for (const file of files) {
    if (current.length > 0 && (current.length >= maxFiles || bytes + file.size > maxBytes)) {
      chunks.push(current);
      current = [];
      bytes = 0;
    }
    current.push(file);
    bytes += file.size;
  }
  if (current.length > 0) chunks.push(current);
  return chunks;
}

export interface UploadSummary {
  added: number;
  duplicates: number;
}

export type OnUpdate = (fileName: string, outcome: FileOutcome, reason?: string) => void;

/**
 * Sends files chunk by chunk. A failed request marks its chunk `failed` and moves on, so one bad
 * request never loses the rest of the batch. The server reports duplicates only as a count, and
 * rejected ZIP entries under their own names, which the caller may not have queued.
 */
export async function uploadInChunks(
  sessionId: string,
  files: File[],
  onUpdate: OnUpdate
): Promise<UploadSummary> {
  const summary: UploadSummary = { added: 0, duplicates: 0 };
  for (const chunk of chunkFiles(files)) {
    chunk.forEach((f) => onUpdate(f.name, 'uploading'));
    try {
      const { data } = await sessionsApi.uploadResumes(sessionId, chunk);
      summary.added += data.uploadedCount;
      summary.duplicates += data.duplicateCount;
      const rejected = new Map(data.rejected.map((r) => [r.fileName, r.reason]));
      chunk.forEach((f) => {
        if (!rejected.has(f.name)) onUpdate(f.name, 'added');
      });
      rejected.forEach((reason, name) => onUpdate(name, 'rejected', reason));
    } catch (error) {
      const reason = error instanceof Error ? error.message : 'Upload failed';
      chunk.forEach((f) => onUpdate(f.name, 'failed', reason));
    }
  }
  return summary;
}
