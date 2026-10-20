# ADR-002: Keep the current stack for the portal and profile work

**Status:** Accepted
**Date:** 2026-10-20
**Deciders:** Muhammed (owner)

## Context

The project's planning spec lists defaults for new work: RFC 9457 problem details, versioned `/api/v1` paths, Spring Modulith, TanStack Query, and a layered `api/application/domain/infrastructure` package split. The code base already has fourteen feature packages built a different way:

- Every response, success or failure, is the `{success, message, data | error}` envelope, and the UI's `apiErrorMessage` reads it.
- Paths are `/api/...` with no version.
- Module boundaries are checked by ArchUnit in `FeatureBoundaryTest`.
- The UI fetches with axios into component state and keeps only the signed-in user in zustand.
- Packages are flat per feature: controller, service and `dto/`.

The next features (job details on cycles, user profiles, the candidate portal and reschedule requests) add four packages next to the existing ones.

## Decision

Build the new features on the existing stack and conventions. New packages (`jobs`, `profile`, `portal`, `reschedule`) follow the flat layout, return the envelope, live under `/api`, and are covered by the existing ArchUnit rules. Moving to the spec's defaults is a separate upgrade project, done across the whole code base at once.

## Consequences

- New code reads the same as its neighbours, and reviewers check one set of conventions.
- The UI keeps one error-handling path.
- The spec's defaults are deferred, not rejected. When the upgrade happens it touches every package, including these four.
- Without Spring Modulith there is no generated module documentation; the ArchUnit rules remain the boundary check.
