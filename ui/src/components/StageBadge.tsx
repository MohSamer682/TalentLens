import type { ReviewStatus } from '@/types';
import { Badge } from '@/components/ui/Badge';
import { STAGE_LABEL, stageTone } from '@/lib/pipeline';

/** A candidate's real stage, worded and coloured the same on every screen. */
export function StageBadge({
  stage,
  className,
  children,
}: {
  stage: ReviewStatus;
  className?: string;
  children?: React.ReactNode;
}) {
  return (
    <Badge tone={stageTone(stage)} className={className}>
      {children}
      {STAGE_LABEL[stage] ?? stage}
    </Badge>
  );
}
