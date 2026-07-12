# Project Improvement Report & Architectural Overview

## 1. Project Overview: Resume Screener
The **Resume Screener** is a distributed system designed to automate the initial screening of job applicants by matching their resumes against job descriptions using Large Language Models (LLMs).

### System Architecture
- **UI (React + TypeScript + Vite)**: Modern dashboard for recruiters to create screening sessions, upload job descriptions (JD), and bulk-upload resumes.
- **API Service (Spring Boot)**: Manages user authentication (JWT), session metadata, file uploads to MinIO, and coordinates with the worker.
- **Batch Worker (Spring Boot)**: Asynchronously processes resumes. It extracts text from PDFs, communicates with OpenAI for scoring, calculates composite rankings, and generates Excel reports.
- **Storage (MinIO)**: S3-compatible object storage for JDs, resumes, and generated reports.
- **Database (PostgreSQL)**: Stores users, sessions, candidates, and scoring results.

---

## 2. Identified Issues & Fixes Applied

### A. Architectural: Code Duplication
- **Issue**: Identical `@Entity`, `@Repository`, and `enum` classes were copied in both `api-service` and `batch-worker`. This violated the DRY (Don't Repeat Yourself) principle and made maintenance error-prone.
- **Fix**: Migrated the project to a **Multi-Module Maven Build**.
    - Created a `common` module containing all shared domain logic.
    - Configured `api-service` and `batch-worker` to depend on the `common` module.
    - Centralized dependency versions in a root `pom.xml`.

### B. Networking: Legacy Web Clients
- **Issue**: Both services used the legacy `RestTemplate` with manual instantiation (`new RestTemplate()`). This bypasses Spring's connection pooling, timeouts, and logging configuration.
- **Fix**: Refactored `BatchTriggerService` and `OpenAiClient` to use **`RestClient`** (introduced in Spring 6.1).
    - Configured `RestClient` as a Spring-managed Bean for proper lifecycle management.

### C. Docker: Non-Portable Frontend
- **Issue**: The UI Dockerfile baked the `VITE_API_URL` during the build phase. This meant the Docker image could only talk to one specific environment (e.g., localhost) and had to be rebuilt for production.
- **Fix**: (Recommended for next step) Update UI container to use environment variable substitution at runtime via Nginx or a lightweight entrypoint script.

### D. Data Integrity: Unmanaged Schema
- **Issue**: Relying on `hibernate.ddl-auto: update` for database schema management is dangerous for production as it can lead to inconsistent states.
- **Fix**: (Recommended for next step) Integrate **Flyway** for version-controlled database migrations.

---

## 3. Improvements Summary

| Category | Before | After | Benefit |
| :--- | :--- | :--- | :--- |
| **Structure** | Monolithic Duplication | Multi-Module | Clean separation, DRY code, easier maintenance. |
| **Java Version** | Java 21 | Java 21 (Optimized) | Full utilization of modern Java features and virtual threads. |
| **Communication** | RestTemplate | RestClient | Fluent API, better observability, modern Spring support. |
| **Dependencies** | Hardcoded Versions | Centralized BOM | No version mismatches between microservices. |

---

## 4. How the System Works (The "Flow")
1. **User Auth**: Recruiter logs in; API Service issues a JWT.
2. **Session Setup**: Recruiter creates a "Session" (e.g., "Senior Java Dev").
3. **Uploads**: JD and Resumes (PDFs) are uploaded to API Service, which saves them to MinIO and creates records in Postgres.
4. **Trigger**: When the user clicks "Start", API Service calls `batch-worker` via an internal endpoint secured by a shared secret.
5. **Processing**: 
    - Worker picks up candidate IDs.
    - Downloads PDFs from MinIO.
    - Uses `PdfTextExtractor` (PDFBox) to get raw text.
    - Calls OpenAI GPT-4o with a specialized prompt to score the candidate.
6. **Ranking**: Worker calculates a weighted score (Technical, Experience, Fit, etc.) and ranks candidates.
7. **Report**: Worker generates an Excel file with all results and uploads it to MinIO.
8. **Results**: Recruiter views the ranked list and downloads the final report from the UI.
