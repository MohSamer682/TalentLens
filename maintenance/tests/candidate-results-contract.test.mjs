import { readFileSync } from 'node:fs';
import assert from 'node:assert/strict';
import path from 'node:path';

const root = process.cwd();
const read = (file) => readFileSync(path.join(root, file), 'utf8');

const dto = read('common/src/main/java/com/resumescreener/common/dto/CandidateResultDto.java');
const controller = read('api-service/src/main/java/com/resumescreener/api/batch/BatchController.java');
const sessionDetail = read('ui/src/pages/SessionDetail.tsx');

const dimensions = [
  ['technicalSkills', 'TechnicalSkills', 'Technical Skills'],
  ['experience', 'Experience', 'Experience'],
  ['education', 'Education', 'Education'],
  ['softSkills', 'SoftSkills', 'Soft Skills'],
  ['cultureFit', 'CultureFit', 'Culture Fit'],
];

for (const [field, getter, label] of dimensions) {
  assert.match(
    dto,
    new RegExp(`private double ${field};`),
    `CandidateResultDto must expose ${field} from AI scoring`
  );
  assert.match(
    controller,
    new RegExp(`valueOrZero\\(score\\.get${getter}\\(\\)\\)`),
    `BatchController must map score.${field} into candidate results`
  );
  // The results table abbreviates headers, so match the accessible full label
  assert.match(
    sessionDetail,
    new RegExp(`full: '${label}'`),
    `SessionDetail table must render the ${label} AI score column`
  );
  assert.match(
    sessionDetail,
    new RegExp(`formatScore\\(c\\.${field}\\)`),
    `SessionDetail table must render each candidate's ${field} value`
  );
}
