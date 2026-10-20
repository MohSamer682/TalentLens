import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { ApiRequestError, apiErrorMessage, portalApi } from '@/api/client';
import type { ApplicationView } from '@/types';
import { Badge, Tag } from '@/components/ui/Badge';
import { Panel, PanelHeader } from '@/components/ui/Card';
import { Alert, Loader } from '@/components/ui/Feedback';
import { InterviewCard } from './InterviewCard';
import { PortalLayout } from './PortalLayout';
import { StageSteps } from './StageSteps';
import { STAGE_LABEL, STAGE_TONE } from './portal';

export default function ApplicationPage() {
  const { id = '' } = useParams();
  const [view, setView] = useState<ApplicationView | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    setView(null);
    setError(null);
    portalApi
      .application(id)
      .then((res) => active && setView(res.data))
      .catch((err) => {
        if (!active) return;
        setError(err instanceof ApiRequestError && err.status === 404 ? "We couldn't find this application." : apiErrorMessage(err, 'Could not load this application'));
      });
    return () => {
      active = false;
    };
  }, [id]);

  return (
    <PortalLayout>
      <Link to="/portal" className="inline-flex items-center gap-1.5 text-sm text-ink-muted hover:text-ink">
        <ArrowLeft className="size-4" aria-hidden="true" />
        All applications
      </Link>
      {error && <Alert className="mt-6">{error}</Alert>}
      {!error && !view && <Loader label="Loading your application…" />}
      {view && (
        <>
          <header className="mt-4 space-y-2">
            <h1 className="break-words text-[32px] font-bold leading-tight">{view.job.title}</h1>
            <div className="flex flex-wrap items-center gap-2">
              <Badge tone={STAGE_TONE[view.stage]}>{STAGE_LABEL[view.stage]}</Badge>
              {view.job.positionCode && <span className="font-mono text-[12px] text-ink-muted">{view.job.positionCode}</span>}
            </div>
          </header>

          <div className="mt-8 grid gap-6 lg:grid-cols-[minmax(0,1fr)_320px]">
            <section aria-labelledby="interviews-heading" className="space-y-4">
              <h2 id="interviews-heading" className="text-[19px] font-semibold">
                Your interviews
              </h2>
              <Panel className="p-4">
                <StageSteps steps={view.steps} />
              </Panel>
              {view.interviews.map((interview) => (
                <InterviewCard key={interview.id} interview={interview} />
              ))}
            </section>

            <aside className="space-y-6">
              <Panel>
                <PanelHeader title="About the job" />
                <div className="space-y-4 p-4 sm:px-5">
                  {view.job.description ? (
                    <p className="whitespace-pre-line text-sm leading-relaxed">{view.job.description}</p>
                  ) : (
                    <p className="text-sm text-ink-muted">Your recruiter hasn't added a description yet.</p>
                  )}
                  {!!view.job.requiredSkills?.length && (
                    <div>
                      <h3 className="mb-2 text-[13px] font-semibold text-ink-muted">Skills for this role</h3>
                      <ul className="flex flex-wrap gap-1.5">
                        {view.job.requiredSkills.map((skill) => (
                          <li key={skill}>
                            <Tag>{skill}</Tag>
                          </li>
                        ))}
                      </ul>
                    </div>
                  )}
                </div>
              </Panel>
              <Panel>
                <PanelHeader title="Your recruiter" />
                <div className="p-4 text-sm sm:px-5">
                  <p className="font-semibold">{view.recruiter.name}</p>
                  {view.recruiter.headline && <p className="text-ink-muted">{view.recruiter.headline}</p>}
                </div>
              </Panel>
            </aside>
          </div>
        </>
      )}
    </PortalLayout>
  );
}
