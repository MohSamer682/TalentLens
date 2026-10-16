import { readFileSync } from 'node:fs';
import assert from 'node:assert/strict';
import path from 'node:path';

const root = process.cwd();
const read = (file) => readFileSync(path.join(root, file), 'utf8');

// --- Upload endpoints return typed payloads, not raw strings or ints ---

const uploadController = read('api-service/src/main/java/com/resumescreener/api/upload/UploadController.java');

assert.match(uploadController, /ResponseEntity<ApiResponse<UploadJdResponse>>/);
assert.match(uploadController, /ApiResponse\.success\("JD uploaded successfully"/);
assert.match(uploadController, /ResponseEntity<ApiResponse<UploadResumesResponse>>/);
assert.match(uploadController, /ApiResponse\.success\("Resumes uploaded successfully"/);
assert.doesNotMatch(uploadController, /ResponseEntity<String>/, 'JD upload must not return a raw string');
assert.doesNotMatch(uploadController, /ResponseEntity<Object>/, 'Resume upload must not return a raw object');

const uploadService = read('api-service/src/main/java/com/resumescreener/api/upload/MinioUploadService.java');
assert.match(uploadService, /public List<String> uploadResumes\(/, 'uploadResumes must return candidate IDs');

// --- Every controller speaks the envelope; none returns an empty body ---

const controllers = {
  SessionController: 'api-service/src/main/java/com/resumescreener/api/session/SessionController.java',
  BatchController: 'api-service/src/main/java/com/resumescreener/api/batch/BatchController.java',
  AuthController: 'api-service/src/main/java/com/resumescreener/api/auth/AuthController.java',
  AdminController: 'api-service/src/main/java/com/resumescreener/api/admin/AdminController.java',
  UploadController: 'api-service/src/main/java/com/resumescreener/api/upload/UploadController.java',
};

for (const [name, file] of Object.entries(controllers)) {
  const source = read(file);
  assert.match(source, /ApiResponse</, `${name} must return ApiResponse wrappers`);
  assert.match(source, /ApiResponse\.success/, `${name} must use success envelopes`);
  assert.doesNotMatch(source, /ResponseEntity<Void>/, `${name} must not return an empty body`);
  assert.doesNotMatch(source, /noContent\(\)/, `${name} must not return 204 with no payload`);
  assert.doesNotMatch(source, /ok\(\)\.build\(\)/, `${name} must not return an empty 200`);
  assert.doesNotMatch(source, /badRequest\(\)\.build\(\)/, `${name} must not return an empty 400`);
}

// --- Errors are envelopes too, and HTML error pages are switched off ---

const handler = read('api-service/src/main/java/com/resumescreener/api/config/GlobalExceptionHandler.java');
assert.match(handler, /ResponseEntity<ApiResponse<Void>>/);
assert.match(handler, /MaxUploadSizeExceededException/, 'oversized uploads must not reach the error page');
assert.match(handler, /NoResourceFoundException/, 'unknown paths must resolve to a JSON 404');
assert.doesNotMatch(handler, /Map<String, String>/, 'errors must not be bare maps');

const applicationYml = read('api-service/src/main/resources/application.yml');
assert.match(applicationYml, /whitelabel:\s*\n\s*enabled:\s*false/, 'whitelabel HTML page must be disabled');

const errorController = read('api-service/src/main/java/com/resumescreener/api/config/JsonErrorController.java');
assert.match(errorController, /implements ErrorController/);
assert.match(errorController, /ApiResponse\.failure/);

const securityConfig = read('api-service/src/main/java/com/resumescreener/api/config/SecurityConfig.java');
assert.match(securityConfig, /ApiResponse\.failure/, 'security rejections must use the envelope');

// --- Frontend knows the envelope and unwraps it once ---

const client = read('ui/src/api/client.ts');
const types = read('ui/src/types/index.ts');

assert.match(types, /export interface ApiResponse<T>/);
assert.match(types, /export interface ApiError/);
assert.match(types, /export interface UploadJdResponse/);
assert.match(types, /export interface UploadResumesResponse/);
assert.match(types, /export interface SessionActionResponse/);
assert.match(types, /export interface ExportResponse/);
assert.match(types, /export interface ResumeDownloadResponse/);

assert.match(client, /isApiResponse/);
assert.match(client, /response\.data = response\.data\.data/);
assert.match(client, /class ApiRequestError/);
assert.match(client, /export function apiErrorMessage/);
assert.match(client, /candidateResume:/, 'client must expose the resume download endpoint');

// No page may reach back through the old axios error shape
for (const file of [
  'ui/src/pages/Login.tsx',
  'ui/src/pages/Register.tsx',
  'ui/src/pages/NewSession.tsx',
  'ui/src/pages/SessionDetail.tsx',
  'ui/src/pages/CandidateDetail.tsx',
  'ui/src/pages/AdminUsers.tsx',
  'ui/src/components/SessionCard.tsx',
]) {
  assert.doesNotMatch(
    read(file),
    /response\?\.data\?\.message/,
    `${file} must read errors via apiErrorMessage, not the raw axios shape`
  );
}

console.log('api-wrapper-contract: OK');
