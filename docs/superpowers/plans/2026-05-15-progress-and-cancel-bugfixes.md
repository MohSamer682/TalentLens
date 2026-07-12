# Progress Display + Session Cancel — Bug Fix Plan

**Date:** 2026-05-15  
**Reported symptoms:**
- LLM scoring starts but progress stays at 0% / nothing visible happens
- Session cannot be deleted or stopped while RUNNING
- No errors in browser DevTools console

---

## Root Cause Analysis

### Why progress stays at 0%

**Cause A — SessionCard hardcodes 45% (frontend, `SessionCard.tsx:43`)**  
The progress bar on the dashboard card is completely static:
```tsx
<span>45%</span>                           // line 43 — hardcoded
<div className="h-full bg-primary w-[45%]" />  // line 45 — hardcoded
```
Dashboard never fetches real progress. Card just shows a fake bar.

**Cause B — SessionDetail hides the progress ring until first poll returns (`SessionDetail.tsx:161`)**  
```tsx
{session.status === 'RUNNING' && progress && (   // progress === null on mount
```
`progress` starts as `null`. The component renders nothing until the first `loadProgress()` call resolves. If that call fails or is slow, user sees a blank space. Should show 0% immediately when RUNNING.

**Cause C — `.env` change requires docker container restart**  
The `LLM_BASE_URL` and `LLM_MODEL` values in `.env` were updated but running containers still use the OLD values baked in at startup. Until `docker compose down && docker compose up -d` is run, every LLM call still hits `api.openai.com` with a Gemini key → 401 → all candidates fail → session goes FAILED quickly (no visible progress before it flips).

**Cause D — When all candidates fail, session flips to FAILED before user sees progress**  
`finalizeSession` marks FAILED when `processed == 0 && lastErrorMessage != null`. With 401 errors, all candidates fail instantly (no retry — 401 is `HttpClientErrorException.Unauthorized`, not in `retry-exceptions`). Session goes FAILED in seconds. User sees RUNNING briefly then FAILED, or misses it entirely.

### Why session cannot be deleted while RUNNING

**Cause E — RUNNING guard throws, frontend swallows silently**  
`SessionService.deleteSession` throws `RuntimeException("Cannot delete a session that is currently running")`.  
`GlobalExceptionHandler` maps `RuntimeException` → HTTP 400.  
Both `SessionCard.tsx` and `SessionDetail.tsx` catch the axios error and only call `console.error(...)` — no user-visible feedback. User clicks delete, nothing happens, no error shown.

**Cause F — No cancel/stop endpoint exists**  
There is no `POST /sessions/{id}/cancel` endpoint. User has no way to abort a stuck RUNNING session. If batch-worker fails silently mid-processing (e.g., uncaught exception in `@Async` thread), the session stays RUNNING forever.

### Gemini API implementation — verified correct

The `OpenAiClient` sends:
- `POST {baseUrl}/chat/completions` with `Authorization: Bearer {apiKey}`
- Body: `{ model, messages, temperature }`

Gemini's OpenAI-compatible endpoint is `https://generativelanguage.googleapis.com/v1beta/openai`.  
Full URL becomes `https://generativelanguage.googleapis.com/v1beta/openai/chat/completions`.  
Gemini returns the same `choices[0].message.content` structure as OpenAI.  
`LlmScoreDtoWrapper` deserializes this correctly.  
`@JsonProperty` fix on `LlmScoreDto` ensures snake_case fields map to camelCase Java fields.  

**No code changes needed for Gemini compatibility — only the docker restart.**

---

## Fix Plan

---

### Fix 1 — Docker Restart (Operational — do first)

```
docker compose down
docker compose up -d
```

This picks up the updated `LLM_BASE_URL` and `LLM_MODEL` from `.env`. Without this, all fixes are irrelevant — every LLM call returns 401.

**Verify:** `docker compose logs batch-worker -f` → first LLM call should show 200, not 401.

---

### Fix 2 — SessionCard: Real Progress via Polling

**File:** `ui/src/components/SessionCard.tsx`

**Problem:** Lines 40–48 show a hardcoded progress bar at 45%. The component receives `session` but never calls the progress API.

**Solution:** Add a `useEffect` that polls `sessionsApi.progress(session.id)` every 3 seconds when `session.status === 'RUNNING'`. Store result in local state and render the real percent.

**Replace lines 1–106 with:**
```tsx
import { Link } from 'react-router-dom';
import { useState, useEffect } from 'react';
import { sessionsApi } from '@/api/client';
import type { ScreeningSession } from '@/types';
import { Badge } from '@/components/ui/Badge';
import { FileText, Edit2, Eye, AlertCircle, Trash2 } from 'lucide-react';
import { Button } from '@/components/ui/Button';

interface SessionCardProps {
  session: ScreeningSession;
  onDelete?: () => void;
}

export function SessionCard({ session, onDelete }: SessionCardProps) {
  const [percent, setPercent] = useState(0);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  useEffect(() => {
    if (session.status !== 'RUNNING') return;
    let active = true;
    const poll = async () => {
      try {
        const res = await sessionsApi.progress(session.id);
        if (active) setPercent(res.data.percent);
        // If done/failed, parent onDelete-style refresh could be wired here
      } catch {
        // silent — dashboard still shows status badge
      }
    };
    poll();
    const id = setInterval(poll, 3000);
    return () => { active = false; clearInterval(id); };
  }, [session.id, session.status]);

  const statusVariant = {
    PENDING: 'default' as const,
    RUNNING: 'running' as const,
    DONE: 'success' as const,
    FAILED: 'error' as const,
  };

  const handleDelete = async () => {
    setDeleteError(null);
    if (!confirm('Are you sure you want to delete this session?')) return;
    try {
      await sessionsApi.delete(session.id);
      onDelete?.();
    } catch (err: any) {
      const msg = err?.response?.data?.message || 'Failed to delete session';
      setDeleteError(msg);
    }
  };

  return (
    <div className="bg-surface-container-lowest border border-surface-container rounded-3xl p-8 shadow-sm hover:shadow-xl hover:border-primary/20 transition-all group flex flex-col justify-between min-h-[320px]">
      <div className="space-y-6">
        <div className="flex justify-between items-start gap-4">
          <h3 className="text-2xl font-bold tracking-tight text-on-surface leading-tight">
            {session.title}
          </h3>
          <Badge variant={statusVariant[session.status]}>{session.status}</Badge>
        </div>

        <div className="flex items-center gap-2 text-on-surface-variant">
          <FileText className="text-base" />
          <span className="text-sm font-semibold">
            {session.candidateCount} resumes
          </span>
        </div>

        {session.status === 'RUNNING' && (
          <div className="space-y-3 pt-4">
            <div className="flex justify-between text-[11px] font-black text-on-surface-variant uppercase tracking-wider">
              <span>Progress</span>
              <span>{percent}%</span>
            </div>
            <div className="h-2 w-full bg-surface-container-high rounded-full overflow-hidden">
              <div
                className="h-full bg-primary rounded-full shadow-primary/20 transition-all duration-500"
                style={{ width: `${percent}%` }}
              />
            </div>
          </div>
        )}

        {session.status === 'FAILED' && (
          <div className="flex items-start gap-3 text-red-600 text-xs font-bold bg-red-50 border border-red-200 p-4 rounded-2xl mt-2">
            <AlertCircle className="w-4 h-4 flex-shrink-0" />
            <span>{session.lastErrorMessage || 'Screening failed. Check logs or retry.'}</span>
          </div>
        )}

        {deleteError && (
          <div className="flex items-start gap-3 text-red-600 text-xs font-bold bg-red-50 border border-red-200 p-4 rounded-2xl mt-2">
            <AlertCircle className="w-4 h-4 flex-shrink-0" />
            <span>{deleteError}</span>
          </div>
        )}
      </div>

      <div className="flex items-center gap-3 mt-10">
        {session.status === 'DONE' ? (
          <Link to={`/sessions/${session.id}`} className="flex-1">
            <Button variant="outline" className="w-full justify-center">
              <Eye className="w-4 h-4" />
              View Results
            </Button>
          </Link>
        ) : session.status === 'RUNNING' ? (
          <Link to={`/sessions/${session.id}`} className="flex-1">
            <Button variant="outline" className="w-full justify-center">
              <Eye className="w-4 h-4" />
              View Progress
            </Button>
          </Link>
        ) : (
          <Link to={`/sessions/new/${session.id}`} className="flex-1">
            <Button variant="outline" className="w-full justify-center">
              <Eye className="w-4 h-4" />
              Continue Setup
            </Button>
          </Link>
        )}
        <Link to={`/sessions/new/${session.id}?step=1`}>
          <Button variant="ghost" size="icon">
            <Edit2 className="w-4 h-4" />
          </Button>
        </Link>
        <Button
          variant="ghost"
          size="icon"
          className="text-red-500 hover:text-red-700 hover:bg-red-50"
          onClick={handleDelete}
        >
          <Trash2 className="w-4 h-4" />
        </Button>
      </div>
    </div>
  );
}
```

**Key changes:**
- `percent` state replaces hardcoded `45%`
- `useEffect` polls `/progress` every 3s when RUNNING, cleans up on unmount/status change
- `deleteError` state shows inline error message when delete is rejected (e.g. RUNNING guard)
- Progress bar width uses inline `style={{ width: \`${percent}%\` }}` with CSS transition

---

### Fix 3 — SessionDetail: Show Progress Immediately + Error Feedback on Delete

**File:** `ui/src/pages/SessionDetail.tsx`

**Problem 1 (line 161):** `{session.status === 'RUNNING' && progress && (` — hides ring until `progress !== null`.  
**Problem 2 (line 83–89):** `handleDelete` only `console.error` on failure — no visible error.

**Change 1:** Replace `progress &&` condition with a default-to-zero approach.

Find line 161:
```tsx
{session.status === 'RUNNING' && progress && (
```
Replace with:
```tsx
{session.status === 'RUNNING' && (
```

And wherever `progress.percent`, `progress.processed`, `progress.total`, `progress.failed` are accessed, add safe defaults. Find in the JSX all `progress.X` references and add `progress?.X ?? 0`:

```tsx
// line ~181
strokeDasharray={`${(progress?.percent ?? 0) * 2.83} 283`}

// line ~187
{progress?.percent ?? 0}%

// line ~197
{progress?.processed ?? 0} / {progress?.total ?? 0}

// (failed count line)
{progress?.failed ?? 0}
```

**Change 2:** Add visible error state for delete. Find `handleDelete` (lines 80–90):

```tsx
const [actionError, setActionError] = useState<string | null>(null);

const handleDelete = async () => {
  if (!id) return;
  if (confirm('Are you sure you want to delete this session?')) {
    try {
      await sessionsApi.delete(id);
      navigate('/dashboard');
    } catch (err: any) {
      const msg = err?.response?.data?.message || 'Failed to delete session';
      setActionError(msg);
    }
  }
};
```

Add `useState` import if not present. Add error display near the top of the return JSX (after the header):
```tsx
{actionError && (
  <div className="flex items-start gap-3 text-red-600 text-sm font-bold bg-red-50 border border-red-200 p-4 rounded-2xl mb-6">
    <AlertCircle className="w-4 h-4 flex-shrink-0 mt-0.5" />
    <span>{actionError}</span>
  </div>
)}
```

---

### Fix 4 — Backend: Add Cancel Endpoint

**Purpose:** Allow force-stopping a stuck RUNNING session. This is the only way to unblock deletion if batch-worker fails silently.

#### Fix 4a — SessionService: add cancelSession method

**File:** `api-service/src/main/java/com/resumescreener/api/session/SessionService.java`

Add after `deleteSession`:
```java
@Transactional
public void cancelSession(String id, String userEmail) {
    ScreeningSession session = getSession(id, userEmail);
    if (session.getStatus() != SessionStatus.RUNNING) {
        throw new RuntimeException("Session is not running");
    }
    session.setStatus(SessionStatus.FAILED);
    session.setLastErrorMessage("Cancelled by user");
    session.setCompletedAt(java.time.Instant.now());
    sessionRepository.save(session);
}
```

#### Fix 4b — SessionController: add DELETE /sessions/{id}/cancel endpoint

**File:** `api-service/src/main/java/com/resumescreener/api/session/SessionController.java`

Add after the existing `deleteSession` endpoint:
```java
@PostMapping("/{id}/cancel")
public ResponseEntity<Void> cancelSession(@PathVariable String id, Authentication auth) {
    sessionService.cancelSession(id, auth.getName());
    return ResponseEntity.noContent().build();
}
```

#### Fix 4c — Frontend: expose cancel API + wire Cancel button

**File:** `ui/src/api/client.ts`

In `sessionsApi`, add:
```ts
cancel: (id: string) => api.post(`/sessions/${id}/cancel`),
```

**File:** `ui/src/components/SessionCard.tsx`

In the RUNNING branch of the footer buttons, add a Cancel button before the Trash:
```tsx
{session.status === 'RUNNING' && (
  <Button
    variant="ghost"
    size="icon"
    className="text-orange-500 hover:text-orange-700 hover:bg-orange-50"
    title="Cancel screening"
    onClick={async () => {
      if (confirm('Cancel this screening session?')) {
        try {
          await sessionsApi.cancel(session.id);
          onDelete?.(); // triggers parent to reload sessions
        } catch (err: any) {
          setDeleteError(err?.response?.data?.message || 'Failed to cancel');
        }
      }
    }}
  >
    {/* X icon from lucide */}
    <svg xmlns="http://www.w3.org/2000/svg" className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
  </Button>
)}
```

Or import `X` from lucide-react and use `<X className="w-4 h-4" />`.

**File:** `ui/src/pages/SessionDetail.tsx`

Add `handleCancel` alongside `handleDelete`:
```tsx
const handleCancel = async () => {
  if (!id) return;
  if (confirm('Cancel this screening? It will be marked as FAILED.')) {
    try {
      await sessionsApi.cancel(id);
      loadData(); // refresh session status
    } catch (err: any) {
      setActionError(err?.response?.data?.message || 'Failed to cancel');
    }
  }
};
```

In the header buttons area, add when RUNNING:
```tsx
{session.status === 'RUNNING' && (
  <Button variant="outline" className="text-orange-500 border-orange-200 hover:bg-orange-50" onClick={handleCancel}>
    <X className="w-4 h-4" />
    Cancel Screening
  </Button>
)}
```

Import `X` from `lucide-react` (add to existing import on line 9).

---

### Fix 5 — Backend: deleteSession should allow deletion after cancel

**File:** `api-service/src/main/java/com/resumescreener/api/session/SessionService.java`

Current `deleteSession` throws for RUNNING. After user cancels (sets status to FAILED), delete should work automatically. No code change needed — the cancel endpoint sets status to FAILED first, then user can delete.

**BUT:** also update the error message to be more helpful:
```java
// current:
throw new RuntimeException("Cannot delete a session that is currently running");
// change to:
throw new RuntimeException("Session is currently running. Cancel it first, then delete.");
```

---

### Fix 6 — Resilience4j: Add 401/4xx to non-retryable list (prevent silent hangs)

**File:** `batch-worker/src/main/resources/application.yml`

Currently `retry-exceptions` only includes 5xx and network errors. A 401 from a misconfigured LLM key throws `HttpClientErrorException.Unauthorized` — NOT retried — but it IS caught per-candidate and marked FAILED. This is correct behavior.

However, add `ignore-exceptions` to prevent retry on 4xx client errors (saves time if LLM key is wrong):
```yaml
resilience4j:
  retry:
    instances:
      llm:
        max-attempts: 3
        wait-duration: 2s
        retry-exceptions:
          - org.springframework.web.client.HttpServerErrorException
          - org.springframework.web.client.ResourceAccessException
        ignore-exceptions:
          - org.springframework.web.client.HttpClientErrorException
```

This means: 5xx → retry up to 3x. 4xx (401, 400, 429) → fail immediately, no retry.

---

## Execution Order

| # | Fix | Type | Impact |
|---|-----|------|--------|
| 1 | Docker restart (`docker compose down && up -d`) | Ops | **Critical — unblocks LLM** |
| 2 | Resilience4j ignore 4xx (Fix 6) | Backend config | Fast fail on bad key |
| 3 | Add cancel endpoint (Fix 4a + 4b) | Backend | Unblock stuck sessions |
| 4 | Add `cancel` to sessionsApi (Fix 4c client.ts) | Frontend | Wire cancel to backend |
| 5 | SessionCard real progress + error display (Fix 2) | Frontend | Fix 0% display |
| 6 | SessionDetail show ring immediately + error (Fix 3) | Frontend | Fix blank progress UI |
| 7 | Cancel button in SessionCard + SessionDetail (Fix 4c UI) | Frontend | User can stop stuck session |

---

## Verification Checklist

- [ ] `docker compose logs batch-worker -f` — first LLM call returns 200, not 401
- [ ] Start a screening → SessionCard shows real % ticking up (not 45%)
- [ ] Navigate to SessionDetail → progress ring shows 0% immediately, not blank
- [ ] Session reaches DONE → ring shows 100%, page auto-refreshes to results
- [ ] `POST /api/sessions/{id}/cancel` while RUNNING → session becomes FAILED
- [ ] After cancel, delete button works (session is now FAILED, not RUNNING)
- [ ] Delete a RUNNING session without cancelling → inline error message appears (not just console)
- [ ] `grep "45%" ui/src/components/SessionCard.tsx` → zero results

---

## Gemini API Compatibility — Confirmed

No code changes needed. The existing `OpenAiClient` is correct for Gemini:

| Check | Status |
|-------|--------|
| Endpoint: `{base-url}/chat/completions` | ✅ Correct for Gemini OpenAI-compat |
| Auth: `Authorization: Bearer {key}` | ✅ Correct |
| Body: `{ model, messages, temperature }` | ✅ Gemini accepts this |
| Response: `choices[0].message.content` | ✅ Gemini returns same structure |
| `LlmScoreDto` `@JsonProperty` snake_case | ✅ Already fixed (Task 1) |
| JSON extraction: `indexOf / lastIndexOf` | ✅ Already fixed (Task 5) |

Only prerequisite: docker restart to pick up `LLM_BASE_URL=https://generativelanguage.googleapis.com/v1beta/openai` and `LLM_MODEL=gemini-2.0-flash`.
