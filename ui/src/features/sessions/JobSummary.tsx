import type { JobDetails } from '@/types';
import { Panel } from '@/components/ui/Card';

export function JobSummary({ job }: { job: JobDetails }) {
  const skills = job.requiredSkills ?? [];
  return (
    <Panel className="mb-6 space-y-3 p-5">
      <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
        <p className="text-lg font-semibold tracking-tight">{job.title}</p>
        <span className="font-mono text-[13px] text-ink-muted">{job.positionCode ?? 'Not set'}</span>
      </div>
      {job.description && <p className="line-clamp-3 whitespace-pre-line text-sm text-ink-muted">{job.description}</p>}
      {skills.length > 0 && (
        <ul aria-label="Required skills" className="flex flex-wrap gap-1.5">
          {skills.map((skill) => (
            <li key={skill} className="rounded-full border border-line bg-sunken px-2.5 py-0.5 text-[13px]">
              {skill}
            </li>
          ))}
        </ul>
      )}
    </Panel>
  );
}
