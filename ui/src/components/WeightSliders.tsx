import type { Weights } from '@/types';
import { Activity, ShieldCheck, ShieldAlert } from 'lucide-react';

interface WeightSlidersProps {
  weights: Weights;
  onChange: (weights: Weights) => void;
}

const WEIGHT_LABELS: (keyof Weights)[] = [
  'technicalSkills',
  'experience',
  'education',
  'softSkills',
  'cultureFit',
];

const WEIGHT_LABELS_DISPLAY: Record<keyof Weights, string> = {
  technicalSkills: 'Technical Skills',
  experience: 'Experience',
  education: 'Education',
  softSkills: 'Soft Skills',
  cultureFit: 'Culture Fit',
};

export function WeightSliders({ weights, onChange }: WeightSlidersProps) {
  const total = Object.values(weights).reduce((a, b) => a + b, 0);
  const isValid = total === 100;

  const handleChange = (key: keyof Weights, value: number) => {
    onChange({ ...weights, [key]: value });
  };

  return (
    <div className="space-y-6">
      <div className="space-y-2">
        <h3 className="text-lg font-semibold">Scoring Weights</h3>
        <p className="text-sm text-on-surface-variant">
          Weights must sum to 100
        </p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {WEIGHT_LABELS.map((key) => (
          <div
            key={key}
            className="bg-white/40 rounded-2xl p-6 border border-white/20 hover:bg-white/60 transition-colors"
          >
            <div className="flex justify-between items-center mb-4">
              <span className="text-sm font-bold tracking-widest uppercase text-slate-500">
                {WEIGHT_LABELS_DISPLAY[key]}
              </span>
              <span className="px-3 py-1 bg-primary/10 text-primary rounded-full font-bold text-sm">
                {weights[key]}
              </span>
            </div>
            <input
              type="range"
              min="0"
              max="100"
              value={weights[key]}
              onChange={(e) => handleChange(key, Number(e.target.value))}
              className="w-full h-1.5 accent-primary cursor-pointer"
            />
          </div>
        ))}
      </div>

      <div className="flex items-center justify-between p-6 bg-slate-900 rounded-3xl shadow-xl text-white">
        <div className="flex items-center gap-4">
          <div className="w-12 h-12 rounded-2xl bg-white/10 flex items-center justify-center">
            <Activity className="w-6 h-6 text-indigo-400" />
          </div>
          <div>
            <p className="text-slate-400 text-xs font-bold tracking-widest uppercase">
              Configuration Health
            </p>
            <div className="flex items-center gap-2">
              <p className="text-lg font-semibold">
                Total: <span className={isValid ? 'text-green-400' : 'text-red-400'}>{total}</span> / 100
              </p>
              {isValid ? (
                <ShieldCheck className="w-5 h-5 text-green-400" />
              ) : (
                <ShieldAlert className="w-5 h-5 text-red-400" />
              )}
            </div>
          </div>
        </div>
      </div>

      <p className="text-center text-sm font-medium">
        {isValid ? (
          <span className="text-green-600">Weights are valid</span>
        ) : (
          <span className="text-red-600">
            Weights must equal 100 (currently {total})
          </span>
        )}
      </p>
    </div>
  );
}
