# System Maintenance & Fix Log - April 18, 2026

## 1. Backend Diagnostics & Critical Fixes

### A. Package Scanning & Dependency Injection
- **Problem**: `api-service` and `batch-worker` failed to start due to `UnsatisfiedDependencyException`. Spring could not find `UserRepository` because it was in the `common` module, outside the default scan path.
- **Fix**: Added `@SpringBootApplication(scanBasePackages = "com.resumescreener")`, `@EntityScan`, and `@EnableJpaRepositories` to both application classes.
- **Result**: Backend services successfully initialize and map all repositories.

### B. Persistent Data Constraints
- **Problem**: `null value in column "created_at"` error when creating new sessions.
- **Fix**: Implemented `@PrePersist` hooks in `ScreeningSession.java` and `User.java` to automatically initialize the `createdAt` timestamp.
- **Result**: Database inserts now succeed without requiring manual timestamp management in the UI or Service layer.

### C. Blocking API & Async Refactoring
- **Problem**: The job trigger endpoint was processing candidates synchronously, causing the API to hang and eventually timeout.
- **Fix**: Created `JobService` in `batch-worker` and enabled `@EnableAsync`. Refactored `JobTriggerController` to return a `202 Accepted` response immediately while processing continues in the background.
- **Result**: Smooth user experience with instant feedback when launching a screening.

---

## 2. Security & Connectivity

### A. CORS Policy Integration
- **Problem**: The UI was unable to receive responses from the backend despite the port being open.
- **Fix**: Enabled `.cors()` in `SecurityConfig.java` and linked it to the `CorsConfigurationSource` in `WebConfig.java`.
- **Result**: The frontend can now securely communicate with the API on port 8080.

### B. Admin Bootstrap Logic
- **Enhancement**: Updated `AuthService.java` to automatically grant the first registered user the `ADMIN` role.
- **Result**: Simplifies initial deployment and user management setup.

---

## 3. UI & API Synchronization

### A. TypeScript Type Alignment
- **Problem**: UI build failure due to missing `name` and `email` properties in the `CandidateScore` interface.
- **Fix**: Updated `ui/src/types/index.ts` and ensure the backend DTOs (`CandidateScoreDetailDto`) provided these fields.
- **Result**: Successful UI build and accurate candidate evaluation screens.

### B. Missing Endpoints
- **Update**: Implemented the following missing endpoints required by the UI designs:
    - Ranked Candidate List (`GET /api/sessions/{id}/candidates`)
    - Candidate Detailed Evaluation (`GET /api/sessions/{id}/candidates/{cid}`)
    - Excel Export (`GET /api/sessions/{id}/export`)

---

## Status: All Systems Operational
- **Backend**: Robust, Async-capable, and Secure.
- **UI**: Correctly typed and fully integrated with real data.
- **Infrastructure**: Ready for deployment with `docker-compose up --build`.
