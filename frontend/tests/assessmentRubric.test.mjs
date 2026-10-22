import assert from 'node:assert/strict'
import test from 'node:test'
import {
  criterionScore,
  defaultAssessmentCriteria,
  fromApiCriteria,
  rubricTotal,
  toApiCriteria,
  validRubric,
} from '../src/utils/assessmentRubric.ts'

test('the reference template totals 100 without counting parent scores twice', () => {
  const criteria = defaultAssessmentCriteria()
  assert.equal(criterionScore(criteria[1]), 50)
  assert.equal(rubricTotal(criteria), 100)
  assert.equal(validRubric(criteria), true)
  const payload = toApiCriteria(criteria)
  assert.equal(payload.length, 12)
  assert.equal(
    payload.reduce((total, criterion) => total + Math.round(criterion.maxScore * 100), 0),
    1000,
  )
  assert.ok(!payload.some((criterion) => criterion.name.startsWith('[TC2]')))
})

test('saved criteria retain their hierarchy, descriptions and scores on reload', () => {
  const criteria = defaultAssessmentCriteria()
  criteria[1].children[0].description = 'Mô tả chi tiết\nDòng thứ hai'
  const payload = toApiCriteria(criteria)
  const restored = fromApiCriteria(payload)
  assert.equal(restored.length, 6)
  assert.equal(restored[1].children.length, 7)
  assert.equal(restored[1].children[0].description, 'Mô tả chi tiết\nDòng thứ hai')
  assert.deepEqual(toApiCriteria(restored), payload)
})

test('existing flat rubrics remain usable and preserve decimal scores', () => {
  const restored = fromApiCriteria([
    { name: 'Báo cáo', description: 'Tài liệu', maxScore: 6.25, sortOrder: 2 },
    { name: 'Sản phẩm', description: 'Kết quả', maxScore: 3.75, sortOrder: 1 },
  ])
  assert.equal(restored[0].name, 'Sản phẩm')
  assert.equal(restored[0].score, 37.5)
  assert.equal(rubricTotal(restored), 100)
  assert.equal(validRubric(restored), true)
  assert.deepEqual(
    toApiCriteria(restored).map((criterion) => criterion.maxScore),
    [3.75, 6.25],
  )
})

test('incomplete, non-finite, non-positive, and over-precision scores cannot be saved', () => {
  for (const score of [0, -1, NaN, Infinity, 10.01]) {
    const criteria = defaultAssessmentCriteria()
    criteria[0].score = score
    assert.equal(validRubric(criteria), false)
  }
  const criteria = defaultAssessmentCriteria()
  criteria[1].children[0].name = '   '
  assert.equal(validRubric(criteria), false)
  assert.equal(validRubric([]), false)
})
