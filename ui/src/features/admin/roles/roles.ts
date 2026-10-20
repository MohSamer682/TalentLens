import { PERMISSION_LABEL } from '@/lib/permissions';
import { ROLE_LABEL } from '@/lib/utils';

const LABELS: Record<string, string> = { ...ROLE_LABEL, ...PERMISSION_LABEL };

/** The audit text names roles and permissions by code; show the names people know. */
export function readable(details: string): string {
  return details.replace(/->/g, '→').replace(/\b[A-Z][A-Z_]{2,}\b/g, (code) => LABELS[code] ?? code);
}
