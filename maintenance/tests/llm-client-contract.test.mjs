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
