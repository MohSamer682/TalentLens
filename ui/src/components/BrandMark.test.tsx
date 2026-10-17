import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import { BrandMark } from './BrandMark';

describe('BrandMark', () => {
  it('renders the lens mark with the saffron bar and no accessible name of its own', () => {
    const { container } = render(<BrandMark size={28} />);
    const svg = container.querySelector('svg')!;
    expect(svg.getAttribute('aria-hidden')).toBe('true');
    expect(svg.getAttribute('width')).toBe('28');
    expect(container.querySelectorAll('rect[data-bar]').length).toBe(3);
  });
});
