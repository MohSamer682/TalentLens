import { describe, expect, it } from 'vitest';
import { toggle, toggleAll } from './selection';

describe('toggle', () => {
  it('adds then removes an id', () => {
    const once = toggle(new Set(), 'a');
    expect([...once]).toEqual(['a']);
    expect([...toggle(once, 'a')]).toEqual([]);
  });
});

describe('toggleAll', () => {
  it('selects the whole page from empty', () => expect([...toggleAll(new Set(), ['a', 'b'])]).toEqual(['a', 'b']));
  it('completes a partial page', () => expect([...toggleAll(new Set(['a']), ['a', 'b'])].sort()).toEqual(['a', 'b']));
  it('clears a full page but keeps other pages', () =>
    expect([...toggleAll(new Set(['a', 'b', 'z']), ['a', 'b'])]).toEqual(['z']));
  it('does nothing for an empty page', () => expect([...toggleAll(new Set(['z']), [])]).toEqual(['z']));
});
