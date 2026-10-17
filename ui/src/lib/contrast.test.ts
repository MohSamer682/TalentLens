import { describe, expect, it } from 'vitest';
import fs from 'node:fs';
import path from 'node:path';
import { contrastRatio } from './contrast';

function parseLightTokens(cssContent: string): Record<string, string> {
  const lightBlockMatch = cssContent.match(/\[data-theme=['"]light['"]\]\s*\{([^}]+)\}/);
  if (!lightBlockMatch) {
    throw new Error("Could not find [data-theme='light'] in index.css");
  }
  const lines = lightBlockMatch[1].split('\n');
  const tokens: Record<string, string> = {};
  for (const line of lines) {
    const match = line.match(/--([\w-]+):\s*([^;/*]+)/);
    if (match) {
      tokens[match[1].trim()] = match[2].trim();
    }
  }
  return tokens;
}

describe('contrastRatio', () => {
  it('calculates pure black and white contrast as 21:1', () => {
    expect(contrastRatio('#000000', '#FFFFFF')).toBeCloseTo(21, 1);
  });

  it('lightTokensMeetContrast', () => {
    const cssPath = path.resolve(__dirname, '../index.css');
    const css = fs.readFileSync(cssPath, 'utf8');
    const tokens = parseLightTokens(css);

    expect(tokens.ink).toBeDefined();
    expect(tokens.ground).toBeDefined();
    expect(tokens['ink-muted']).toBeDefined();
    expect(tokens.surface).toBeDefined();
    expect(tokens.sunken).toBeDefined();
    expect(tokens['accent-text']).toBeDefined();
    expect(tokens['mark-text']).toBeDefined();
    expect(tokens['mark-soft']).toBeDefined();

    expect(contrastRatio(tokens.ink, tokens.ground)).toBeGreaterThanOrEqual(4.5);
    expect(contrastRatio(tokens['ink-muted'], tokens.surface)).toBeGreaterThanOrEqual(4.5);
    expect(contrastRatio(tokens['ink-muted'], tokens.sunken)).toBeGreaterThanOrEqual(4.5);
    expect(contrastRatio(tokens['accent-text'], tokens.surface)).toBeGreaterThanOrEqual(4.5);
    expect(contrastRatio(tokens['mark-text'], tokens['mark-soft'])).toBeGreaterThanOrEqual(4.5);
    expect(contrastRatio(tokens.success, tokens['success-soft'])).toBeGreaterThanOrEqual(4.5);
    expect(contrastRatio(tokens.info, tokens['info-soft'])).toBeGreaterThanOrEqual(4.5);
    expect(contrastRatio(tokens.warn, tokens['warn-soft'])).toBeGreaterThanOrEqual(4.5);
    expect(contrastRatio(tokens.danger, tokens['danger-soft'])).toBeGreaterThanOrEqual(4.5);
  });
});
