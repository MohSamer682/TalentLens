import { forwardRef, useEffect, useRef, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { ArrowLeft, ArrowRight, Check, FileUp, X } from 'lucide-react';
import { apiErrorMessage, sessionsApi } from '@/api/client';
import type { ScreeningSession, Weights } from '@/types';
import { WeightSliders } from '@/components/WeightSliders';
import { Field, Input, Textarea } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { Panel } from '@/components/ui/Card';
import { Alert, Loader } from '@/components/ui/Feedback';
import { MainLayout } from '@/layouts/MainLayout';
import { DEFAULT_WEIGHTS, cn } from '@/lib/utils';

const sessionSchema = z.object({
  title: z.string().trim().min(1, 'Give the session a title, usually the opening it is for'),
  description: z.string().optional(),
});

type SessionForm = z.infer<typeof sessionSchema>;
type Step = 1 | 2 | 3;

const MAX_RESUMES = 200;
const STEPS = ['Opening', 'Files', 'Weights'] as const;

const fileSize = (bytes: number) =>
  bytes < 1024 * 1024 ? `${Math.max(1, Math.round(bytes / 1024))} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`;

export default function NewSession() {
  const { id } = useParams<{ id: string }>();
  const [searchParams] = useSearchParams();
  const forcedStep = Number(searchParams.get('step')) as Step | 0;
  const navigate = useNavigate();

  const [step, setStep] = useState<Step>(forcedStep || 1);
  const [saving, setSaving] = useState(false);
  const [initialLoading, setInitialLoading] = useState(!!id);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [weights, setWeights] = useState<Weights>(DEFAULT_WEIGHTS);
  const [jdFile, setJdFile] = useState<File | null>(null);
  const [resumeFiles, setResumeFiles] = useState<File[]>([]);
  const [sessionId, setSessionId] = useState<string | null>(id || null);
  const [session, setSession] = useState<ScreeningSession | null>(null);
  const headingRef = useRef<HTMLHeadingElement>(null);

  const {
    register,
    handleSubmit,
    setValue,
    formState: { errors },
  } = useForm<SessionForm>({ resolver: zodResolver(sessionSchema) });

  useEffect(() => {
    if (!id) return;
    sessionsApi
      .get(id)
      .then(({ data }) => {
        if (data.status !== 'PENDING' && data.status !== 'FAILED') {
          navigate(`/sessions/${id}`, { replace: true });
          return;
        }
        setSession(data);
        setValue('title', data.title);
        setValue('description', data.description || '');
        if (data.weights) setWeights(data.weights);
        if (!forcedStep && data.jdFileKey) setStep(2);
      })
      .catch((err) => setError(apiErrorMessage(err, 'Could not load this session')))
      .finally(() => setInitialLoading(false));
  }, [id]); // eslint-disable-line react-hooks/exhaustive-deps

  // Move focus to the new step's heading so keyboard and screen-reader users land in the right place.
  useEffect(() => {
    headingRef.current?.focus();
  }, [step]);

  const totalWeight = Object.values(weights).reduce((a, b) => a + b, 0);
  const hasJd = !!jdFile || !!session?.jdFileKey;
  const hasResumes = resumeFiles.length > 0 || (session?.candidateCount ?? 0) > 0;

  const handleSaveInfo = async (data: SessionForm) => {
    setSaving(true);
    setError(null);
    try {
      if (sessionId) {
        await sessionsApi.update(sessionId, { title: data.title, description: data.description });
      } else {
        const res = await sessionsApi.create({ title: data.title, description: data.description, weights: DEFAULT_WEIGHTS });
        setSessionId(res.data.id);
        setSession(res.data);
        navigate(`/sessions/new/${res.data.id}`, { replace: true });
      }
      setStep(2);
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not save the session'));
    } finally {
      setSaving(false);
    }
  };

  const handleUpload = async () => {
    if (!sessionId) return;
    setSaving(true);
    setError(null);
    try {
      if (jdFile) await sessionsApi.uploadJd(sessionId, jdFile);
      if (resumeFiles.length) await sessionsApi.uploadResumes(sessionId, resumeFiles);
      setStep(3);
    } catch (err) {
      setError(apiErrorMessage(err, 'Upload failed. Check that every file is a PDF under the size limit.'));
    } finally {
      setSaving(false);
    }
  };

  const handleLaunch = async () => {
    if (!sessionId) return;
    setSaving(true);
    setError(null);
    try {
      await sessionsApi.update(sessionId, { weights });
      await sessionsApi.start(sessionId);
      navigate(`/sessions/${sessionId}`);
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not start scoring'));
      setSaving(false);
    }
  };

  const addResumes = (files: File[]) => {
    const pdfs = files.filter((f) => f.type === 'application/pdf' || f.name.toLowerCase().endsWith('.pdf'));
    const merged = [...resumeFiles, ...pdfs.filter((f) => !resumeFiles.some((r) => r.name === f.name && r.size === f.size))];
    const skipped = files.length - pdfs.length;
    const over = Math.max(0, merged.length - MAX_RESUMES);
    setResumeFiles(merged.slice(0, MAX_RESUMES));
    setNotice(
      [skipped && `${skipped} non-PDF file${skipped > 1 ? 's were' : ' was'} skipped.`, over && `Only the first ${MAX_RESUMES} resumes were kept.`]
        .filter(Boolean)
        .join(' ') || null
    );
  };

  if (initialLoading) {
    return (
      <MainLayout width="narrow">
        <Loader label="Loading session…" />
      </MainLayout>
    );
  }

  return (
    <MainLayout width="narrow">
      <button
        type="button"
        onClick={() => navigate(sessionId ? `/sessions/${sessionId}` : '/dashboard')}
        className="mb-3 inline-block text-sm font-semibold text-ink-muted hover:text-ink"
      >
        ← {sessionId ? 'Back to session' : 'All sessions'}
      </button>

      <ol className="mb-5 grid grid-cols-3 gap-2" aria-label="Setup steps">
        {STEPS.map((label, i) => {
          const n = (i + 1) as Step;
          const done = step > n;
          return (
            <li
              key={label}
              aria-current={step === n ? 'step' : undefined}
              className={cn(
                'condensed flex items-center gap-2 border-t-[3px] pt-2 text-[15px] font-semibold uppercase tracking-wide transition-colors duration-300',
                step === n ? 'border-accent text-ink' : done ? 'border-accent/40 text-ink-muted' : 'border-line text-ink-muted'
              )}
            >
              <span className="num">{done ? <Check className="size-4 text-accent-text" aria-label="Done" /> : n}</span>
              {label}
            </li>
          );
        })}
      </ol>

      <Panel className="p-5 sm:p-7">
        <div key={step} className="animate-fade-up">
        {error && <Alert className="mb-5">{error}</Alert>}

        {step === 1 && (
          <form onSubmit={handleSubmit(handleSaveInfo)} className="space-y-5" noValidate>
            <StepHeading ref={headingRef} title={sessionId ? 'Edit the opening' : 'Which opening is this for?'}>
              The title is how you will find this ranking later.
            </StepHeading>
            <Field id="title" label="Session title" error={errors.title?.message}>
              <Input
                id="title"
                placeholder="e.g. Backend Engineer, Cairo office"
                aria-invalid={!!errors.title}
                aria-describedby={errors.title ? 'title-error' : undefined}
                {...register('title')}
              />
            </Field>
            <Field id="description" label="Notes (optional)" hint="Only you and your team see this.">
              <Textarea id="description" placeholder="Hiring manager, headcount, anything worth remembering" {...register('description')} />
            </Field>
            <Footer>
              <Button variant="ghost" onClick={() => navigate('/dashboard')}>
                Cancel
              </Button>
              <Button type="submit" disabled={saving}>
                {saving ? 'Saving…' : 'Continue'}
                <ArrowRight />
              </Button>
            </Footer>
          </form>
        )}

        {step === 2 && (
          <div className="space-y-5">
            <StepHeading ref={headingRef} title="Add the job description and resumes">
              PDF files only. Resumes that cannot be read are flagged later and do not stop the batch.
            </StepHeading>

            <DropZone
              id="jd"
              label="Job description"
              hint={session?.jdFileKey && !jdFile ? 'Already uploaded. Choose a file only to replace it.' : 'One PDF'}
              onFiles={(files) => setJdFile(files[0] ?? null)}
            />
            {jdFile && <FileList files={[jdFile]} onRemove={() => setJdFile(null)} />}

            <DropZone
              id="resumes"
              label="Resumes"
              hint={`Up to ${MAX_RESUMES} PDFs. Drop more to add to the list.${
                session?.candidateCount ? ` ${session.candidateCount} already uploaded.` : ''
              }`}
              multiple
              onFiles={addResumes}
            />
            {notice && <p className="text-[13px] text-warn">{notice}</p>}
            {resumeFiles.length > 0 && (
              <FileList files={resumeFiles} onRemove={(f) => setResumeFiles((list) => list.filter((x) => x !== f))} />
            )}

            <Footer>
              <Button variant="ghost" onClick={() => setStep(1)}>
                <ArrowLeft />
                Back
              </Button>
              <Button onClick={handleUpload} disabled={!hasJd || !hasResumes || saving}>
                {saving ? 'Uploading…' : jdFile || resumeFiles.length ? 'Upload and continue' : 'Continue'}
                <ArrowRight />
              </Button>
            </Footer>
          </div>
        )}

        {step === 3 && (
          <div className="space-y-5">
            <StepHeading ref={headingRef} title="What matters most for this role?">
              Each resume gets a 0–100 score per dimension. The ranking uses these weights, which must add up to 100.
            </StepHeading>
            <WeightSliders weights={weights} onChange={setWeights} />
            <Footer>
              <Button variant="ghost" onClick={() => setStep(2)}>
                <ArrowLeft />
                Back
              </Button>
              <Button onClick={handleLaunch} disabled={totalWeight !== 100 || saving}>
                {saving ? 'Starting…' : 'Start scoring'}
              </Button>
            </Footer>
          </div>
        )}
        </div>
      </Panel>
    </MainLayout>
  );
}


const StepHeading = forwardRef<HTMLHeadingElement, { title: string; children: React.ReactNode }>(({ title, children }, ref) => (
  <div className="space-y-1">
    <h1 ref={ref} tabIndex={-1} className="text-xl font-bold tracking-tight focus:outline-none">
      {title}
    </h1>
    <p className="text-sm text-ink-muted">{children}</p>
  </div>
));
StepHeading.displayName = 'StepHeading';

function Footer({ children }: { children: React.ReactNode }) {
  return <div className="flex items-center justify-between border-t border-line pt-5">{children}</div>;
}

function DropZone({
  id,
  label,
  hint,
  multiple,
  onFiles,
}: {
  id: string;
  label: string;
  hint: string;
  multiple?: boolean;
  onFiles: (files: File[]) => void;
}) {
  const [over, setOver] = useState(false);
  return (
    <div className="space-y-1.5">
      <p className="text-sm font-semibold" id={`${id}-label`}>
        {label}
      </p>
      <label
        htmlFor={id}
        onDragOver={(e) => {
          e.preventDefault();
          setOver(true);
        }}
        onDragLeave={() => setOver(false)}
        onDrop={(e) => {
          e.preventDefault();
          setOver(false);
          onFiles(Array.from(e.dataTransfer.files));
        }}
        className={cn(
          'flex cursor-pointer flex-col items-center gap-1 rounded-lg border-2 border-dashed px-4 py-6 text-center text-sm transition-colors focus-within:border-accent-text',
          over ? 'border-accent-text bg-accent-soft' : 'border-line-strong hover:border-ink-muted hover:bg-ground'
        )}
      >
        <FileUp className="size-5 text-ink-muted" aria-hidden="true" />
        <span>
          <span className="font-semibold text-accent-text">Choose {multiple ? 'files' : 'a file'}</span> or drop {multiple ? 'them' : 'it'} here
        </span>
        <span className="text-[13px] text-ink-muted">{hint}</span>
        <input
          id={id}
          type="file"
          accept="application/pdf,.pdf"
          multiple={multiple}
          aria-labelledby={`${id}-label`}
          className="sr-only"
          onChange={(e) => {
            onFiles(Array.from(e.target.files || []));
            e.target.value = '';
          }}
        />
      </label>
    </div>
  );
}

function FileList({ files, onRemove }: { files: File[]; onRemove: (f: File) => void }) {
  return (
    <div className="rounded-lg border border-line">
      <p className="num border-b border-line bg-sunken px-3 py-1.5 text-[13px] font-semibold text-ink-muted">
        {files.length} file{files.length === 1 ? '' : 's'} ready to upload
      </p>
      <ul className="max-h-56 divide-y divide-line overflow-y-auto">
        {files.map((f) => (
          <li key={`${f.name}-${f.size}`} className="flex items-center gap-3 px-3 py-1.5 text-sm">
            <span className="min-w-0 flex-1 truncate">{f.name}</span>
            <span className="num shrink-0 text-[13px] text-ink-muted">{fileSize(f.size)}</span>
            <button
              type="button"
              onClick={() => onRemove(f)}
              aria-label={`Remove ${f.name}`}
              className="flex size-7 shrink-0 items-center justify-center rounded text-ink-muted hover:bg-sunken hover:text-ink"
            >
              <X className="size-4" />
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}
