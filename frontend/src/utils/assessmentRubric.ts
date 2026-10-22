import type { CriterionRequest } from '../types/domain'

export interface AssessmentCriterion {
  name: string
  score: number
  description?: string
  children: AssessmentCriterion[]
}

export const criterionScore = (criterion: AssessmentCriterion): number =>
  criterion.children.length
    ? Math.round(criterion.children.reduce((sum, child) => sum + child.score, 0) * 10) / 10
    : criterion.score

export const rubricTotal = (criteria: AssessmentCriterion[]): number =>
  Math.round(criteria.reduce((sum, criterion) => sum + criterionScore(criterion), 0) * 10) / 10

export function validRubric(criteria: AssessmentCriterion[]): boolean {
  return (
    criteria.length > 0 &&
    rubricTotal(criteria) === 100 &&
    criteria.every(
      (criterion) =>
        criterion.name.trim().length > 0 &&
        criterion.name.length <= 220 &&
        (criterion.children.length ? criterion.children : [criterion]).every(
          (leaf) =>
            leaf.name.trim().length > 0 &&
            leaf.name.length <= 220 &&
            Number.isFinite(leaf.score) &&
            leaf.score >= 0.1 &&
            leaf.score <= 100 &&
            Math.abs(leaf.score * 10 - Math.round(leaf.score * 10)) < 0.000001,
        ),
    )
  )
}

export function defaultAssessmentCriteria(): AssessmentCriterion[] {
  const leaf = (name: string, score: number): AssessmentCriterion => ({ name, score, children: [] })
  return [
    leaf('Tính thực tiễn và hiểu biết vấn đề', 10),
    {
      name: 'Phương pháp, thiết kế và hoàn thiện sản phẩm',
      score: 0,
      children: [
        leaf('Phân tích nghiệp vụ, đặc tả yêu cầu và thiết kế', 10),
        leaf('Hoàn thiện sản phẩm và khối lượng công việc', 10),
        leaf('Làm chủ và kiểm soát LLM/Agentic AI', 5),
        leaf('Chất lượng mã nguồn và quản lý chất lượng', 10),
        leaf('Kiểm thử phần mềm', 5),
        leaf('Triển khai CI/CD và vận hành', 5),
        leaf('Thử nghiệm với người dùng thực tế', 5),
      ],
    },
    leaf('Chất lượng bài thuyết trình', 10),
    leaf('Kiến thức, viết luận văn và trình bày', 10),
    leaf('Chất lượng trả lời câu hỏi hội đồng', 15),
    leaf('Các câu hỏi bắt buộc', 5),
  ]
}

// The API scores individual criteria on a 10-point scale. Persist only leaves,
// with readable codes/group headings, so a group total is never counted twice.
export function toApiCriteria(criteria: AssessmentCriterion[]): CriterionRequest[] {
  const result: CriterionRequest[] = []
  criteria.forEach((group, index) => {
    const code = `TC${index + 1}`
    const leaves = group.children.length ? group.children : [group]
    leaves.forEach((leaf, childIndex) => {
      const leafCode = group.children.length ? `${code}.${childIndex + 1}` : code
      result.push({
        name: `[${leafCode}] ${leaf.name.trim()}`,
        description: group.children.length
          ? `Nhóm [${code}]: ${group.name.trim()}\n${leaf.description?.trim() || leaf.name.trim()}`
          : leaf.description?.trim() || leaf.name.trim(),
        maxScore: Math.round(leaf.score * 10) / 100,
        sortOrder: result.length + 1,
      })
    })
  })
  return result
}

export function fromApiCriteria(criteria: CriterionRequest[]): AssessmentCriterion[] {
  const groups: AssessmentCriterion[] = []
  const parents = new Map<string, AssessmentCriterion>()
  const ordered = [...criteria].sort((a, b) => a.sortOrder - b.sortOrder)
  ordered.forEach((criterion) => {
    const code = criterion.name.match(/^\[(TC\d+)(\.\d+)?\] (.+)$/)
    const heading = criterion.description.match(/^Nhóm \[(TC\d+)\]: (.+)\n/)
    const leaf: AssessmentCriterion = {
      name: code?.[3] || criterion.name,
      score: Math.round(Number(criterion.maxScore) * 100) / 10,
      description: heading ? criterion.description.slice(heading[0].length) : criterion.description,
      children: [],
    }
    if (code?.[2] && heading && code[1] === heading[1]) {
      let parent = parents.get(code[1])
      if (!parent) {
        parent = { name: heading[2], score: 0, children: [] }
        parents.set(code[1], parent)
        groups.push(parent)
      }
      parent.children.push(leaf)
    } else {
      groups.push(leaf)
    }
  })
  return groups
}
