import { RadarChart, PolarGrid, PolarAngleAxis, Radar, ResponsiveContainer } from 'recharts';

interface Props {
  technical: number;
  experience: number;
  education: number;
  softSkills: number;
  cultureFit: number;
}

export function RadarScoreChart({ technical, experience, education, softSkills, cultureFit }: Props) {
  const data = [
    { dim: 'Technical', value: technical },
    { dim: 'Experience', value: experience },
    { dim: 'Education', value: education },
    { dim: 'Soft Skills', value: softSkills },
    { dim: 'Culture Fit', value: cultureFit },
  ];

  return (
    <ResponsiveContainer width="100%" height={280}>
      <RadarChart cx="50%" cy="50%" outerRadius="80%" data={data}>
        <PolarGrid stroke="#e2e8f0" />
        <PolarAngleAxis dataKey="dim" tick={{ fill: '#64748b', fontSize: 12 }} />
        <Radar
          name="Score"
          dataKey="value"
          stroke="#6366f1"
          fill="#6366f1"
          fillOpacity={0.5}
        />
      </RadarChart>
    </ResponsiveContainer>
  );
}
