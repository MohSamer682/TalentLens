import { useState, useEffect } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { sessionsApi } from '@/api/client';
import type { Weights, ScreeningSession } from '@/types';
import { Navbar } from '@/components/Navbar';
import { WeightSliders } from '@/components/WeightSliders';
import { Input, Textarea } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { Check, X, ArrowRight, AlertCircle, Loader2 } from 'lucide-react';

const sessionSchema = z.object({
  title: z.string().min(1, 'Title is required'),
  description: z.string().optional(),
});

type SessionForm = z.infer<typeof sessionSchema>;

type Step = 1 | 2 | 3;

const DEFAULT_WEIGHTS: Weights = {
  technicalSkills: 30,
  experience: 25,
  education: 15,
  softSkills: 20,
  cultureFit: 10,
};

export default function NewSession() {
  const { id } = useParams<{ id: string }>();
  const [searchParams] = useSearchParams();
  const forcedStep = searchParams.get('step');
  
  const [step, setStep] = useState<Step>(forcedStep ? Number(forcedStep) as Step : 1);
  const [loading, setLoading] = useState(false);
  const [initialLoading, setInitialLoading] = useState(!!id);
  const [error, setError] = useState<string | null>(null);
  const [weights, setWeights] = useState<Weights>(DEFAULT_WEIGHTS);
  const [jdFile, setJdFile] = useState<File | null>(null);
  const [resumeFiles, setResumeFiles] = useState<File[]>([]);
  const [sessionId, setSessionId] = useState<string | null>(id || null);
  const [session, setSession] = useState<ScreeningSession | null>(null);
  const [uploading, setUploading] = useState(false);
  const navigate = useNavigate();

  const {
    register,
    handleSubmit,
    setValue,
    formState: { errors },
  } = useForm<SessionForm>({
    resolver: zodResolver(sessionSchema),
  });

  useEffect(() => {
    if (id) {
      loadSession(id);
    }
  }, [id]);

  const loadSession = async (sessionId: string) => {
    try {
      const res = await sessionsApi.get(sessionId);
      const data = res.data;
      setSession(data);
      setValue('title', data.title);
      setValue('description', data.description || '');
      
      if (data.status !== 'PENDING' && data.status !== 'FAILED') {
        navigate(`/sessions/${sessionId}`);
        return;
      }

      if (!forcedStep && data.jdFileKey) {
        setStep(2);
      }
    } catch (err) {
      console.error('Failed to load session:', err);
      setError('Failed to load existing session.');
    } finally {
      setInitialLoading(false);
    }
  };

  const totalWeight = Object.values(weights).reduce((a, b) => a + b, 0);
  const canProceedStep2 = (jdFile && resumeFiles.length > 0) || (session?.jdFileKey);
  const canLaunch = totalWeight === 100;

  const handleCreateSession = async (data: SessionForm) => {
    setLoading(true);
    setError(null);
    try {
      if (sessionId) {
        await sessionsApi.update(sessionId, {
          title: data.title,
          description: data.description,
        });
        setStep(2);
      } else {
        const res = await sessionsApi.create({
          title: data.title,
          description: data.description,
          weights: DEFAULT_WEIGHTS,
        });
        setSessionId(res.data.id);
        navigate(`/sessions/new/${res.data.id}`, { replace: true });
        setStep(2);
      }
    } catch (err) {
      console.error('Failed to save session:', err);
      setError('Failed to save session. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  const handleUploadFiles = async () => {
    if (!sessionId) return;
    setUploading(true);
    setError(null);
    try {
      if (jdFile) {
        await sessionsApi.uploadJd(sessionId, jdFile);
      }
      if (resumeFiles.length > 0) {
        await sessionsApi.uploadResumes(sessionId, resumeFiles);
      }
      setStep(3);
    } catch (err: any) {
      console.error('Failed to upload files:', err);
      const msg = err.response?.data?.message || 'Failed to upload files. Please check file sizes and formats.';
      setError(typeof msg === 'string' ? msg : 'Upload failed');
    } finally {
      setUploading(false);
    }
  };

  const handleLaunch = async () => {
    if (!sessionId) return;
    setLoading(true);
    setError(null);
    try {
      await sessionsApi.update(sessionId, { weights });
      await sessionsApi.start(sessionId);
      navigate(`/sessions/${sessionId}`);
    } catch (err) {
      console.error('Failed to start session:', err);
      setError('Failed to start processing. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  const steps = [
    { num: 1, label: 'Session Info' },
    { num: 2, label: 'Upload Files' },
    { num: 3, label: 'Configure' },
  ];

  if (initialLoading) {
    return (
      <div className="min-h-screen bg-surface flex items-center justify-center">
        <Loader2 className="w-8 h-8 animate-spin text-primary" />
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-surface">
      <Navbar />
      <main className="pt-32 pb-12 px-6 flex items-center justify-center min-h-screen">
        <div className="w-full max-w-2xl relative">
          <div className="glass-panel border border-white/40 rounded-3xl shadow-2xl shadow-indigo-500/10 p-8 md:p-12 glass">
            {error && (
              <div className="mb-6 bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-xl flex items-center gap-3">
                <AlertCircle className="w-5 h-5 flex-shrink-0" />
                <p className="text-sm">{error}</p>
              </div>
            )}
            <div className="flex items-center justify-between mb-12">
              {steps.map((s, i) => (
                <div key={s.num} className="flex items-center gap-2 flex-1">
                  <div
                    className={`w-10 h-10 rounded-full flex items-center justify-center font-bold ${
                      step >= s.num
                        ? 'bg-primary text-white shadow-lg shadow-primary/30'
                        : 'bg-surface-container text-on-surface-variant'
                    }`}
                  >
                    {step > s.num ? (
                      <Check className="w-5 h-5" />
                    ) : (
                      s.num
                    )}
                  </div>
                  <span
                    className={`text-xs font-medium tracking-wider uppercase hidden md:inline ${
                      step >= s.num ? 'text-primary' : 'text-on-surface-variant'
                    }`}
                  >
                    {s.label}
                  </span>
                  {i < steps.length - 1 && (
                    <div className="h-px bg-surface-container-highest flex-1 -mt-6" />
                  )}
                </div>
              ))}
            </div>

            {step === 1 && (
              <form onSubmit={handleSubmit(handleCreateSession)} className="space-y-8">
                <div className="space-y-2">
                  <h1 className="text-3xl font-bold tracking-tight text-on-surface">
                    {sessionId ? 'Edit Session' : 'Start a New Session'}
                  </h1>
                  <p className="text-on-surface-variant leading-relaxed">
                    Define the core attributes of your screening session.
                  </p>
                </div>

                <div className="space-y-6">
                  <div className="space-y-2">
                    <label className="block text-sm font-semibold text-on-surface ml-1">
                      Session Title <span className="text-primary">*</span>
                    </label>
                    <Input
                      placeholder="Backend Engineer Q2 2026"
                      {...register('title')}
                    />
                    {errors.title && (
                      <p className="text-red-500 text-xs">
                        {errors.title.message}
                      </p>
                    )}
                  </div>

                  <div className="space-y-2">
                    <label className="block text-sm font-semibold text-on-surface ml-1">
                      Description{' '}
                      <span className="text-on-surface-variant font-normal">
                        (Optional)
                      </span>
                    </label>
                    <Textarea
                      placeholder="Provide context or objectives for this session..."
                      {...register('description')}
                    />
                  </div>
                </div>

                <div className="pt-8 flex items-center justify-between border-t border-surface-container/50">
                  <Button
                    type="button"
                    variant="ghost"
                    onClick={() => navigate('/dashboard')}
                  >
                    <X className="w-5 h-5" />
                    Cancel
                  </Button>
                  <Button type="submit" disabled={loading}>
                    Next Step
                    <ArrowRight className="w-5 h-5" />
                  </Button>
                </div>
              </form>
            )}

            {step === 2 && (
              <div className="space-y-8">
                <div className="space-y-2">
                  <h1 className="text-3xl font-bold tracking-tight text-on-surface">
                    Upload Files
                  </h1>
                  <p className="text-on-surface-variant leading-relaxed">
                    Add your job description and candidate resumes.
                  </p>
                </div>

                <div className="space-y-6">
                  <div className="space-y-2">
                    <label className="block text-sm font-semibold text-on-surface ml-1">
                      Job Description (PDF) {session?.jdFileKey && '✅'}
                    </label>
                    <input
                      type="file"
                      accept=".pdf"
                      onChange={(e) => setJdFile(e.target.files?.[0] || null)}
                      className="w-full h-32 border-2 border-dashed border-surface-container rounded-2xl flex items-center justify-center cursor-pointer hover:bg-surface-container-low transition-colors"
                    />
                    {jdFile && (
                      <p className="text-sm text-green-600">
                        Selected: {jdFile.name}
                      </p>
                    )}
                  </div>

                  <div className="space-y-2">
                    <label className="block text-sm font-semibold text-on-surface ml-1">
                      Resumes (PDF, max 200)
                    </label>
                    <input
                      type="file"
                      accept=".pdf"
                      multiple
                      onChange={(e) => {
                        const files = Array.from(e.target.files || []);
                        setResumeFiles(files.slice(0, 200));
                      }}
                      className="w-full h-32 border-2 border-dashed border-surface-container rounded-2xl flex items-center justify-center cursor-pointer hover:bg-surface-container-low transition-colors"
                    />
                    {resumeFiles.length > 0 && (
                      <p className="text-sm text-green-600">
                        {resumeFiles.length} files selected
                      </p>
                    )}
                  </div>
                </div>

                <div className="pt-8 flex items-center justify-between border-t border-surface-container/50">
                  <Button variant="ghost" onClick={() => setStep(1)}>
                    <X className="w-5 h-5" />
                    Back
                  </Button>
                  <Button
                    onClick={handleUploadFiles}
                    disabled={!canProceedStep2 || uploading}
                  >
                    {uploading ? 'Uploading...' : 'Next Step'}
                    <ArrowRight className="w-5 h-5" />
                  </Button>
                </div>
              </div>
            )}

            {step === 3 && (
              <div className="space-y-8">
                <div className="space-y-2">
                  <h1 className="text-3xl font-bold tracking-tight text-on-surface">
                    Configure Weights
                  </h1>
                  <p className="text-on-surface-variant leading-relaxed">
                    Define scoring weights. Total must equal 100.
                  </p>
                </div>

                <WeightSliders weights={weights} onChange={setWeights} />

                <div className="pt-8 flex items-center justify-between border-t border-surface-container/50">
                  <Button variant="ghost" onClick={() => setStep(2)}>
                    <X className="w-5 h-5" />
                    Back
                  </Button>
                  <Button onClick={handleLaunch} disabled={!canLaunch || loading}>
                    {loading ? 'Launching...' : 'Launch Session'}
                  </Button>
                </div>
              </div>
            )}
          </div>
        </div>
      </main>
    </div>
  );
}
