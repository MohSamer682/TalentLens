import { afterEach, describe, it, expect } from 'vitest';
import { useState } from 'react';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';

afterEach(cleanup);
import { TagInput } from './TagInput';

function Harness({ initial = [] as string[], max }: { initial?: string[]; max?: number }) {
  const [tags, setTags] = useState(initial);
  return (
    <>
      <TagInput id="skills" label="Required skills" value={tags} onChange={setTags} max={max} />
      <output data-testid="tags">{tags.join('|')}</output>
    </>
  );
}

const input = () => screen.getByLabelText('Required skills') as HTMLInputElement;
const tags = () => screen.getByTestId('tags').textContent;

describe('TagInput', () => {
  it('adds a trimmed tag on Enter and on comma', () => {
    render(<Harness />);
    fireEvent.change(input(), { target: { value: '  Java ' } });
    fireEvent.keyDown(input(), { key: 'Enter' });
    fireEvent.change(input(), { target: { value: 'Spring' } });
    fireEvent.keyDown(input(), { key: ',' });
    expect(tags()).toBe('Java|Spring');
    expect(input().value).toBe('');
  });

  it('refuses a duplicate ignoring case and says why', () => {
    render(<Harness initial={['Java']} />);
    fireEvent.change(input(), { target: { value: 'java' } });
    fireEvent.keyDown(input(), { key: 'Enter' });
    expect(tags()).toBe('Java');
    expect(screen.getByText('That skill is already on the list.')).toBeTruthy();
  });

  it('removes the last tag on Backspace when the input is empty', () => {
    render(<Harness initial={['Java', 'SQL']} />);
    fireEvent.keyDown(input(), { key: 'Backspace' });
    expect(tags()).toBe('Java');
  });

  it('removes a tag from its own button', () => {
    render(<Harness initial={['Java', 'SQL']} />);
    fireEvent.click(screen.getByRole('button', { name: 'Remove Java' }));
    expect(tags()).toBe('SQL');
  });

  it('stops at the maximum', () => {
    render(<Harness initial={['A', 'B']} max={2} />);
    fireEvent.change(input(), { target: { value: 'C' } });
    fireEvent.keyDown(input(), { key: 'Enter' });
    expect(tags()).toBe('A|B');
    expect(screen.getByText('2 of 2')).toBeTruthy();
  });

  it('refuses a skill longer than 80 characters and says why', () => {
    render(<Harness />);
    fireEvent.change(input(), { target: { value: 'x'.repeat(81) } });
    fireEvent.keyDown(input(), { key: 'Enter' });
    expect(tags()).toBe('');
    expect(screen.getByText('Keep each skill under 80 characters.')).toBeTruthy();
  });

  it('keeps the input usable at the maximum so Backspace still removes', () => {
    render(<Harness initial={['A', 'B']} max={2} />);
    expect(input().disabled).toBe(false);
    fireEvent.keyDown(input(), { key: 'Backspace' });
    expect(tags()).toBe('A');
  });

  it('returns focus to the input after a remove button is used', () => {
    render(<Harness initial={['Java', 'SQL']} />);
    fireEvent.click(screen.getByRole('button', { name: 'Remove Java' }));
    expect(document.activeElement).toBe(input());
  });
});
