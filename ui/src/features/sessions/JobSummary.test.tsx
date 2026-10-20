import { afterEach, describe, it, expect } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { JobSummary } from './JobSummary';

afterEach(cleanup);

describe('JobSummary', () => {
  it('lists the job title, code and skills in the order entered', () => {
    render(<JobSummary job={{ title: 'Senior Backend Engineer', positionCode: 'ENG-BE-104', requiredSkills: ['Java', 'Spring', 'SQL'] }} />);
    expect(screen.getByText('Senior Backend Engineer')).toBeTruthy();
    expect(screen.getByText('ENG-BE-104')).toBeTruthy();
    expect(screen.getAllByRole('listitem').map((li) => li.textContent)).toEqual(['Java', 'Spring', 'SQL']);
  });

  it('says Not set for a session created before job details existed', () => {
    render(<JobSummary job={{ title: 'Old cycle', requiredSkills: [] }} />);
    expect(screen.getByText('Not set')).toBeTruthy();
    expect(screen.queryByRole('list')).toBeNull();
  });
});
