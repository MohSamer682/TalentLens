import { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import { sessionsApi } from '@/api/client';
import type { CandidateScore } from '@/types';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { RadarScoreChart } from '@/components/RadarScoreChart';
import { MainLayout } from '@/layouts/MainLayout';
import { ArrowLeft, Download, FileText, Check, Info, Calendar, Mail } from 'lucide-react';

export default function CandidateDetail() {
  const { sessionId, candidateId } = useParams<{ sessionId: string; candidateId: string }>();
  const [score, setScore] = useState<CandidateScore | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadData();
  }, [sessionId, candidateId]);

  const loadData = async () => {
    if (!sessionId || !candidateId) return;
    try {
      const res = await sessionsApi.candidateDetail(sessionId, candidateId);
      setScore(res.data);
    } catch (err) {
      console.error('Failed to load candidate:', err);
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-surface flex items-center justify-center">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary" />
      </div>
    );
  }

  if (!score) {
    return (
      <div className="min-h-screen bg-surface flex items-center justify-center">
        <p>Candidate not found</p>
      </div>
    );
  }

  const dimensions = [
    { key: 'technicalSkills', label: 'Technical', weight: score.weights?.technicalSkills ?? 30 },
    { key: 'experience', label: 'Experience', weight: score.weights?.experience ?? 25 },
    { key: 'education', label: 'Education', weight: score.weights?.education ?? 15 },
    { key: 'softSkills', label: 'Soft Skills', weight: score.weights?.softSkills ?? 20 },
    { key: 'cultureFit', label: 'Culture Fit', weight: score.weights?.cultureFit ?? 10 },
  ];

  // Calculate weighted scores
  const weightedScores = dimensions.map((d) => ({
    ...d,
    value: score[d.key as keyof CandidateScore] as number,
    weighted: ((score[d.key as keyof CandidateScore] as number) * d.weight) / 100,
  }));

  const totalWeighted = weightedScores.reduce((sum, d) => sum + d.weighted, 0);

  return (
    <MainLayout>
      <Link
        to={`/sessions/${sessionId}`}
        className="inline-flex items-center text-on-surface-variant hover:text-primary mb-12"
      >
        <ArrowLeft className="w-4 h-4 mr-2" />
        Back to Session
      </Link>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-16 items-start">
        {/* LEFT COLUMN */}
        <section className="space-y-10">
          <div className="space-y-4">
            <Badge variant="primary">Active Candidate</Badge>
            <h1 className="text-[3.5rem] font-semibold tracking-tight">
              {score.name}
            </h1>
            <div className="flex items-center gap-2 text-on-surface-variant">
              <Mail className="w-4 h-4" />
              <p className="text-body-md">{score.email}</p>
            </div>
          </div>

          <div className="space-y-4">
            <h3 className="text-[1rem] font-semibold">Core Competencies</h3>
            <div className="flex flex-wrap gap-2">
              {score.skills.map((skill) => (
                <Badge key={skill} variant="default">
                  {skill}
                </Badge>
              ))}
            </div>
          </div>

          <div className="p-8 bg-surface-container-low rounded-xl flex items-center justify-between group cursor-pointer hover:bg-surface-container-high transition-colors">
            <div className="flex items-center gap-4">
              <div className="w-12 h-12 bg-surface-container-lowest rounded-xl flex items-center justify-center shadow-sm">
                <FileText className="text-primary" />
              </div>
              <div>
                <p className="font-semibold">Download Resume (PDF)</p>
                <p className="text-xs text-on-surface-variant">
                  Last updated 2 days ago • 2.4 MB
                </p>
              </div>
            </div>
            <Button variant="ghost">
              <Download className="w-4 h-4" />
            </Button>
          </div>
        </section>

        {/* RIGHT COLUMN */}
        <section className="space-y-10">
          {/* Radar Chart */}
          <div className="bg-surface-container-lowest rounded-xl p-8 border border-outline-variant/10 shadow-sm">
            <div className="flex items-center justify-between mb-8">
              <h3 className="text-[1rem] font-semibold">Evaluation Radar</h3>
              <Badge variant="primary">
                Overall Score: {score.compositeScore}
              </Badge>
            </div>

            <div className="flex justify-center py-4">
              <RadarScoreChart
                technical={score.technicalSkills}
                experience={score.experience}
                education={score.education}
                softSkills={score.softSkills}
                cultureFit={score.cultureFit}
              />
            </div>
          </div>

          {/* Score Table */}
          <div className="overflow-hidden rounded-xl bg-surface-container-lowest border border-outline-variant/10">
            <table className="w-full text-left text-sm">
              <thead className="bg-surface-container-low text-on-surface-variant">
                <tr>
                  <th className="px-6 py-4 font-semibold uppercase text-[10px]">
                    Dimension
                  </th>
                  <th className="px-6 py-4 font-semibold uppercase text-[10px] text-right">
                    Score
                  </th>
                  <th className="px-6 py-4 font-semibold uppercase text-[10px] text-right">
                    Weight
                  </th>
                  <th className="px-6 py-4 font-semibold uppercase text-[10px] text-right">
                    Weighted
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-surface-container-low">
                {weightedScores.map((d) => (
                  <tr key={d.key}>
                    <td className="px-6 py-4 font-medium">{d.label}</td>
                    <td className="px-6 py-4 text-right">{d.value}</td>
                    <td className="px-6 py-4 text-right text-on-surface-variant">
                      {d.weight}%
                    </td>
                    <td className="px-6 py-4 text-right font-semibold">
                      {d.weighted.toFixed(1)}
                    </td>
                  </tr>
                ))}
              </tbody>
              <tfoot className="bg-indigo-50/50">
                <tr>
                  <td className="px-6 py-4 font-bold text-indigo-600">Total</td>
                  <td className="px-6 py-4" />
                  <td className="px-6 py-4 text-right font-bold text-indigo-600">
                    100%
                  </td>
                  <td className="px-6 py-4 text-right font-bold text-indigo-600">
                    {totalWeighted.toFixed(1)}
                  </td>
                </tr>
              </tfoot>
            </table>
          </div>

          {/* Justification */}
          <div className="p-8 bg-surface-container-lowest border-l-4 border-indigo-500 rounded-r-xl shadow-sm italic leading-relaxed">
            "{score.justification}"
          </div>

          {/* Strengths & Gaps */}
          <div className="grid grid-cols-1 gap-6">
            <div className="space-y-4">
              <h4 className="text-[10px] font-bold tracking-widest uppercase text-on-surface-variant">
                Core Strengths
              </h4>
              <ul className="space-y-3">
                {score.strengths.split('\n').map((s, i) => (
                  <li key={i} className="flex items-start gap-3">
                    <Check className="text-indigo-600 w-5 h-5 mt-0.5" />
                    <span className="text-sm">{s}</span>
                  </li>
                ))}
              </ul>
            </div>

            <div className="space-y-4">
              <h4 className="text-[10px] font-bold tracking-widest uppercase text-on-surface-variant">
                Identified Gaps
              </h4>
              <ul className="space-y-3">
                {score.gaps.split('\n').map((g, i) => (
                  <li key={i} className="flex items-start gap-3">
                    <Info className="text-red-500 w-5 h-5 mt-0.5" />
                    <span className="text-sm">{g}</span>
                  </li>
                ))}
              </ul>
            </div>
          </div>

          {/* Actions */}
          <div className="pt-8 flex gap-4">
            <Button className="flex-1">
              <Calendar className="w-4 h-4" />
              Schedule Interview
            </Button>
            <Button variant="outline">Reject</Button>
          </div>
        </section>
      </div>
    </MainLayout>
  );
}