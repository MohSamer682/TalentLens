export function toggle(selected: Set<string>, id: string): Set<string> {
  const next = new Set(selected);
  if (next.has(id)) next.delete(id);
  else next.add(id);
  return next;
}

/** When every id on the page is already selected, clears them; otherwise selects them all. */
export function toggleAll(selected: Set<string>, pageIds: string[]): Set<string> {
  const next = new Set(selected);
  const allSelected = pageIds.length > 0 && pageIds.every((id) => next.has(id));
  pageIds.forEach((id) => (allSelected ? next.delete(id) : next.add(id)));
  return next;
}
