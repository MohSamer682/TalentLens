import { readFileSync } from 'node:fs';
import assert from 'node:assert/strict';
import path from 'node:path';

const root = process.cwd();
const read = (file) => readFileSync(path.join(root, file), 'utf8');

const dto = read('common/src/main/java/com/resumescreener/common/session/CandidateResultDto.java');
const controller = read('api-service/src/main/java/com/resumescreener/api/batch/ScreeningResultsService.java');

const dimensions = [
  ['technicalSkills', 'TechnicalSkills'],
  ['experience', 'Experience'],
  ['education', 'Education'],
  ['softSkills', 'SoftSkills'],
  ['cultureFit', 'CultureFit'],
];

for (const [field, getter] of dimensions) {
  assert.match(
    dto,
    new RegExp(`private double ${field};`),
    `CandidateResultDto must expose ${field} from AI scoring`
  );
  assert.match(
    controller,
    new RegExp(`valueOrZero\\(score\\.get${getter}\\(\\)\\)`),
    `ScreeningResultsService must map score.${field} into candidate results`
  );
}
