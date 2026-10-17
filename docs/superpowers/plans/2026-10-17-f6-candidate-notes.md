# F6 Candidate Notes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let everyone who can open a candidate leave short notes on them, so the team's reasoning sits next to the score.

**Architecture:** One table `candidate_notes`, one small service that reuses `SessionService.getSession` for read access, three endpoints under the existing candidate path, and a `NotesPanel` on the candidate page.

**Tech Stack:** Java 21, Spring Boot 3.3, JPA, Flyway, JUnit 5 + Mockito, Testcontainers; React 18, TypeScript, Tailwind 3, Vitest.

**Spec:** [2026-10-17-talentlens-brief.md](2026-10-17-talentlens-brief.md), section F6. Mockup: TalentLens designs artifact, "Candidate page".

## Global Constraints

- Branch `feat/candidate-notes` from `feat/talentlens-materials`. One PR. Migration `V9__candidate_notes.sql`.
- Note body: 1 to 4,000 characters after trimming. Error copy: "Write something before adding the note" and "Notes can be up to 4,000 characters".
- Read and add: anyone `SessionService.getSession` lets in (owner or `SESSION_VIEW_ALL`). Delete: the author, or `Role.ADMIN`.
- Audit actions: `CANDIDATE_NOTE_ADD`, `CANDIDATE_NOTE_DELETE` (target type `candidate`, target id the candidate id).
- Notes are listed newest first, at most 200 per candidate in one response (shortcut, see Task 1).
- UI copy: panel title "Notes", placeholder "Add a note for the team…", button "Add note", empty state "No notes yet. Notes are visible to everyone who can open this session."

## Review Focus

1. **A user who can't open the session** posts or reads notes by guessing ids: 403, nothing saved. Test: Task 1 `outsiderCannotReadOrAdd`.
2. **A note id from another candidate** in the delete URL: 404, nothing deleted. Test: Task 1 `deleteChecksTheNoteBelongsToTheCandidate`.
3. **Whitespace-only or 4,001-character body**: 400 with the exact copy above. Test: Task 1 `rejectsBlankAndTooLongBodies`.
4. **Deleting a candidate or session** that has notes: notes go with it, no FK error. Test: Task 1 `notesCascadeWithCandidate` (Testcontainers).
5. **Submitting twice fast** (double click, Ctrl+Enter and click): one note. Test: Task 2 `submitIsDisabledWhileSaving`.

---

### Task 1: Notes API

**Files:**
- Create: `common/src/main/resources/db/migration/V9__candidate_notes.sql`, `common/src/main/java/com/resumescreener/common/session/CandidateNote.java`, `.../common/session/CandidateNoteRepository.java`, `api-service/src/main/java/com/resumescreener/api/candidate/NoteService.java`, `.../candidate/dto/NoteDto.java`, `.../candidate/dto/NoteRequest.java`
- Modify: `api-service/src/main/java/com/resumescreener/api/candidate/CandidateController.java` (three endpoints)
- Test: `api-service/src/test/java/com/resumescreener/api/candidate/NoteServiceTest.java`, `.../candidate/NoteCascadeDatabaseTest.java`

**Interfaces:**
- Produces:
  - Table `candidate_notes(id VARCHAR(255) PK, candidate_id VARCHAR(255) NOT NULL REFERENCES candidates(id) ON DELETE CASCADE, author_id VARCHAR(255) REFERENCES users(id) ON DELETE SET NULL, body TEXT NOT NULL, created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL)`, index `(candidate_id, created_at DESC)`.
  - `record NoteDto(String id, String body, String authorId, String authorName, Role authorRole, Instant createdAt, boolean canDelete)`; author fields null when the author was deleted (UI shows "Former user").
  - `record NoteRequest(@NotBlank(message = "Write something before adding the note") @Size(max = 4000, message = "Notes can be up to 4,000 characters") String body)`
  - `NoteService.list(String sessionId, String candidateId, String email): List<NoteDto>`
  - `NoteService.add(String sessionId, String candidateId, String body, String email): NoteDto`
  - `NoteService.delete(String sessionId, String candidateId, String noteId, String email): void`
  - `GET /api/sessions/{id}/candidates/{candidateId}/notes`, `POST` same path (201), `DELETE .../notes/{noteId}` (204).

- [ ] **Step 1: Write the failing tests** in `NoteServiceTest` (mocks, same style as `CandidateReviewTest`):

```java
@Test void addsTrimmedNoteAndAudits()            // body "  Strong on Kafka  " saved as "Strong on Kafka"; audit CANDIDATE_NOTE_ADD
@Test void outsiderCannotReadOrAdd()             // getSession throws AccessDeniedException -> list and add both throw it; repository.save never called
@Test void rejectsBlankAndTooLongBodies()        // "   " and "x".repeat(4001) -> ApiException validation with the exact copy
@Test void onlyAuthorOrAdminDeletes()            // other recruiter -> AccessDeniedException; admin -> deleted; author -> deleted
@Test void deleteChecksTheNoteBelongsToTheCandidate() // note.candidate.id = "c2", path candidate "c1" -> ApiException notFound, delete never called
@Test void canDeleteFlagFollowsTheRule()         // list as author -> true on own notes, false on others; as admin -> true on all
@Test void candidateFromAnotherSessionIs404()    // candidate.session.id != path session id -> notFound
```

`NoteCascadeDatabaseTest` (`@DataJpaTest` + Testcontainers like `RoleServiceDatabaseTest`): insert user, session, candidate, two notes; delete the candidate; `assertEquals(0, notes.count())`.

- [ ] **Step 2: Run** `mvn -pl api-service -am test -Dtest='Note*Test' -Dsurefire.failIfNoSpecifiedTests=false` — expect FAIL (classes missing).

- [ ] **Step 3: Implement.** `NoteService` calls `sessionService.getSession(sessionId, email)` first, then loads the candidate with `candidateRepository.findByIdAndSessionId(candidateId, sessionId)` (add this finder). Validation of the body happens in the service as well as on the DTO, because the service trims first. Author name and role come from a `@EntityGraph(attributePaths = "author")` finder `findTop200ByCandidateIdOrderByCreatedAtDesc` (open-in-view is off: never touch a lazy association outside the transaction). `// shortcut: 200 newest notes, no paging; add paging if a candidate ever passes that`.

- [ ] **Step 4: Run** the same command — expect PASS. Then `mvn -q verify` for the whole build.

- [ ] **Step 5: Commit** `feat(candidate): notes on candidates`

---

### Task 2: Notes panel on the candidate page

**Files:**
- Create: `ui/src/components/NotesPanel.tsx`, `ui/src/components/NotesPanel.test.tsx`
- Modify: `ui/src/types/index.ts` (`CandidateNote`), `ui/src/api/client.ts` (`notesApi`), `ui/src/pages/CandidateDetail.tsx` (render the panel under strengths and gaps; right column on `lg`)

**Interfaces:**
- Consumes: Task 1 endpoints and `NoteDto` shape.
- Produces: `notesApi.list(sessionId, candidateId)`, `notesApi.add(sessionId, candidateId, body)`, `notesApi.remove(sessionId, candidateId, noteId)`; `NotesPanel({ sessionId: string; candidateId: string })`.

- [ ] **Step 1: Write the failing tests** (`@testing-library/react`, `notesApi` mocked):

```tsx
it('shows the empty state copy when there are no notes')
it('adds a note on Ctrl+Enter and puts it first')
it('submitIsDisabledWhileSaving: a second Ctrl+Enter during the request sends nothing')  // expect(notesApi.add).toHaveBeenCalledTimes(1)
it('shows a delete button only on notes with canDelete')
it('keeps the typed text and shows the server message when adding fails')
it('shows "Former user" when the author is null')
```

- [ ] **Step 2: Run** `cd ui && npx vitest run src/components/NotesPanel.test.tsx` — expect FAIL.

- [ ] **Step 3: Implement `NotesPanel`.** `Panel` + `PanelHeader title="Notes"` with the count; textarea with a visible label "New note" (`sr-only` is not enough: use the placeholder plus `aria-label`), live character count from 3,600 on, "Add note" button disabled while empty or saving. Each note: author name, role label from `ROLE_LABEL`, relative time (`Intl.RelativeTimeFormat`, full date in `title`), body with `whitespace-pre-wrap break-words`. Delete asks "Delete this note?" inline (Delete / Keep), no modal.

- [ ] **Step 4: Run** `cd ui && npx vitest run && npm run build` — expect PASS.

- [ ] **Step 5: Smoke and screenshots.** Real stack: add a note as recruiter, read it as HR chief, delete as admin; screenshots `docs/screenshots/f6/candidate-notes-{dark,light}-{360,1280}.png`.

- [ ] **Step 6: Commit** `feat(ui): notes panel on the candidate page`
