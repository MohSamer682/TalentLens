# ADR-002: Keep the current stack for the portal and profile work

**Status:** Accepted
**Date:** 2026-10-20
**Deciders:** Muhammed (owner)

## Context

The planning spec defaults to RFC 9457 problem details, `/api/v1` paths, Spring Modulith, TanStack Query and an `api/application/domain/infrastructure` split. The code base has fourteen feature packages built another way:

- Every response, success or failure, is the `{success, message, data | error}` envelope, and the UI's `apiErrorMessage` reads it.
- Paths are `/api/...` with no version.
- Module boundaries are checked by ArchUnit in `FeatureBoundaryTest`.
- The UI fetches with axios into component state and keeps only the signed-in user in zustand.
- Packages are flat per feature: controller, service and `dto/`.

The next features (job details, profiles, candidate portal, reschedule requests) add four more packages.

## Decision

Build them on the existing conventions. `jobs`, `profile`, `portal` and `reschedule` use the flat layout, return the envelope, live under `/api` and fall under the ArchUnit rules. Moving to the spec's defaults is a separate upgrade across the whole code base.

## Consequences

- New code matches its neighbours; reviewers check one set of conventions.
- The UI keeps one error path.
- The spec's defaults are deferred, not rejected. The upgrade will touch every package, these four included.
- No Spring Modulith means no generated module docs; ArchUnit stays the boundary check.
