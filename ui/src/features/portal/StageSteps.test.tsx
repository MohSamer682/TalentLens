import { afterEach, describe, expect, it } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { StageSteps } from './StageSteps';

afterEach(cleanup);

describe('StageSteps', () => {
  it('marks current and done steps', () => {
    render(
      <StageSteps
        steps={[
          { kind: 'HR', state: 'DONE' },
          { kind: 'TECHNICAL', state: 'SCHEDULED' },
          { kind: 'MANAGEMENT', state: 'PLANNED' },
        ]}
      />
    );
    const items = screen.getAllByRole('listitem');
    expect(items[0].textContent).toContain('HR interview');
    expect(items[0].textContent).toContain('Done');
    expect(items[1].getAttribute('aria-current')).toBe('step');
    expect(items[1].textContent).toContain('Scheduled');
    expect(items[2].textContent).toContain('Not scheduled yet');
    expect(items[2].getAttribute('aria-current')).toBeNull();
  });

  it('says so when no rounds are planned', () => {
    render(<StageSteps steps={[]} />);
    expect(screen.getByText(/no interviews planned yet/i)).toBeTruthy();
  });
});
