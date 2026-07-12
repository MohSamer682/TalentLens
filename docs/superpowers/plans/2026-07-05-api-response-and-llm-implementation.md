# API Response and LLM Reliability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Standardize all backend responses as typed success/error envelopes and make LLM scoring provider-neutral, validated, and free of static fallback scores.

**Architecture:** Add a small API response layer in `api-service`, then wrap the current controller payloads without changing route paths. Split LLM parsing/validation into a pure parser and rename the HTTP client to `LlmClient`, so provider behavior can be tested without a live API call.

**Tech Stack:** Spring Boot 3.3.4, Java 21, Lombok, Jackson, JUnit 5, Mockito, React 18, Axios, TypeScript, Vite.

**Git Note:** The current workspace is not a Git repository, so commit steps are replaced with verification checkpoints. Do not run destructive Git commands.

---

## File Structure

Create:
- `api-service/src/main/java/com/resumescreener/api/common/ApiResponse.java` - generic success/error response envelope.
- `api-service/src/main/java/com/resumescreener/api/common/ApiError.java` - typed error payload.
- `api-service/src/main/java/com/resumescreener/api/common/ApiErrorCode.java` - stable backend error codes.
- `api-service/src/main/java/com/resumescreener/api/common/ApiException.java` - runtime exception with status and error code.
- `api-service/src/main/java/com/resumescreener/api/upload/dto/UploadJdResponse.java` - JD upload data payload.
- `api-service/src/main/java/com/resumescreener/api/upload/dto/UploadResumesResponse.java` - resume upload data payload.
- `api-service/src/main/java/com/resumescreener/api/batch/dto/SessionActionResponse.java` - start/cancel/delete state payload.
- `api-service/src/main/java/com/resumescreener/api/batch/dto/ExportResponse.java` - export URL payload.
- `api-service/src/test/java/com/resumescreener/api/common/ApiResponseContractTest.java` - response JSON contract tests.
- `batch-worker/src/main/java/com/resumescreener/batch/llm/LlmClient.java` - provider-neutral chat completions client.
- `batch-worker/src/main/java/com/resumescreener/batch/llm/LlmScoreParser.java` - pure parser/validator for model JSON.
- `batch-worker/src/main/java/com/resumescreener/batch/llm/LlmException.java` - typed LLM failure.
- `batch-worker/src/test/java/com/resumescreener/batch/llm/LlmScoreParserTest.java` - parser red/green tests.
- `batch-worker/src/test/java/com/resumescreener/batch/web/CandidateProcessingServiceTest.java` - proves invalid LLM output does not save static scores.

Modify:
- `api-service/src/main/java/com/resumescreener/api/config/GlobalExceptionHandler.java` - return `ApiResponse` error bodies.
- `api-service/src/main/java/com/resumescreener/api/auth/AuthController.java` - wrap auth responses.
- `api-service/src/main/java/com/resumescreener/api/admin/AdminController.java` - wrap admin responses.
- `api-service/src/main/java/com/resumescreener/api/upload/UploadController.java` - wrap upload responses and use typed payloads.
- `api-service/src/main/java/com/resumescreener/api/upload/MinioUploadService.java` - return uploaded candidate IDs.
- `api-service/src/main/java/com/resumescreener/api/session/SessionController.java` - wrap session responses and return delete data.
- `api-service/src/main/java/com/resumescreener/api/batch/BatchController.java` - wrap start/progress/candidate/export responses.
- `api-service/src/main/java/com/resumescreener/api/batch/BatchTriggerService.java` - throw typed batch worker errors.
- `batch-worker/src/main/java/com/resumescreener/batch/web/CandidateProcessingService.java` - inject `LlmClient`.
- `ui/src/types/index.ts` - add `ApiResponse`, `ApiError`, upload/export/action payload types.
- `ui/src/api/client.ts` - unwrap successful API envelopes and normalize errors once.
- `maintenance/tests/candidate-results-contract.test.mjs` - extend contract to include wrapped response expectations.

---

### Task 1: Add API Envelope and Error Primitives

**Files:**
- Create: `api-service/src/main/java/com/resumescreener/api/common/ApiResponse.java`
- Create: `api-service/src/main/java/com/resumescreener/api/common/ApiError.java`
- Create: `api-service/src/main/java/com/resumescreener/api/common/ApiErrorCode.java`
- Create: `api-service/src/main/java/com/resumescreener/api/common/ApiException.java`
- Create: `api-service/src/test/java/com/resumescreener/api/common/ApiResponseContractTest.java`
- Modify: `api-service/src/main/java/com/resumescreener/api/config/GlobalExceptionHandler.java`

- [ ] **Step 1: Write the failing API response contract test**

Create `api-service/src/test/java/com/resumescreener/api/common/ApiResponseContractTest.java`:

```java
package com.resumescreener.api.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void successResponseSerializesPredictably() throws Exception {
        ApiResponse<Map<String, String>> response =
                ApiResponse.success("JD uploaded successfully", Map.of("sessionId", "session-123"));

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"success\":true");
        assertThat(json).contains("\"message\":\"JD uploaded successfully\"");
        assertThat(json).contains("\"data\":{\"sessionId\":\"session-123\"}");
        assertThat(json).doesNotContain("\"error\"");
    }

    @Test
    void errorResponseSerializesPredictably() throws Exception {
        ApiResponse<Void> response =
                ApiResponse.failure("Failed to upload JD",
                        new ApiError(ApiErrorCode.MINIO_UNAVAILABLE, "Temporary failure in name resolution"));

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"success\":false");
        assertThat(json).contains("\"message\":\"Failed to upload JD\"");
        assertThat(json).contains("\"code\":\"MINIO_UNAVAILABLE\"");
        assertThat(json).contains("\"details\":\"Temporary failure in name resolution\"");
        assertThat(json).doesNotContain("\"data\"");
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails because classes do not exist**

Run:

```powershell
& 'C:\Users\Muham\.m2\wrapper\dists\apache-maven-3.9.14-bin\1cb7fhup6b5n3bed6kckbrnspv\apache-maven-3.9.14\bin\mvn.cmd' -pl api-service -am -Dtest=ApiResponseContractTest test
```

Expected: compilation fails with missing `ApiResponse`, `ApiError`, or `ApiErrorCode`.

- [ ] **Step 3: Implement `ApiErrorCode`**

Create `api-service/src/main/java/com/resumescreener/api/common/ApiErrorCode.java`:

```java
package com.resumescreener.api.common;

public enum ApiErrorCode {
    VALIDATION_ERROR,
    AUTHENTICATION_FAILED,
    ACCESS_DENIED,
    NOT_FOUND,
    MINIO_UNAVAILABLE,
    BATCH_WORKER_UNAVAILABLE,
    LLM_CONFIGURATION_ERROR,
    LLM_PROVIDER_ERROR,
    LLM_RESPONSE_ERROR,
    INTERNAL_ERROR
}
```

- [ ] **Step 4: Implement `ApiError`**

Create `api-service/src/main/java/com/resumescreener/api/common/ApiError.java`:

```java
package com.resumescreener.api.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiError {
    private ApiErrorCode code;
    private String details;
}
```

- [ ] **Step 5: Implement `ApiResponse`**

Create `api-service/src/main/java/com/resumescreener/api/common/ApiResponse.java`:

```java
package com.resumescreener.api.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private ApiError error;

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, null);
    }

    public static <T> ApiResponse<T> failure(String message, ApiError error) {
        return new ApiResponse<>(false, message, null, error);
    }
}
```

- [ ] **Step 6: Implement `ApiException`**

Create `api-service/src/main/java/com/resumescreener/api/common/ApiException.java`:

```java
package com.resumescreener.api.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final ApiErrorCode code;
    private final String details;

    public ApiException(HttpStatus status, ApiErrorCode code, String message, String details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }
}
```

- [ ] **Step 7: Update global exception handling**

Replace `api-service/src/main/java/com/resumescreener/api/config/GlobalExceptionHandler.java` with:

```java
package com.resumescreener.api.config;

import com.resumescreener.api.auth.EmailTakenException;
import com.resumescreener.api.common.ApiError;
import com.resumescreener.api.common.ApiErrorCode;
import com.resumescreener.api.common.ApiException;
import com.resumescreener.api.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.net.UnknownHostException;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException e) {
        return ResponseEntity.status(e.getStatus())
                .body(ApiResponse.failure(e.getMessage(), new ApiError(e.getCode(), e.getDetails())));
    }

    @ExceptionHandler(EmailTakenException.class)
    public ResponseEntity<ApiResponse<Void>> handleEmailTaken(EmailTakenException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.failure("Email already registered",
                        new ApiError(ApiErrorCode.VALIDATION_ERROR, e.getMessage())));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.failure("Invalid email or password",
                        new ApiError(ApiErrorCode.AUTHENTICATION_FAILED, e.getMessage())));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.failure("Access denied",
                        new ApiError(ApiErrorCode.ACCESS_DENIED, e.getMessage())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String details = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Request validation failed");
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure("Validation failed",
                        new ApiError(ApiErrorCode.VALIDATION_ERROR, details)));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntimeException(RuntimeException e) {
        ApiErrorCode code = containsCause(e, UnknownHostException.class)
                ? ApiErrorCode.MINIO_UNAVAILABLE
                : ApiErrorCode.VALIDATION_ERROR;
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure(e.getMessage(), new ApiError(code, safeDetails(e))));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure("An unexpected error occurred",
                        new ApiError(ApiErrorCode.INTERNAL_ERROR, safeDetails(e))));
    }

    private boolean containsCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String safeDetails(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank()
                ? throwable.getClass().getSimpleName()
                : message;
    }
}
```

- [ ] **Step 8: Run the contract test and confirm it passes**

Run:

```powershell
& 'C:\Users\Muham\.m2\wrapper\dists\apache-maven-3.9.14-bin\1cb7fhup6b5n3bed6kckbrnspv\apache-maven-3.9.14\bin\mvn.cmd' -pl api-service -am -Dtest=ApiResponseContractTest test
```

Expected: `BUILD SUCCESS`.

---

### Task 2: Wrap Upload Endpoints With Typed Payloads

**Files:**
- Create: `api-service/src/main/java/com/resumescreener/api/upload/dto/UploadJdResponse.java`
- Create: `api-service/src/main/java/com/resumescreener/api/upload/dto/UploadResumesResponse.java`
- Modify: `api-service/src/main/java/com/resumescreener/api/upload/UploadController.java`
- Modify: `api-service/src/main/java/com/resumescreener/api/upload/MinioUploadService.java`

- [ ] **Step 1: Write a source contract test for upload response DTOs**

Create `maintenance/tests/api-wrapper-contract.test.mjs`:

```javascript
import { readFileSync } from 'node:fs';
import assert from 'node:assert/strict';
import path from 'node:path';

const root = process.cwd();
const read = (file) => readFileSync(path.join(root, file), 'utf8');

const uploadController = read('api-service/src/main/java/com/resumescreener/api/upload/UploadController.java');

assert.match(uploadController, /ResponseEntity<ApiResponse<UploadJdResponse>>/);
assert.match(uploadController, /ApiResponse\.success\("JD uploaded successfully"/);
assert.match(uploadController, /ResponseEntity<ApiResponse<UploadResumesResponse>>/);
assert.match(uploadController, /ApiResponse\.success\("Resumes uploaded successfully"/);
```

- [ ] **Step 2: Run the source contract and confirm it fails**

Run:

```powershell
node maintenance\tests\api-wrapper-contract.test.mjs
```

Expected: assertion failure because the upload controller still returns raw `String` and `Object`.

- [ ] **Step 3: Create upload DTOs**

Create `api-service/src/main/java/com/resumescreener/api/upload/dto/UploadJdResponse.java`:

```java
package com.resumescreener.api.upload.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadJdResponse {
    private String sessionId;
    private String jdFileKey;
    private String fileName;
    private long sizeBytes;
}
```

Create `api-service/src/main/java/com/resumescreener/api/upload/dto/UploadResumesResponse.java`:

```java
package com.resumescreener.api.upload.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadResumesResponse {
    private String sessionId;
    private int uploadedCount;
    private List<String> candidateIds;
}
```

- [ ] **Step 4: Update upload service to return candidate IDs**

In `api-service/src/main/java/com/resumescreener/api/upload/MinioUploadService.java`, change the method signature:

```java
public java.util.List<String> uploadResumes(String sessionId, MultipartFile[] files, String userEmail) {
```

Inside the method, replace `int count = 0;` with:

```java
java.util.List<String> candidateIds = new java.util.ArrayList<>();
int count = 0;
```

After `candidateRepository.save(candidate);`, add:

```java
candidateIds.add(candidate.getId());
```

Replace `return count;` with:

```java
return candidateIds;
```

- [ ] **Step 5: Wrap upload controller responses**

Update `api-service/src/main/java/com/resumescreener/api/upload/UploadController.java` imports:

```java
import com.resumescreener.api.common.ApiError;
import com.resumescreener.api.common.ApiErrorCode;
import com.resumescreener.api.common.ApiException;
import com.resumescreener.api.common.ApiResponse;
import com.resumescreener.api.upload.dto.UploadJdResponse;
import com.resumescreener.api.upload.dto.UploadResumesResponse;
import org.springframework.http.HttpStatus;
```

Change the JD method:

```java
@PostMapping("/{id}/jd")
public ResponseEntity<ApiResponse<UploadJdResponse>> uploadJd(
        @PathVariable String id,
        @RequestParam("file") MultipartFile file,
        Authentication auth) {

    String contentType = file.getContentType();
    if (contentType == null || !contentType.equals("application/pdf")) {
        throw new ApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_ERROR,
                "Only PDF files are allowed", "Rejected file: " + file.getOriginalFilename());
    }
    if (file.getSize() > 5 * 1024 * 1024) {
        throw new ApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_ERROR,
                "File too large", "JD max size is 5MB: " + file.getOriginalFilename());
    }

    String key = uploadService.uploadJd(id, file, auth.getName());
    UploadJdResponse data = new UploadJdResponse(id, key, file.getOriginalFilename(), file.getSize());
    return ResponseEntity.ok(ApiResponse.success("JD uploaded successfully", data));
}
```

Change the resumes method:

```java
@PostMapping("/{id}/resumes")
public ResponseEntity<ApiResponse<UploadResumesResponse>> uploadResumes(
        @PathVariable String id,
        @RequestParam("files") MultipartFile[] files,
        Authentication auth) {

    for (MultipartFile file : files) {
        String contentType = file.getContentType();
        if (contentType == null || !contentType.equals("application/pdf")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_ERROR,
                    "Only PDF files are allowed", "Rejected file: " + file.getOriginalFilename());
        }
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_ERROR,
                    "File too large", "Resume max size is 10MB: " + file.getOriginalFilename());
        }
    }

    java.util.List<String> candidateIds = uploadService.uploadResumes(id, files, auth.getName());
    UploadResumesResponse data = new UploadResumesResponse(id, candidateIds.size(), candidateIds);
    return ResponseEntity.ok(ApiResponse.success("Resumes uploaded successfully", data));
}
```

- [ ] **Step 6: Run upload source contract**

Run:

```powershell
node maintenance\tests\api-wrapper-contract.test.mjs
```

Expected: passes silently.

- [ ] **Step 7: Run backend compile/tests**

Run:

```powershell
& 'C:\Users\Muham\.m2\wrapper\dists\apache-maven-3.9.14-bin\1cb7fhup6b5n3bed6kckbrnspv\apache-maven-3.9.14\bin\mvn.cmd' -pl api-service -am test
```

Expected: `BUILD SUCCESS`.

---

### Task 3: Wrap Session, Batch, Auth, and Admin Endpoints

**Files:**
- Create: `api-service/src/main/java/com/resumescreener/api/batch/dto/SessionActionResponse.java`
- Create: `api-service/src/main/java/com/resumescreener/api/batch/dto/ExportResponse.java`
- Modify: `api-service/src/main/java/com/resumescreener/api/session/SessionController.java`
- Modify: `api-service/src/main/java/com/resumescreener/api/batch/BatchController.java`
- Modify: `api-service/src/main/java/com/resumescreener/api/auth/AuthController.java`
- Modify: `api-service/src/main/java/com/resumescreener/api/admin/AdminController.java`
- Modify: `api-service/src/main/java/com/resumescreener/api/batch/BatchTriggerService.java`

- [ ] **Step 1: Extend the source contract test**

Append to `maintenance/tests/api-wrapper-contract.test.mjs`:

```javascript
const sessionController = read('api-service/src/main/java/com/resumescreener/api/session/SessionController.java');
const batchController = read('api-service/src/main/java/com/resumescreener/api/batch/BatchController.java');
const authController = read('api-service/src/main/java/com/resumescreener/api/auth/AuthController.java');
const adminController = read('api-service/src/main/java/com/resumescreener/api/admin/AdminController.java');

for (const [name, source] of [
  ['SessionController', sessionController],
  ['BatchController', batchController],
  ['AuthController', authController],
  ['AdminController', adminController],
]) {
  assert.match(source, /ApiResponse</, `${name} must return ApiResponse wrappers`);
  assert.match(source, /ApiResponse\.success/, `${name} must use success envelopes`);
}
```

- [ ] **Step 2: Run contract and confirm it fails**

Run:

```powershell
node maintenance\tests\api-wrapper-contract.test.mjs
```

Expected: assertion failure on unwrapped controllers.

- [ ] **Step 3: Add action/export DTOs**

Create `api-service/src/main/java/com/resumescreener/api/batch/dto/SessionActionResponse.java`:

```java
package com.resumescreener.api.batch.dto;

import com.resumescreener.common.enums.SessionStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionActionResponse {
    private String sessionId;
    private SessionStatus status;
    private String message;
}
```

Create `api-service/src/main/java/com/resumescreener/api/batch/dto/ExportResponse.java`:

```java
package com.resumescreener.api.batch.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExportResponse {
    private String sessionId;
    private String url;
    private int expiresInMinutes;
}
```

- [ ] **Step 4: Update `BatchTriggerService` to throw typed worker errors**

In `api-service/src/main/java/com/resumescreener/api/batch/BatchTriggerService.java`, add imports:

```java
import com.resumescreener.api.common.ApiErrorCode;
import com.resumescreener.api.common.ApiException;
```

Replace the catch block in `triggerBatch`:

```java
} catch (Exception e) {
    session.setStatus(SessionStatus.FAILED);
    sessionRepository.save(session);
    throw new ApiException(HttpStatus.BAD_GATEWAY, ApiErrorCode.BATCH_WORKER_UNAVAILABLE,
            "Failed to trigger batch worker", e.getMessage());
}
```

- [ ] **Step 5: Wrap `BatchController`**

Change method return types and bodies in `api-service/src/main/java/com/resumescreener/api/batch/BatchController.java`:

```java
@PostMapping("/{id}/start")
public ResponseEntity<ApiResponse<SessionActionResponse>> startBatch(@PathVariable String id, Authentication auth) {
    ScreeningSession session = sessionService.getSession(id, auth.getName());
    batchTriggerService.triggerBatch(id);
    SessionActionResponse data = new SessionActionResponse(id, SessionStatus.RUNNING, "Screening started");
    return ResponseEntity.ok(ApiResponse.success("Screening started", data));
}
```

Wrap progress:

```java
return ResponseEntity.ok(ApiResponse.success("Progress loaded", progress));
```

Wrap candidates page:

```java
return ResponseEntity.ok(ApiResponse.success("Candidates loaded", dtos));
```

Wrap candidate detail:

```java
return ResponseEntity.ok(ApiResponse.success("Candidate loaded", dto));
```

Wrap export:

```java
String url = minioUploadService.getPresignedUrl(session.getReportFileKey());
ExportResponse data = new ExportResponse(id, url, 15);
return ResponseEntity.ok(ApiResponse.success("Export URL generated", data));
```

- [ ] **Step 6: Wrap `SessionController`**

Update `api-service/src/main/java/com/resumescreener/api/session/SessionController.java` to return `ApiResponse<SessionResponse>` for create/get/update, `ApiResponse<Page<SessionResponse>>` for list, and `ApiResponse<SessionActionResponse>` for delete/cancel.

Delete method body:

```java
@DeleteMapping("/{id}")
public ResponseEntity<ApiResponse<SessionActionResponse>> deleteSession(@PathVariable String id, Authentication auth) {
    sessionService.deleteSession(id, auth.getName());
    SessionActionResponse data = new SessionActionResponse(id, SessionStatus.FAILED, "Session deleted");
    return ResponseEntity.ok(ApiResponse.success("Session deleted", data));
}
```

Cancel method body:

```java
@PostMapping("/{id}/cancel")
public ResponseEntity<ApiResponse<SessionActionResponse>> cancelSession(@PathVariable String id, Authentication auth) {
    sessionService.cancelSession(id, auth.getName());
    SessionActionResponse data = new SessionActionResponse(id, SessionStatus.FAILED, "Cancelled by user");
    return ResponseEntity.ok(ApiResponse.success("Screening cancelled", data));
}
```

- [ ] **Step 7: Wrap `AuthController`**

Update `api-service/src/main/java/com/resumescreener/api/auth/AuthController.java`:

```java
@PostMapping("/register")
public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
    return ResponseEntity.ok(ApiResponse.success("Registration successful", authService.register(request)));
}

@PostMapping("/login")
public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
    return ResponseEntity.ok(ApiResponse.success("Login successful", authService.login(request)));
}
```

- [ ] **Step 8: Wrap `AdminController`**

In `api-service/src/main/java/com/resumescreener/api/admin/AdminController.java`, add imports:

```java
import com.resumescreener.api.common.ApiErrorCode;
import com.resumescreener.api.common.ApiException;
import com.resumescreener.api.common.ApiResponse;
import org.springframework.http.HttpStatus;
```

Replace `listUsers` with:

```java
@GetMapping("/users")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<ApiResponse<Page<UserDto>>> listUsers(Pageable pageable) {
    Page<UserDto> data = userRepository.findAll(pageable)
            .map(u -> new UserDto(u.getId(), u.getEmail(), u.getFullName(), u.getRole(), u.getCreatedAt()));
    return ResponseEntity.ok(ApiResponse.success("Users loaded", data));
}
```

Replace `updateRole` with:

```java
@PatchMapping("/users/{id}/role")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<ApiResponse<UserDto>> updateRole(
        @PathVariable String id,
        @RequestBody RoleRequest request,
        org.springframework.security.core.Authentication auth) {
    User caller = userRepository.findByEmail(auth.getName())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND,
                    "Caller not found", "No user exists for email " + auth.getName()));

    if (caller.getId().equals(id)) {
        throw new ApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_ERROR,
                "You cannot change your own role", "Rejected role update for current user " + id);
    }

    User user = userRepository.findById(id)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND,
                    "User not found", "No user exists for id " + id));

    user.setRole(request.getRole());
    userRepository.save(user);

    UserDto data = new UserDto(user.getId(), user.getEmail(), user.getFullName(), user.getRole(), user.getCreatedAt());
    return ResponseEntity.ok(ApiResponse.success("User role updated", data));
}
```

- [ ] **Step 9: Run backend and source contracts**

Run:

```powershell
node maintenance\tests\api-wrapper-contract.test.mjs
& 'C:\Users\Muham\.m2\wrapper\dists\apache-maven-3.9.14-bin\1cb7fhup6b5n3bed6kckbrnspv\apache-maven-3.9.14\bin\mvn.cmd' -pl api-service -am test
```

Expected: both commands pass.

---

### Task 4: Adapt Frontend API Client to Wrapped Responses

**Files:**
- Modify: `ui/src/types/index.ts`
- Modify: `ui/src/api/client.ts`
- Modify: `ui/src/pages/SessionDetail.tsx`
- Modify: `ui/src/pages/NewSession.tsx`

- [ ] **Step 1: Write a frontend source contract**

Append to `maintenance/tests/api-wrapper-contract.test.mjs`:

```javascript
const client = read('ui/src/api/client.ts');
const types = read('ui/src/types/index.ts');

assert.match(types, /export interface ApiResponse<T>/);
assert.match(types, /export interface ApiError/);
assert.match(client, /isApiResponse/);
assert.match(client, /response\.data = response\.data\.data/);
```

- [ ] **Step 2: Run contract and confirm it fails**

Run:

```powershell
node maintenance\tests\api-wrapper-contract.test.mjs
```

Expected: assertion failure because the frontend does not know the wrapper yet.

- [ ] **Step 3: Add frontend API wrapper types**

In `ui/src/types/index.ts`, add near the top:

```typescript
export interface ApiError {
  code: string;
  details: string;
}

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data?: T;
  error?: ApiError;
}

export interface UploadJdResponse {
  sessionId: string;
  jdFileKey: string;
  fileName: string;
  sizeBytes: number;
}

export interface UploadResumesResponse {
  sessionId: string;
  uploadedCount: number;
  candidateIds: string[];
}

export interface SessionActionResponse {
  sessionId: string;
  status: 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED';
  message: string;
}

export interface ExportResponse {
  sessionId: string;
  url: string;
  expiresInMinutes: number;
}
```

- [ ] **Step 4: Add Axios wrapper unwrapping**

In `ui/src/api/client.ts`, import `ApiResponse`, `UploadJdResponse`, `UploadResumesResponse`, `SessionActionResponse`, and `ExportResponse`.

Add below the `api` creation:

```typescript
function isApiResponse(value: unknown): value is ApiResponse<unknown> {
  return (
    typeof value === 'object' &&
    value !== null &&
    'success' in value &&
    'message' in value
  );
}
```

Replace the response interceptor with:

```typescript
api.interceptors.response.use(
  (response) => {
    if (isApiResponse(response.data) && response.data.success) {
      response.data = response.data.data;
    }
    return response;
  },
  (error) => {
    if (error.response?.status === 401) {
      useAuthStore.getState().logout();
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);
```

- [ ] **Step 5: Update API method payload types**

Change upload/start/cancel/export method types in `ui/src/api/client.ts`:

```typescript
uploadJd: (id: string, file: File) => {
  const formData = new FormData();
  formData.append('file', file);
  return api.post<UploadJdResponse>(`/sessions/${id}/jd`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
},
uploadResumes: (id: string, files: File[]) => {
  const formData = new FormData();
  files.forEach((file) => formData.append('files', file));
  return api.post<UploadResumesResponse>(`/sessions/${id}/resumes`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
},
start: (id: string) => api.post<SessionActionResponse>(`/sessions/${id}/start`),
cancel: (id: string) => api.post<SessionActionResponse>(`/sessions/${id}/cancel`),
export: (id: string) =>
  api.get<ExportResponse>(`/sessions/${id}/export`),
```

Because the interceptor replaces `response.data` with the inner payload, current UI code that reads `res.data` continues to receive the typed payload.

- [ ] **Step 6: Verify frontend call sites that read export/upload payloads**

In `ui/src/pages/SessionDetail.tsx`, keep export URL access as:

```typescript
const handleExport = async () => {
  if (!id) return;
  try {
    const res = await sessionsApi.export(id);
    window.open(res.data.url, '_blank');
  } catch (err) {
    console.error('Failed to export:', err);
  }
};
```

In `ui/src/pages/NewSession.tsx`, keep upload sequencing unchanged. The upload methods return typed data now, but this screen only needs successful completion before moving to the next step.

- [ ] **Step 7: Run frontend verification**

Run:

```powershell
node maintenance\tests\api-wrapper-contract.test.mjs
npm run build
```

Expected: source contract passes and Vite build succeeds.

---

### Task 5: Add Pure LLM Score Parser and Validation

**Files:**
- Create: `batch-worker/src/main/java/com/resumescreener/batch/llm/LlmScoreParser.java`
- Create: `batch-worker/src/main/java/com/resumescreener/batch/llm/LlmException.java`
- Create: `batch-worker/src/test/java/com/resumescreener/batch/llm/LlmScoreParserTest.java`

- [ ] **Step 1: Write failing parser tests**

Create `batch-worker/src/test/java/com/resumescreener/batch/llm/LlmScoreParserTest.java`:

```java
package com.resumescreener.batch.llm;

import com.resumescreener.common.dto.LlmScoreDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmScoreParserTest {

    private final LlmScoreParser parser = new LlmScoreParser();

    @Test
    void parsesValidJsonContent() {
        String content = """
                {
                  "technical_skills": 91,
                  "experience": 82,
                  "education": 70,
                  "soft_skills": 76,
                  "culture_fit": 88,
                  "skills": ["Java", "Spring Boot"],
                  "justification": "Strong backend fit.",
                  "strengths": "Solid Java experience.",
                  "gaps": "Limited cloud detail."
                }
                """;

        LlmScoreDto score = parser.parseContent(content);

        assertThat(score.getTechnicalSkills()).isEqualTo(91);
        assertThat(score.getExperience()).isEqualTo(82);
        assertThat(score.getSkills()).containsExactly("Java", "Spring Boot");
    }

    @Test
    void rejectsMissingRequiredScore() {
        String content = """
                {
                  "experience": 82,
                  "education": 70,
                  "soft_skills": 76,
                  "culture_fit": 88,
                  "skills": ["Java"],
                  "justification": "Assessment.",
                  "strengths": "Strength.",
                  "gaps": "Gap."
                }
                """;

        assertThatThrownBy(() -> parser.parseContent(content))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("technical_skills");
    }

    @Test
    void rejectsOutOfRangeScore() {
        String content = """
                {
                  "technical_skills": 101,
                  "experience": 82,
                  "education": 70,
                  "soft_skills": 76,
                  "culture_fit": 88,
                  "skills": ["Java"],
                  "justification": "Assessment.",
                  "strengths": "Strength.",
                  "gaps": "Gap."
                }
                """;

        assertThatThrownBy(() -> parser.parseContent(content))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("technical_skills");
    }

    @Test
    void rejectsContentWithoutJsonObject() {
        assertThatThrownBy(() -> parser.parseContent("I cannot score this resume."))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("No JSON object");
    }

    @Test
    void fillsSafeDefaultsForOptionalNarrativeFields() {
        String content = """
                {
                  "technical_skills": 80,
                  "experience": 75,
                  "education": 60,
                  "soft_skills": 70,
                  "culture_fit": 65
                }
                """;

        LlmScoreDto score = parser.parseContent(content);

        assertThat(score.getSkills()).isEmpty();
        assertThat(score.getJustification()).isEqualTo("No justification provided by LLM.");
        assertThat(score.getStrengths()).isEqualTo("No strengths provided by LLM.");
        assertThat(score.getGaps()).isEqualTo("No gaps provided by LLM.");
    }
}
```

- [ ] **Step 2: Run parser tests and confirm they fail**

Run:

```powershell
& 'C:\Users\Muham\.m2\wrapper\dists\apache-maven-3.9.14-bin\1cb7fhup6b5n3bed6kckbrnspv\apache-maven-3.9.14\bin\mvn.cmd' -pl batch-worker -am -Dtest=LlmScoreParserTest test
```

Expected: compilation fails because `LlmScoreParser` and `LlmException` do not exist.

- [ ] **Step 3: Implement `LlmException`**

Create `batch-worker/src/main/java/com/resumescreener/batch/llm/LlmException.java`:

```java
package com.resumescreener.batch.llm;

public class LlmException extends RuntimeException {
    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Step 4: Implement `LlmScoreParser`**

Create `batch-worker/src/main/java/com/resumescreener/batch/llm/LlmScoreParser.java`:

```java
package com.resumescreener.batch.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumescreener.common.dto.LlmScoreDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

@Component
public class LlmScoreParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public LlmScoreDto parseContent(String content) {
        if (content == null || content.isBlank()) {
            throw new LlmException("LLM response content is empty");
        }

        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start == -1 || end == -1 || end <= start) {
            throw new LlmException("No JSON object found in LLM response");
        }

        String json = content.substring(start, end + 1);
        LlmScoreDto score;
        try {
            score = objectMapper.readValue(json, LlmScoreDto.class);
        } catch (Exception e) {
            throw new LlmException("Failed to parse LLM JSON: " + e.getMessage(), e);
        }

        validateRange("technical_skills", score.getTechnicalSkills());
        validateRange("experience", score.getExperience());
        validateRange("education", score.getEducation());
        validateRange("soft_skills", score.getSoftSkills());
        validateRange("culture_fit", score.getCultureFit());

        if (score.getSkills() == null) {
            score.setSkills(new ArrayList<>());
        }
        if (isBlank(score.getJustification())) {
            score.setJustification("No justification provided by LLM.");
        }
        if (isBlank(score.getStrengths())) {
            score.setStrengths("No strengths provided by LLM.");
        }
        if (isBlank(score.getGaps())) {
            score.setGaps("No gaps provided by LLM.");
        }

        return score;
    }

    private void validateRange(String field, Integer value) {
        if (value == null) {
            throw new LlmException("Missing required LLM score field: " + field);
        }
        if (value < 0 || value > 100) {
            throw new LlmException("LLM score field out of range 0-100: " + field + "=" + value);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
```

- [ ] **Step 5: Run parser tests and confirm they pass**

Run:

```powershell
& 'C:\Users\Muham\.m2\wrapper\dists\apache-maven-3.9.14-bin\1cb7fhup6b5n3bed6kckbrnspv\apache-maven-3.9.14\bin\mvn.cmd' -pl batch-worker -am -Dtest=LlmScoreParserTest test
```

Expected: `BUILD SUCCESS`.

---

### Task 6: Rename `OpenAiClient` to `LlmClient` and Use Parser

**Files:**
- Create: `batch-worker/src/main/java/com/resumescreener/batch/llm/LlmClient.java`
- Delete after replacement: `batch-worker/src/main/java/com/resumescreener/batch/llm/OpenAiClient.java`
- Modify: `batch-worker/src/main/java/com/resumescreener/batch/web/CandidateProcessingService.java`

- [ ] **Step 1: Write source contract for provider-neutral naming**

Create `maintenance/tests/llm-client-contract.test.mjs`:

```javascript
import { existsSync, readFileSync } from 'node:fs';
import assert from 'node:assert/strict';
import path from 'node:path';

const root = process.cwd();
const llmClientPath = path.join(root, 'batch-worker/src/main/java/com/resumescreener/batch/llm/LlmClient.java');
const openAiPath = path.join(root, 'batch-worker/src/main/java/com/resumescreener/batch/llm/OpenAiClient.java');
const processingPath = path.join(root, 'batch-worker/src/main/java/com/resumescreener/batch/web/CandidateProcessingService.java');

assert.equal(existsSync(llmClientPath), true, 'LlmClient.java must exist');
assert.equal(existsSync(openAiPath), false, 'OpenAiClient.java should be replaced');

const llmClient = readFileSync(llmClientPath, 'utf8');
const processing = readFileSync(processingPath, 'utf8');

assert.match(llmClient, /class LlmClient/);
assert.match(llmClient, /LlmScoreParser/);
assert.match(llmClient, /validateConfig/);
assert.match(processing, /private final LlmClient llmClient;/);
assert.doesNotMatch(processing, /OpenAiClient/);
```

- [ ] **Step 2: Run source contract and confirm it fails**

Run:

```powershell
node maintenance\tests\llm-client-contract.test.mjs
```

Expected: assertion failure because the client is still named `OpenAiClient`.

- [ ] **Step 3: Implement `LlmClient`**

Create `batch-worker/src/main/java/com/resumescreener/batch/llm/LlmClient.java`:

```java
package com.resumescreener.batch.llm;

import com.resumescreener.common.dto.LlmScoreDto;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LlmClient {

    @Value("${llm.api-key}")
    private String apiKey;

    @Value("${llm.model}")
    private String model;

    @Value("${llm.base-url}")
    private String baseUrl;

    private final RestClient restClient;
    private final LlmScoreParser parser;

    @Retry(name = "llm")
    @RateLimiter(name = "llm")
    public LlmScoreDto score(String jdText, String resumeText) {
        validateConfig();

        Map<String, Object> body = Map.of(
                "model", model.trim(),
                "messages", List.of(
                        Map.of("role", "system", "content", getSystemPrompt()),
                        Map.of("role", "user", "content", buildUserPrompt(jdText, resumeText))
                ),
                "temperature", 0.2
        );

        LlmChatCompletionResponse response;
        try {
            response = restClient.post()
                    .uri(normalizedBaseUrl() + "/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey.trim())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(LlmChatCompletionResponse.class);
        } catch (HttpStatusCodeException e) {
            throw new LlmException("LLM provider error: HTTP " + e.getStatusCode().value(), e);
        } catch (Exception e) {
            throw new LlmException("LLM provider request failed: " + e.getMessage(), e);
        }

        return parser.parseContent(extractContent(response));
    }

    private void validateConfig() {
        if (isBlank(apiKey)) {
            throw new LlmException("LLM API key is not configured");
        }
        if (isBlank(model)) {
            throw new LlmException("LLM model is not configured");
        }
        if (isBlank(baseUrl)) {
            throw new LlmException("LLM base URL is not configured");
        }
    }

    private String normalizedBaseUrl() {
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith("/chat/completions")) {
            normalized = normalized.substring(0, normalized.length() - "/chat/completions".length());
        }
        return normalized;
    }

    private String extractContent(LlmChatCompletionResponse response) {
        if (response == null || response.choices == null || response.choices.isEmpty()) {
            throw new LlmException("No choices in LLM response");
        }
        Choice choice = response.choices.get(0);
        if (choice == null || choice.message == null || isBlank(choice.message.content)) {
            throw new LlmException("No message content in LLM response");
        }
        return choice.message.content;
    }

    private String getSystemPrompt() {
        return "You are an expert HR analyst. Evaluate the resume against the job description.\n" +
                "Return ONLY valid JSON matching the schema below. No prose outside JSON.\n\n" +
                "Schema:\n" +
                "{\n" +
                "  \"technical_skills\": <0-100>,\n" +
                "  \"experience\": <0-100>,\n" +
                "  \"education\": <0-100>,\n" +
                "  \"soft_skills\": <0-100>,\n" +
                "  \"culture_fit\": <0-100>,\n" +
                "  \"skills\": [\"skill1\", \"skill2\"],\n" +
                "  \"justification\": \"<2-3 sentence overall assessment>\",\n" +
                "  \"strengths\": \"<key strengths>\",\n" +
                "  \"gaps\": \"<key gaps>\"\n" +
                "}";
    }

    private String buildUserPrompt(String jdText, String resumeText) {
        return "JOB DESCRIPTION:\n" + truncate(nullToEmpty(jdText), 4000) +
                "\n\nRESUME:\n" + truncate(nullToEmpty(resumeText), 14000);
    }

    private String truncate(String text, int max) {
        return text.length() > max ? text.substring(0, max) : text;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static class LlmChatCompletionResponse {
        public List<Choice> choices;
    }

    private static class Choice {
        public Message message;
    }

    private static class Message {
        public String content;
    }
}
```

- [ ] **Step 4: Update candidate processing injection**

In `batch-worker/src/main/java/com/resumescreener/batch/web/CandidateProcessingService.java`:

Replace imports:

```java
import com.resumescreener.batch.llm.LlmClient;
```

Replace field:

```java
private final LlmClient llmClient;
```

Replace scoring call:

```java
LlmScoreDto score = llmClient.score(jdText, text);
```

- [ ] **Step 5: Remove old client**

Delete `batch-worker/src/main/java/com/resumescreener/batch/llm/OpenAiClient.java`.

- [ ] **Step 6: Run contracts and backend compile**

Run:

```powershell
node maintenance\tests\llm-client-contract.test.mjs
& 'C:\Users\Muham\.m2\wrapper\dists\apache-maven-3.9.14-bin\1cb7fhup6b5n3bed6kckbrnspv\apache-maven-3.9.14\bin\mvn.cmd' -pl batch-worker -am test
```

Expected: both pass.

---

### Task 7: Prove Invalid LLM Output Does Not Save Scores

**Files:**
- Create: `batch-worker/src/test/java/com/resumescreener/batch/web/CandidateProcessingServiceTest.java`

- [ ] **Step 1: Write failing candidate processing test**

Create `batch-worker/src/test/java/com/resumescreener/batch/web/CandidateProcessingServiceTest.java`:

```java
package com.resumescreener.batch.web;

import com.resumescreener.batch.llm.LlmClient;
import com.resumescreener.batch.llm.LlmException;
import com.resumescreener.batch.pdf.PdfTextExtractor;
import com.resumescreener.batch.storage.MinioStorageService;
import com.resumescreener.common.entity.BatchJob;
import com.resumescreener.common.entity.Candidate;
import com.resumescreener.common.entity.ScoringWeights;
import com.resumescreener.common.repository.BatchJobRepository;
import com.resumescreener.common.repository.CandidateRepository;
import com.resumescreener.common.repository.CandidateScoreRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CandidateProcessingServiceTest {

    @Test
    void invalidLlmResponseDoesNotSaveCandidateScore() throws Exception {
        CandidateRepository candidateRepository = mock(CandidateRepository.class);
        CandidateScoreRepository scoreRepository = mock(CandidateScoreRepository.class);
        BatchJobRepository batchJobRepository = mock(BatchJobRepository.class);
        MinioStorageService storageService = mock(MinioStorageService.class);
        PdfTextExtractor pdfTextExtractor = mock(PdfTextExtractor.class);
        LlmClient llmClient = mock(LlmClient.class);

        CandidateProcessingService service = new CandidateProcessingService(
                candidateRepository,
                scoreRepository,
                batchJobRepository,
                storageService,
                pdfTextExtractor,
                llmClient
        );

        Candidate candidate = Candidate.builder()
                .id("candidate-123")
                .email("candidate@example.com")
                .resumeFileKey("resumes/session-123/resume.pdf")
                .build();

        ScoringWeights weights = ScoringWeights.builder()
                .technicalSkills(30.0)
                .experience(25.0)
                .education(15.0)
                .softSkills(20.0)
                .cultureFit(10.0)
                .build();

        BatchJob job = BatchJob.builder().id("job-123").processed(0).failed(0).build();

        when(storageService.downloadBytes("resumes/session-123/resume.pdf")).thenReturn(new byte[]{1, 2, 3});
        when(pdfTextExtractor.extract(new byte[]{1, 2, 3})).thenReturn("Resume text");
        when(llmClient.score("JD text", "Resume text")).thenThrow(new LlmException("Missing required LLM score field: technical_skills"));
        when(batchJobRepository.findById("job-123")).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> service.processSingleCandidate(candidate, "JD text", weights, job))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("technical_skills");

        verify(scoreRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
```

- [ ] **Step 2: Run the test**

Run:

```powershell
& 'C:\Users\Muham\.m2\wrapper\dists\apache-maven-3.9.14-bin\1cb7fhup6b5n3bed6kckbrnspv\apache-maven-3.9.14\bin\mvn.cmd' -pl batch-worker -am -Dtest=CandidateProcessingServiceTest test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Run batch-worker tests**

Run:

```powershell
& 'C:\Users\Muham\.m2\wrapper\dists\apache-maven-3.9.14-bin\1cb7fhup6b5n3bed6kckbrnspv\apache-maven-3.9.14\bin\mvn.cmd' -pl batch-worker -am test
```

Expected: `BUILD SUCCESS`.

---

### Task 8: Final Cross-Project Verification

**Files:**
- No production file changes expected.

- [ ] **Step 1: Run Node source contracts**

Run:

```powershell
node maintenance\tests\candidate-results-contract.test.mjs
node maintenance\tests\api-wrapper-contract.test.mjs
node maintenance\tests\llm-client-contract.test.mjs
```

Expected: all commands exit `0`.

- [ ] **Step 2: Run frontend build**

Run:

```powershell
npm run build
```

Working directory: `D:\0 Spring Boot\Projects\New Project\ui`

Expected: TypeScript and Vite build succeed. Existing Vite chunk-size warnings are acceptable if there are no errors.

- [ ] **Step 3: Run full Maven reactor tests**

Run:

```powershell
& 'C:\Users\Muham\.m2\wrapper\dists\apache-maven-3.9.14-bin\1cb7fhup6b5n3bed6kckbrnspv\apache-maven-3.9.14\bin\mvn.cmd' test
```

Expected: `BUILD SUCCESS` for `common`, `api-service`, and `batch-worker`.

- [ ] **Step 4: Runtime verification when Docker engine is available**

Run:

```powershell
docker compose up -d --build
docker compose ps
```

Expected: `postgres`, `minio`, `api`, `batch-worker`, and `ui` are running. If Docker returns `failed to connect to the docker API at npipe`, stop here and report Docker Desktop engine startup as the blocker.

- [ ] **Step 5: Manual smoke test**

Use the UI to:

1. Register or login.
2. Create a session.
3. Upload a JD PDF.
4. Upload resume PDFs.
5. Start screening.
6. Confirm progress changes.
7. Confirm candidate results show non-static AI dimension scores.
8. Confirm a failed MinIO or LLM call returns `{ success: false, message, error: { code, details } }`.

Expected: upload and scoring use wrapped backend responses, and LLM output is validated before scores are saved.
