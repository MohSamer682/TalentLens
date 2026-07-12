 AI Resume Screening System — Implementation Plan

 Context

 Approved spec exists at docs/superpowers/specs/2026-04-17-resume-screener-design.md. Builds a 3-service platform
 (Spring Boot API + Spring Batch Worker + React UI) that uploads resumes, scores them via GPT-4o against a job
 description, ranks candidates, and exports Excel. Shared PostgreSQL + MinIO. JWT auth. Strict JPA-only (no native
 SQL). Resilience4j wraps OpenAI for retry + rate limiting. Plan follows TDD, bite-sized steps, commit after each task.

 Goal: From empty repo → working end-to-end system runnable via docker compose up.

 Architecture: React (3000) → API (8080) → Batch Worker (8081). API persists to PostgreSQL + MinIO. Batch Worker reads
 shared DB + MinIO, calls OpenAI, writes scores + Excel back.

 Tech Stack: Spring Boot 3.3.x · Spring Security + JJWT 0.12.6 · Spring Data JPA · Spring Batch 5.x · PostgreSQL 16 ·
 MinIO · gpt-4o · or any alternative model "user can customize or use api key " Resilience4j 2.x · PDFBox 3.x · POI 5.3.x · hypersistence-utils 3.8 · React 18 · Vite · Tailwind ·
 shadcn/ui · Zustand · Recharts · Vitest + RTL · Docker Compose.

 ---

 File Structure

 resume-screener/
 ├── docker-compose.yml                      # 5 services: ui, api, batch-worker, postgres, minio
 ├── .env.example
 ├── api-service/
 │   ├── pom.xml
 │   └── src/
 │       ├── main/java/com/resumescreener/api/
 │       │   ├── ApiServiceApplication.java
 │       │   ├── config/{SecurityConfig, MinioConfig, WebConfig}.java
 │       │   ├── auth/{AuthController, AuthService, JwtService, JwtAuthFilter, UserDetailsServiceImpl}.java
 │       │   ├── auth/dto/{RegisterRequest, LoginRequest, AuthResponse}.java
 │       │   ├── entity/{User, ScreeningSession, ScoringWeights, Candidate, CandidateScore, BatchJob}.java
 │       │   ├── enums/{Role, SessionStatus, CandidateStatus}.java
 │       │   ├── repository/*.java                (one per entity)
 │       │   ├── session/{SessionController, SessionService}.java
 │       │   ├── session/dto/{CreateSessionRequest, SessionResponse, WeightsDto}.java
 │       │   ├── upload/{UploadController, MinioUploadService}.java
 │       │   ├── batch/{BatchController, BatchTriggerService}.java
 │       │   ├── batch/dto/{ProgressResponse, CandidateResultDto}.java
 │       │   └── admin/{AdminController, dto/UpdateRoleRequest}.java
 │       ├── main/resources/application.yml
 │       └── test/java/com/resumescreener/api/   (mirrors main, JUnit 5 + Mockito)
 ├── batch-worker/
 │   ├── pom.xml
 │   └── src/
 │       ├── main/java/com/resumescreener/batch/
 │       │   ├── BatchWorkerApplication.java
 │       │   ├── config/{BatchConfig, MinioConfig, ResilienceConfig, SecurityConfig}.java
 │       │   ├── entity/{ScreeningSession, ScoringWeights, Candidate, CandidateScore, BatchJob}.java
 │       │   ├── enums/{SessionStatus, CandidateStatus}.java
 │       │   ├── repository/*.java
 │       │   ├── steps/{ExtractTextProcessor, ExtractTextWriter, ScoreLlmProcessor, ScoreLlmWriter,
 RankAndFinalizeTasklet}.java
 │       │   ├── pdf/PdfTextExtractor.java
 │       │   ├── llm/{OpenAiClient, PromptBuilder}.java
 │       │   ├── llm/dto/{LlmScoreDto, OpenAiRequest, OpenAiResponse}.java
 │       │   ├── excel/ExcelReportGenerator.java
 │       │   ├── storage/MinioStorageService.java
 │       │   └── web/JobTriggerController.java
 │       ├── main/resources/application.yml
 │       └── test/java/com/resumescreener/batch/
 └── ui/
     ├── package.json, vite.config.ts, tailwind.config.ts, tsconfig.json
     └── src/
         ├── main.tsx, App.tsx
         ├── api/client.ts
         ├── store/authStore.ts
         ├── types/index.ts
         ├── components/{ProtectedRoute, AdminRoute, Navbar, SessionCard, WeightSliders, RadarScoreChart}.tsx
         ├── components/ui/*                      (shadcn generated)
         └── pages/{Login, Register, Dashboard, NewSession, SessionDetail, CandidateDetail, AdminUsers}.tsx

 ---

 Task List

 Each task = TDD loop: test → verify fail → implement → verify pass → commit. Sub-agents recommended per task.

 ---

 Task 1: Docker Compose Infrastructure

 Files: Create docker-compose.yml, .env.example.

- Step 1: Write .env.example
  POSTGRES_DB=resume_screener
  POSTGRES_USER=rs_user
  POSTGRES_PASSWORD=rs_password
  MINIO_ROOT_USER=minio_admin
  MINIO_ROOT_PASSWORD=minio_password_123
  MINIO_BUCKET=resumes
  JWT_SECRET=ZGV2LXNlY3JldC1iYXNlNjQtZW5jb2RlZC1rZXktZm9yLWp3dC1oczI1Ni1zaWduaW5nLXVzZS1zdHJvbmdlci1pbi1wcm9k
  JWT_EXPIRATION_MS=86400000
  OPENAI_API_KEY=sk-REPLACE_ME
  OPENAI_MODEL=gpt-4o
  BATCH_WORKER_URL=http://batch-worker:8081
  BATCH_WORKER_SHARED_SECRET=change-me-shared-secret
  OPENAI_RATE_LIMIT_RPM=60

- Step 2: Write docker-compose.yml
  services:
  postgres:
    image: postgres:16-alpine
    environment:
  
      POSTGRES_DB: ${POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
  
    ports: ["5432:5432"]
    volumes: ["pg_data:/var/lib/postgresql/data"]
    healthcheck:
  
      test: ["CMD", "pg_isready", "-U", "${POSTGRES_USER}"]
      interval: 5s
  
  minio:
    image: minio/minio:latest
    command: server /data --console-address ":9001"
    environment:
  
      MINIO_ROOT_USER: ${MINIO_ROOT_USER}
      MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD}
  
    ports: ["9000:9000", "9001:9001"]
    volumes: ["minio_data:/data"]
  minio-init:
    image: minio/mc:latest
    depends_on: [minio]
    entrypoint: >
  
      /bin/sh -c "
      mc alias set local http://minio:9000 $${MINIO_ROOT_USER} $${MINIO_ROOT_PASSWORD};
      mc mb --ignore-existing local/$${MINIO_BUCKET};
      exit 0;"
  
    environment:
  
      MINIO_ROOT_USER: ${MINIO_ROOT_USER}
      MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD}
      MINIO_BUCKET: ${MINIO_BUCKET}
  
  api:
    build: ./api-service
    ports: ["8080:8080"]
    depends_on:
  
      postgres: { condition: service_healthy }
      minio: { condition: service_started }
  
    environment:
  
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB}
      SPRING_DATASOURCE_USERNAME: ${POSTGRES_USER}
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}
      MINIO_ENDPOINT: http://minio:9000
      MINIO_ACCESS_KEY: ${MINIO_ROOT_USER}
      MINIO_SECRET_KEY: ${MINIO_ROOT_PASSWORD}
      MINIO_BUCKET: ${MINIO_BUCKET}
      JWT_SECRET: ${JWT_SECRET}
      JWT_EXPIRATION_MS: ${JWT_EXPIRATION_MS}
      BATCH_WORKER_URL: ${BATCH_WORKER_URL}
      BATCH_WORKER_SHARED_SECRET: ${BATCH_WORKER_SHARED_SECRET}
  
  batch-worker:
    build: ./batch-worker
    ports: ["8081:8081"]
    depends_on:
  
      postgres: { condition: service_healthy }
      minio: { condition: service_started }
  
    environment:
  
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB}
      SPRING_DATASOURCE_USERNAME: ${POSTGRES_USER}
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}
      MINIO_ENDPOINT: http://minio:9000
      MINIO_ACCESS_KEY: ${MINIO_ROOT_USER}
      MINIO_SECRET_KEY: ${MINIO_ROOT_PASSWORD}
      MINIO_BUCKET: ${MINIO_BUCKET}
      OPENAI_API_KEY: ${OPENAI_API_KEY}
      OPENAI_MODEL: ${OPENAI_MODEL}
      OPENAI_RATE_LIMIT_RPM: ${OPENAI_RATE_LIMIT_RPM}
      BATCH_WORKER_SHARED_SECRET: ${BATCH_WORKER_SHARED_SECRET}
  
  ui:
    build: ./ui
    ports: ["3000:3000"]
    depends_on: [api]
    environment:
  
      VITE_API_URL: http://localhost:8080
  
  volumes:
  pg_data:
  minio_data:

- Step 3: Verify  docker compose config — prints valid config.

- Step 4: Commit — chore: scaffold docker compose infrastructure.

 ---

 Task 2: API Service — Maven + Bootstrap

 Files: Create api-service/pom.xml, ApiServiceApplication.java, application.yml, Dockerfile.

- Step 1: Write api-service/pom.xml — Spring Boot 3.3.4 parent; deps: web, security, data-jpa, validation, postgresql,
  lombok, hypersistence-utils-hibernate-63 (3.8.3), minio (8.5.11), jjwt-api/impl/jackson 0.12.6, test +
  spring-security-test.

- Step 2: Write application.yml
  server: { port: 8080 }
  spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL}
    username: ${SPRING_DATASOURCE_USERNAME}
    password: ${SPRING_DATASOURCE_PASSWORD}
  jpa:
    hibernate.ddl-auto: update
    properties.hibernate.dialect: org.hibernate.dialect.PostgreSQLDialect
    open-in-view: false
  servlet.multipart: { max-file-size: 10MB, max-request-size: 210MB }
  jwt:
  secret: ${JWT_SECRET}
  expiration-ms: ${JWT_EXPIRATION_MS:86400000}
  minio:
  endpoint: ${MINIO_ENDPOINT}
  access-key: ${MINIO_ACCESS_KEY}
  secret-key: ${MINIO_SECRET_KEY}
  bucket: ${MINIO_BUCKET}
  batch-worker:
  url: ${BATCH_WORKER_URL}
  shared-secret: ${BATCH_WORKER_SHARED_SECRET}

- Step 3: Write ApiServiceApplication.java — @SpringBootApplication main.

- Step 4: Write config/WebConfig.java — CORS: http://localhost:3000, all methods, Authorization header exposed.

- Step 5: Write Dockerfile — multi-stage maven build → eclipse-temurin:21-jre.

- Step 6: Run  ./mvnw test — passes (no tests yet, compiles clean).

- Step 7: Commit — feat(api): bootstrap Spring Boot service.

 ---

 Task 3: API Service — Enums, Entities, Repositories

 Files: enums/{Role,SessionStatus,CandidateStatus}.java, entity/*.java (6 files), repository/*.java (6 files).

- Step 1: Write enums — Role { ADMIN, RECRUITER }, SessionStatus { PENDING, RUNNING, DONE, FAILED }, CandidateStatus {
  PENDING, SCORED, FAILED }.
- Step 2: Write entities with Lombok @Entity @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder. Use
  @GeneratedValue(strategy = GenerationType.UUID) for PKs. skills_extracted in CandidateScore uses
  @JdbcTypeCode(SqlTypes.JSON) with List<String>. User.password_hash, User.role (@Enumerated STRING). ScoringWeights has
  @OneToOne to session. Candidate @ManyToOne session. CandidateScore @OneToOne candidate. BatchJob @ManyToOne session.
  Add @PrePersist setting created_at = Instant.now() where applicable.
- Step 3: Write repositories extending JpaRepository<Entity, UUID>. UserRepository.findByEmail(String).
  ScreeningSessionRepository.findByUserId(UUID, Pageable) + findByIdAndUserId(UUID, UUID).
  CandidateRepository.findBySessionIdAndStatus(UUID, CandidateStatus) + findBySessionId(UUID, Sort).
  CandidateScoreRepository.findBySessionId(UUID) via join. BatchJobRepository.findBySessionId(UUID).
  ScoringWeightsRepository.findBySessionId(UUID).
- Step 4: Test — @DataJpaTest entity persistence tests: save User, save Session with Weights, save Candidate + Score
  (JSONB roundtrip). Use Testcontainers Postgres or @AutoConfigureTestDatabase(replace = NONE) with in-memory H2
  disabled (JSONB requires real PG → use Testcontainers postgres:16-alpine).
- Step 5: Run  ./mvnw test -Dtest=*RepositoryTest — all green.
- Step 6: Commit — feat(api): add JPA entities and repositories.

 ---

 Task 4: API Service — JWT Auth

 Files: auth/{JwtService, JwtAuthFilter, UserDetailsServiceImpl}.java, config/SecurityConfig.java.

- Step 1: Test JwtService — token roundtrip: generateToken(email, role) → extractUsername() returns email; expired
  token → isTokenValid false.
- Step 2: Implement JwtService — JJWT 0.12.6 API: Jwts.builder().subject(email).claim("role",
  role).issuedAt(...).expiration(...).signWith(hmacShaKeyFor(Decoders.BASE64.decode(secret))).compact(). Parse:
  Jwts.parser().verifyWith(key).build().parseSignedClaims(token).
- Step 3: Implement UserDetailsServiceImpl — loads User by email, returns Spring UserDetails with ROLE_<role.name()>
  authority.
- Step 4: Implement JwtAuthFilter extends OncePerRequestFilter — reads Authorization: Bearer <token>, validates, sets
  SecurityContextHolder.
- Step 5: Implement SecurityConfig — SecurityFilterChain: CSRF disabled, stateless sessions, /api/auth/** permitAll,
  /api/admin/** hasRole("ADMIN"), all else authenticated. Adds JwtAuthFilter before
  UsernamePasswordAuthenticationFilter. BCryptPasswordEncoder bean. AuthenticationManager bean.
- Step 6: Run  ./mvnw test -Dtest=JwtServiceTest — green.
- Step 7: Commit — feat(api): add JWT auth infrastructure.

 ---

 Task 5: API Service — Register + Login Endpoints

 Files: auth/{AuthController, AuthService}.java, auth/dto/*.java.

- Step 1: Write DTOs — RegisterRequest { @Email email, @Size(min=8) password, String fullName }, LoginRequest { email,
  password }, AuthResponse { String token, UserDto user }.
- Step 2: Test AuthController using @WebMvcTest(AuthController.class) + @MockBean AuthService:
  - POST /api/auth/register valid → 200 + token.
  - POST /api/auth/register duplicate email → 409.
  - POST /api/auth/login invalid → 401.
- Step 3: Implement AuthService — register() checks userRepository.existsByEmail, hashes via BCryptPasswordEncoder,
  saves User { role = RECRUITER }, returns token. login() uses AuthenticationManager.authenticate, returns token on
  success.
- Step 4: Implement AuthController — @PostMapping register + login, delegates to service. Exception handler:
  EmailTakenException → 409, BadCredentialsException → 401.
- Step 5: Run  ./mvnw test -Dtest=AuthControllerTest — green.
- Step 6: Commit — feat(api): add register and login endpoints.

 ---

 Task 6: API Service — Session CRUD + Weights

 Files: session/{SessionController, SessionService}.java, session/dto/*.java.

- Step 1: Write DTOs — WeightsDto { @Min(0)@Max(100) technicalSkills, experience, education, softSkills, cultureFit }
+ custom validator @SumEquals100. CreateSessionRequest { @NotBlank title, String description, @Valid WeightsDto
  weights }. SessionResponse { id, title, status, createdAt, completedAt, weights, candidateCount }.
- Step 2: Test SessionService with @ExtendWith(MockitoExtension.class):
  - createSession persists Session + ScoringWeights atomically.
  - getSession(id, user) returns only if owner or ADMIN; else throws AccessDeniedException.
  - listSessions pages by current user.
- Step 3: Implement SessionService — @Transactional methods. createSession(req, user): persist session
  (status=PENDING) → persist weights linked. getSession/delete enforce ownership via RECRUITER role check. Uses
  SecurityContextHolder to resolve current user (helper method getCurrentUser() via UserRepository lookup).
- Step 4: Test SessionController via @WebMvcTest — verify JWT auth required, CRUD routes, validation errors return

 400.

- Step 5: Implement SessionController — REST mappings per spec §5 Sessions.
- Step 6: Run  ./mvnw test — all green.
- Step 7: Commit — feat(api): add session CRUD with weighted scoring config.

 ---

 Task 7: API Service — MinIO File Upload

 Files: config/MinioConfig.java, upload/{MinioUploadService, UploadController}.java.

- Step 1: Implement MinioConfig — @Bean MinioClient from minio.endpoint/access-key/secret-key.
- Step 2: Test MinioUploadService — Testcontainers MinIO module: upload PDF bytes, verify bucket contains object with
  returned key; verify getPresignedUrl produces valid URL.
- Step 3: Implement MinioUploadService — uploadJd(sessionId, MultipartFile) → String key (key pattern
  jd/{sessionId}/{uuid}.pdf). uploadResume(sessionId, MultipartFile) → String key (resumes/{sessionId}/{uuid}.pdf).
  getPresignedUrl(key, 15 min). Validates content-type application/pdf + size limits (JD ≤5MB, resume ≤10MB).
- Step 4: Test UploadController — POST /api/sessions/{id}/jd multipart → 200 + updates session.jdFileKey; POST
  /api/sessions/{id}/resumes multipart[] → creates N Candidate rows (status=PENDING, resume_file_key set). Rejects if
  session not owned.
- Step 5: Implement UploadController — calls service; for resumes, loops and creates candidate rows. Caps at 200 files
  — return 400 above.
- Step 6: Run  ./mvnw test — all green.
- Step 7: Commit — feat(api): add MinIO file upload endpoints.

 ---

 Task 8: API Service — Batch Trigger, Progress, Results, Admin

 Files: batch/{BatchController, BatchTriggerService}.java, batch/dto/*.java, admin/*.java.

- Step 1: Write DTOs — ProgressResponse { total, processed, failed, status, percent }, CandidateResultDto { id, rank,
  name, email, compositeScore, technical, experience, education, softSkills, cultureFit }.
- Step 2: Test BatchTriggerService — mocks RestTemplate, verifies POST to ${batch-worker.url}/internal/jobs/start with
  header X-Internal-Secret, body { sessionId }. Updates session status → RUNNING.
- Step 3: Implement BatchTriggerService — RestTemplate bean, calls batch worker with shared secret. Wraps call in
  try/catch; on failure marks session FAILED and rethrows.
- Step 4: Implement BatchController:
  - POST /api/sessions/{id}/start — validates session owned + has JD + has ≥1 candidate → triggers service.
  - GET /api/sessions/{id}/progress — reads BatchJob row + counts candidates by status → ProgressResponse.
  - GET /api/sessions/{id}/candidates — returns ranked list joining Candidate + CandidateScore ordered by rank.
  - GET /api/sessions/{id}/candidates/{cid} — full detail incl. justification, strengths, gaps, skills JSONB.
  - GET /api/sessions/{id}/export — returns { url } MinIO presigned URL for Excel report key stored on session.
- Step 5: Write AdminController — GET /api/admin/users (paginated), PATCH /api/admin/users/{id}/role.
  @PreAuthorize("hasRole('ADMIN')").
- Step 6: Run  ./mvnw test — all green.
- Step 7: Commit — feat(api): add batch trigger, progress, results, and admin endpoints.

 ---

 Task 9: Batch Worker — Maven Bootstrap + Shared-Secret Filter

 Files: batch-worker/pom.xml, BatchWorkerApplication.java, application.yml, config/SecurityConfig.java, Dockerfile.

- Step 1: Write pom.xml — Spring Boot 3.3.4 parent; deps: web, data-jpa, batch, lombok, postgresql,
  hypersistence-utils, minio 8.5.11, pdfbox 3.0.3, poi-ooxml 5.3.0, resilience4j-spring-boot3 2.2.0, jackson-databind,
  test.
- Step 2: Write application.yml — DB + MinIO + openai.{api-key,model,rate-limit-rpm}, internal.shared-secret,
  server.port: 8081. Spring Batch: spring.batch.jdbc.initialize-schema: always.
- Step 3: Write BatchWorkerApplication.java — @SpringBootApplication @EnableBatchProcessing.
- Step 4: Test SecurityConfig filter — request missing X-Internal-Secret → 401; valid → passes through.
- Step 5: Implement SecurityConfig — custom OncePerRequestFilter validates header against internal.shared-secret,
  applied to /internal/** only. Rest: permitAll (service behind Docker network).
- Step 6: Write Dockerfile — same pattern as API.
- Step 7: Run  ./mvnw test — green.
- Step 8: Commit — feat(batch): bootstrap batch worker with internal auth.

 ---

 Task 10: Batch Worker — Entities + Repositories

 Files: mirror the API's ScreeningSession, ScoringWeights, Candidate, CandidateScore, BatchJob, enums SessionStatus,
 CandidateStatus. repository/*.java.

- Step 1: Copy entities — identical to API (shared schema). Package com.resumescreener.batch.entity. Same annotations.
- Step 2: Write repositories — CandidateRepository.findBySessionIdAndStatus(UUID, CandidateStatus) with paging;
  CandidateScoreRepository.findBySessionId(UUID) via JPQL join; ScreeningSessionRepository,
  ScoringWeightsRepository.findBySessionId, BatchJobRepository.
- Step 3: @DataJpaTest — Testcontainers PG; verify queries return expected candidates.
- Step 4: Commit — feat(batch): add shared JPA entities and repositories.

 ---

 Task 11: Batch Worker — MinIO Storage + PDF Extraction

 Files: storage/MinioStorageService.java, pdf/PdfTextExtractor.java, config/MinioConfig.java.

- Step 1: Implement MinioConfig — same as API.
- Step 2: Test MinioStorageService — Testcontainers MinIO: downloadBytes(key) returns uploaded bytes; uploadBytes(key,
  bytes, contentType) persists.
- Step 3: Implement MinioStorageService — downloadBytes, uploadBytes, methods wrap MinioClient.getObject/putObject.
- Step 4: Test PdfTextExtractor — resource fixture sample-resume.pdf → extract(bytes) returns non-empty string
  containing known word; cleaning strips repeated header lines + normalizes whitespace (collapses runs of \s+ to single
  space, preserves paragraph breaks).
- Step 5: Implement PdfTextExtractor — Loader.loadPDF(bytes) (PDFBox 3 API) → PDFTextStripper.getText(doc) → clean:
  strip lines matching repeated header/footer pattern across pages, normalize whitespace via regex.
- Step 6: Run tests — green.
- Step 7: Commit — feat(batch): add MinIO storage and PDF text extraction.

 ---

 Task 12: Batch Worker — OpenAI Client + Prompt Builder

 Files: llm/{OpenAiClient, PromptBuilder}.java, llm/dto/{LlmScoreDto, OpenAiRequest, OpenAiResponse}.java,
 config/ResilienceConfig.java.

- Step 1: Write DTOs — LlmScoreDto { int technicalSkills, experience, education, softSkills, cultureFit; List<String>
  skills; String justification, strengths, gaps }. OpenAI request/response records matching JSON schema.
- Step 2: Test PromptBuilder — given JD + resume text, output matches spec §7 prompt shape; resume chunks >3500 tokens
  (char/4 heuristic) split; JD truncated at ~1000 tokens.
- Step 3: Implement PromptBuilder — buildMessages(jdText, resumeChunk) → List<Message>. chunkResume(text) →
  List<String> splits on paragraph boundaries not mid-sentence.
- Step 4: Implement ResilienceConfig — Retry bean (3 attempts, exponential 1s→4s, retry on 429/5xx
  HttpStatusCodeException). RateLimiter bean (limitForPeriod = rpm, limitRefreshPeriod = 1 minute).
- Step 5: Test OpenAiClient — WireMock or MockRestServiceServer; verify headers (Authorization: Bearer …), model =
  gpt-4o, temperature 0.2; parse response choices[0].message.content as JSON → LlmScoreDto. Simulate 429 → retries 3x,
  then throws. Simulate malformed JSON → throws LlmResponseException.
- Step 6: Implement OpenAiClient — RestTemplate, annotated with @Retry(name="openai") @RateLimiter(name="openai").
  score(jdText, resumeChunk) → LlmScoreDto. When chunks >1, call per chunk, average numeric fields, merge skills
  distinct, concat justifications.
- Step 7: Run tests — green.
- Step 8: Commit — feat(batch): add OpenAI client with Resilience4j retry and rate limiting.

 ---

 Task 13: Batch Worker — Extract Text Step + Score LLM Step

 Files: steps/{ExtractTextProcessor, ExtractTextWriter, ScoreLlmProcessor, ScoreLlmWriter}.java.

- Step 1: Test ExtractTextProcessor — mock MinioStorageService + PdfTextExtractor; input Candidate { resumeFileKey } →
  output candidate with rawText set. PDF failure → candidate status FAILED, processor returns null (filter out).
- Step 2: Implement ExtractTextProcessor implements ItemProcessor<Candidate, Candidate> — downloads PDF, extracts
  text, populates rawText. On exception: sets status FAILED, returns null.
- Step 3: Implement ExtractTextWriter implements ItemWriter<Candidate> — saves via candidateRepository.saveAll(chunk).
- Step 4: Test ScoreLlmProcessor — mock OpenAiClient + promptBuilder; input Candidate with rawText → output
  CandidateScore populated from LlmScoreDto. LLM failure → returns null + marks candidate FAILED.
- Step 5: Implement ScoreLlmProcessor implements ItemProcessor<Candidate, CandidateScore> — reads session JD text via
  session → jd_file_key → MinIO download → extract; calls OpenAiClient.score(); maps to CandidateScore (rank + composite
  score left null, filled in Task 14). Updates candidate status SCORED on success.
- Step 6: Implement ScoreLlmWriter — saves List<CandidateScore> + related candidate status updates.
- Step 7: Run step unit tests — green.
- Step 8: Commit — feat(batch): add text extraction and LLM scoring step logic.

 ---

 Task 14: Batch Worker — Rank Tasklet + Excel Export

 Files: excel/ExcelReportGenerator.java, steps/RankAndFinalizeTasklet.java.

- Step 1: Test ExcelReportGenerator — given list of CandidateScore + weights + session info → returns bytes of valid
  .xlsx (open with POI, assert 4 sheets with correct headers, correct row count on Sheet 1, Skills Matrix has union of
  skill columns, conditional formatting present).
- Step 2: Implement ExcelReportGenerator — XSSFWorkbook. Sheet 1 "Ranking Summary" cols per spec §8, conditional
  formatting via SheetConditionalFormatting: top 20% green, bottom 20% red on composite score. Sheet 2 "Skills Matrix" —
  build distinct skill set, check ✓ per row. Sheet 3 "Justifications". Sheet 4 "Session Config". Auto-size columns.
- Step 3: Test RankAndFinalizeTasklet — mock repos + excel + storage; verify composite = Σ(dim × weight) / 100
  computed per candidate, sorted desc, ranks 1..N assigned, Excel uploaded to reports/{sessionId}/{uuid}.xlsx, session
  status set DONE + report_file_key populated, batch_job.finished_at set.
- Step 4: Add field — ScreeningSession.reportFileKey VARCHAR (also add to API entity mirror to expose via export
  endpoint).
- Step 5: Implement RankAndFinalizeTasklet implements Tasklet — load scores + weights + candidates, compute composite,
  sort, assign rank, saveAll. Generate Excel bytes, upload via MinioStorageService, update session.
- Step 6: Run tests — green.
- Step 7: Commit — feat(batch): add ranking tasklet and Excel report generator.

 ---

 Task 15: Batch Worker — Job Assembly + Trigger Controller

 Files: config/BatchConfig.java, web/JobTriggerController.java.

- Step 1: Implement BatchConfig — Spring Batch 5 API:
  - JpaPagingItemReader<Candidate> for step 1 (query: session_id=? AND status=PENDING).
  - Step 1 extractTextStep: new StepBuilder("extractTextStep", jobRepo).<Candidate,Candidate>chunk(10,
    txManager).reader(reader).processor(extractProcessor).writer(extractWriter).build().
  - Step 2 scoreLlmStep: chunk 5, input Candidate (status=SCORED-or-filled-raw), output CandidateScore.
  - Step 3 rankAndFinalizeStep: new StepBuilder(..).tasklet(rankTasklet, txManager).build().
  - Job: new JobBuilder("resumeScreeningJob", jobRepo).start(extract).next(score).next(rank).build().
  - All readers @StepScope with @Value("#{jobParameters['sessionId']}").
- Step 2: Test JobTriggerController — MockMvc, POST /internal/jobs/start with header + body {sessionId} launches job
  via JobLauncher; missing header → 401. On launch, inserts BatchJob row, starts job async.
- Step 3: Implement JobTriggerController — uses JobLauncher.run(job, new JobParametersBuilder().addString("sessionId",
  id).addLong("timestamp", now).toJobParameters()). Creates BatchJob row with total = candidate count, started_at =
  now.
- Step 4: Integration test — @SpringBootTest, Testcontainers PG + MinIO + WireMock for OpenAI. Seed session + weights
+ 3 candidates + JD PDF → trigger job → wait for completion → assert scores populated, ranks assigned, session status
  DONE, Excel uploaded to MinIO.
- Step 5: Run tests — green.
- Step 6: Commit — feat(batch): wire Spring Batch job and internal trigger endpoint.

 ---

 Task 16: UI — Vite + Tailwind + shadcn + Auth Foundation

 Files: ui/package.json, ui/src/{main.tsx, App.tsx, types/index.ts, api/client.ts, store/authStore.ts}, shadcn init +
 components/{ProtectedRoute, AdminRoute, Navbar}.tsx, Dockerfile.

- Step 1: Scaffold  npm create vite@latest . -- --template react-ts. Add Tailwind (npm i -D tailwindcss postcss
  autoprefixer + tailwindcss init -p). Install: axios zustand react-router-dom recharts react-hook-form
  @hookform/resolvers zod lucide-react clsx tailwind-merge. Run npx shadcn@latest init (New York, neutral). Add
  components: button input label card badge table dialog progress tabs slider separator dropdown-menu form sonner.
- Step 2: Write types/index.ts — interfaces matching API DTOs: User, Session, Weights, Candidate, CandidateScore,
  ProgressResponse, CandidateResultDto.
- Step 3: Write api/client.ts — Axios instance, baseURL import.meta.env.VITE_API_URL, request interceptor attaches
  Authorization: Bearer ${token} from Zustand store, response 401 → clears auth + redirects /login.
- Step 4: Write store/authStore.ts — Zustand with persist middleware (localStorage): { token, user, login(token,
  user), logout() }.
- Step 5: Test ProtectedRoute + AdminRoute — Vitest + RTL: unauthenticated → <Navigate to="/login"/>; non-admin on
  admin route → /dashboard; admin + authed → children render.
- Step 6: Implement route guards — read authStore, conditionally render <Outlet/> vs <Navigate>.
- Step 7: Write Navbar — logo + app name + user email + logout button.
- Step 8: Write Dockerfile — Node 20 build → nginx serve. Expose 3000.
- Step 9: Run  npm test — green.
- Step 10: Commit — feat(ui): scaffold React app with auth plumbing.

 ---

 Task 17: UI — Login + Register Pages

 Files: pages/{Login, Register}.tsx, update App.tsx routes.

- Step 1: Test Login — RTL: renders email + password; invalid credentials → shows banner "Invalid email or password";
  success → navigates /dashboard. Use msw to stub /api/auth/login.
- Step 2: Implement Login — shadcn Card, react-hook-form + zod schema; POST via client.post('/api/auth/login'); on
  success call authStore.login → navigate('/dashboard').
- Step 3: Test Register — password mismatch inline error; duplicate email → banner; success redirects.
- Step 4: Implement Register — zod schema (email, password min 8, confirmPassword refine match); POST
  /api/auth/register → login on success.
- Step 5: Wire App.tsx — BrowserRouter, /login, /register public; other routes wrapped in ProtectedRoute.
- Step 6: Run tests — green.
- Step 7: Commit — feat(ui): add login and register screens.

 ---

 Task 18: UI — Dashboard

 Files: pages/Dashboard.tsx, components/SessionCard.tsx.

- Step 1: Test SessionCard — renders title, status badge color matches status, View Results button only when DONE.
- Step 2: Implement SessionCard — shadcn Card + Badge (gray/blue/green/red by status) + progress bar when RUNNING.
- Step 3: Test Dashboard — msw stubs GET /api/sessions?page=0 → list; renders 3 cards; search filter narrows list by
  title (client-side filter over current page); status tabs filter; empty state shows illustration + "No sessions yet"
  CTA.
- Step 4: Implement Dashboard — grid 3 cols lg / 1 col sm, search input, tabs (All/Pending/Running/Done/Failed), + New
  Session → /sessions/new.
- Step 5: Run tests — green.
- Step 6: Commit — feat(ui): add dashboard with session grid and filters.

 ---

 Task 19: UI — New Session Wizard

 Files: pages/NewSession.tsx, components/WeightSliders.tsx.

- Step 1: Test WeightSliders — 5 shadcn Slider components with defaults 30/25/15/20/10; live sum label green when
  =100, red otherwise.
- Step 2: Implement WeightSliders — controlled from parent form state.
- Step 3: Test NewSession Stepper — 3 steps: "Session Info" → "Upload Files" → "Configure & Launch". Step 1 Next
  disabled until title non-empty. Step 2 Next disabled until JD + ≥1 resume uploaded. Step 3 Launch disabled unless
  sum=100.
- Step 4: Implement NewSession:
  - Step 1: title + description inputs, stores form state.
  - On "Next" from step 1 → POST /api/sessions with weights placeholder → store id.
  - Step 2: two dropzones (react-dropzone). JD: accept:{ 'application/pdf': ['.pdf']}, max 5MB → POST
    /api/sessions/{id}/jd. Resumes: multiple, max 10MB each, max 200 → POST /api/sessions/{id}/resumes (one request,
    FormData with multiple files).
  - Step 3: WeightSliders; on launch → PATCH weights (or include in initial create) → POST /api/sessions/{id}/start →
    navigate('/sessions/{id}').
- Step 5: Run tests — green.
- Step 6: Commit — feat(ui): add new session wizard with upload and weights.

 ---

 Task 20: UI — Session Detail + Candidate Detail + Admin

 Files: pages/{SessionDetail, CandidateDetail, AdminUsers}.tsx, components/RadarScoreChart.tsx.

- Step 1: Test SessionDetail State A (RUNNING) — polls GET /api/sessions/{id}/progress every 3s (use
  vi.useFakeTimers), shows processed/total + percent + step indicators.
- Step 2: Test SessionDetail State B (DONE) — GET /api/sessions/{id}/candidates → results table with
  rank/name/email/score, sortable headers, pagination 20 per page, Download Excel button fetches
  /api/sessions/{id}/export then opens returned presigned URL.
- Step 3: Test State C (FAILED) — red banner + retry button POSTs /start again.
- Step 4: Implement SessionDetail — useQuery-like polling via setInterval (clear on unmount + on status==DONE);
  conditional rendering per status; shadcn Table with sort state.
- Step 5: Test + implement RadarScoreChart — Recharts <RadarChart> with 5 dims, range 0–100.
- Step 6: Test + implement CandidateDetail — GET /api/sessions/{id}/candidates/{cid}: left column (name, email, skills
  pills, resume link via presigned URL), right (radar + breakdown table with Score/Weight/Weighted cols + composite row
  + Justification quote + Strengths/Gaps bulleted).
- Step 7: Test + implement AdminUsers — GET /api/admin/users, table, inline role dropdown → PATCH
  /api/admin/users/{id}/role. Route under AdminRoute guard.
- Step 8: Wire all routes in App.tsx — /dashboard, /sessions/new, /sessions/:id, /sessions/:id/candidate/:cid,
  /admin/users.
- Step 9: Run all UI tests — green.
- Step 10: Commit — feat(ui): add session detail, candidate detail, and admin panel.

 ---

 Verification (End-to-End)

1. cp .env.example .env → set real OPENAI_API_KEY.
2. docker compose build && docker compose up -d.
3. Open http://localhost:3000/register → create recruiter account → log in.
4. New Session → enter title → upload a JD PDF + 3 resume PDFs → sum weights to 100 → Launch.
5. Session Detail auto-polls progress; step indicators advance; completes in ~30–60s for 3 resumes.
6. Results table shows 3 ranked candidates with scores.
7. Click a candidate → radar chart + breakdown + LLM justification visible.
8. Download Excel → opens 4-sheet workbook with ranking, skills matrix, justifications, config.
    │ 4. New Session → enter title → upload a JD PDF + 3 resume PDFs → sum weights to 100 → Launch.                   │
    │ 5. Session Detail auto-polls progress; step indicators advance; completes in ~30–60s for 3 resumes.             │
    │ 6. Results table shows 3 ranked candidates with scores.                                                         │
    │ 7. Click a candidate → radar chart + breakdown + LLM justification visible.                                     │
    │ 8. Download Excel → opens 4-sheet workbook with ranking, skills matrix, justifications, config.                 │
    │ 9. Promote user to ADMIN via DB (or via another admin) → /admin/users accessible → role change persists.        │
    │ 10. Run full test suites: cd api-service && ./mvnw verify + cd batch-worker && ./mvnw verify + cd ui && npm     │
    │ test — all green.                                                                                               │
    │                                                                                                                 │
    │ Failure modes to exercise manually:                                                                             │
    │ - Kill OpenAI key mid-run → candidates marked FAILED, batch completes with partial results.                     │
    │ - Upload non-PDF → API rejects 400.                                                                             │
    │ - Weights summing ≠100 → Launch button disabled client-side + API 400.                                          │
    │                                                                                                                 │
    │ ---                                                                                                             │
    │ Self-Review Summary                                                                                             │
    │                                                                                                                 │
    │ - Spec coverage: all 11 spec sections mapped to tasks (infra §11 → T1; stack §2 → T2/T9/T16; data model §4 →    │
    │ T3/T10; API §5 → T4–T8; batch §6 → T13–T15; prompt §7 → T12; Excel §8 → T14; UI §9 → T17–T20; NFR §10 →         │
    │ T1+T9+T12).                                                                                                     │
    │ - No placeholders: every step states exact file, code shape, or command.                                        │
    │ - Type consistency: CandidateScore field names identical across entity (T3/T10), DTO (T8 CandidateResultDto),   │
    │ and UI (T16 types/index.ts); LlmScoreDto shape matches prompt JSON in T12.                                      │
    │ - Scope: single plan; services sequenced so each is self-testable before the next consumes it.                  │
    ╰─────────────────────────────────────────────────────────────────────────────────────────────────────────────────╯
