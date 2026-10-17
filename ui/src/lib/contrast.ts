/**
 * WCAG 2.1 relative luminance and contrast ratio calculation.
 */
export function parseColor(color: string): [number, number, number] {
  const trimmed = color.trim();
  if (trimmed.startsWith('#')) {
    let hex = trimmed.slice(1);
    if (hex.length === 3) {
      hex = hex
        .split('')
        .map((c) => c + c)
        .join('');
    }
    const num = parseInt(hex, 16);
    return [(num >> 16) & 255, (num >> 8) & 255, num & 255];
  }
  const tripletMatch = trimmed.match(/^(\d+)\s+(\d+)\s+(\d+)$/);
  if (tripletMatch) {
    return [parseInt(tripletMatch[1], 10), parseInt(tripletMatch[2], 10), parseInt(tripletMatch[3], 10)];
  }
  const rgbMatch = trimmed.match(/^rgba?\(\s*(\d+)[,\s]+(\d+)[,\s]+(\d+)/i);
  if (rgbMatch) {
    return [parseInt(rgbMatch[1], 10), parseInt(rgbMatch[2], 10), parseInt(rgbMatch[3], 10)];
  }
  throw new Error(`Cannot parse color: ${color}`);
}

export function relativeLuminance(rgb: [number, number, number]): number {
  const [r, g, b] = rgb.map((c) => {
    const s = c / 255;
    return s <= 0.04045 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

export function contrastRatio(c1: string, c2: string): number {
  const l1 = relativeLuminance(parseColor(c1));
  const l2 = relativeLuminance(parseColor(c2));
  const lighter = Math.max(l1, l2);
  const darker = Math.min(l1, l2);
  return (lighter + 0.05) / (darker + 0.05);
}
