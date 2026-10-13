import type {
  EvaluationForm,
  EvaluationFormCreateRequest,
  EvaluationFormPage,
  EvaluationFormPageParams,
  EvaluationFormPublishRequest,
  EvaluationFormUpdateRequest,
  EvaluationFormVersionDetail,
  EvaluationFormVersionSummary,
  EvaluationItem,
  EvaluationScoreRule
} from '@/api/evaluation-form';

// TODO replace real api：评价表单真实接口联调完成后删除本文件和 API 中的 Mock 分支。

/** 模拟 Axios 业务错误边界，保持与真实接口统一的错误读取方式。 */
class EvaluationFormContractMockError extends Error {
  readonly response: { data: { code: number; message: string; data: null } };

  constructor(code: number, message: string) {
    super(message);
    this.name = 'EvaluationFormContractMockError';
    this.response = { data: { code, message, data: null } };
  }
}

const INITIAL_FORMS: EvaluationForm[] = [
  {
    evaluationFormId: 1,
    formCode: 'FORM-COMPOSITE-001',
    formName: '培训综合评价表',
    formType: 'COMPOSITE',
    currentVersionNo: 0,
    status: 1,
    description: '汇总培训课程、考试和线下成绩。',
    version: 0,
    createTime: '2026-07-31T09:00:00'
  },
  {
    evaluationFormId: 2,
    formCode: 'FORM-INSPECTION-001',
    formName: '专家现场检查表',
    formType: 'EXPERT_INSPECTION',
    currentVersionNo: 0,
    status: 1,
    description: '用于专家现场检查和评价。',
    version: 0,
    createTime: '2026-07-31T09:10:00'
  }
];

let forms: EvaluationForm[] = [];
let versions: EvaluationFormVersionDetail[] = [];
let nextFormId = 100;
let nextVersionId = 1000;
let nextItemId = 10000;
let nextRuleId = 20000;

/** 恢复稳定种子数据，供页面和自动化测试重复使用。 */
export function resetEvaluationFormMockState(): void {
  forms = structuredClone(INITIAL_FORMS);
  versions = [];
  nextFormId = 100;
  nextVersionId = 1000;
  nextItemId = 10000;
  nextRuleId = 20000;
}

function findForm(evaluationFormId: number): EvaluationForm {
  const form = forms.find((item) => item.evaluationFormId === evaluationFormId);
  if (!form) throw new EvaluationFormContractMockError(8501, '评价表单不存在。');
  return form;
}

function findVersion(formVersionId: number): EvaluationFormVersionDetail {
  const version = versions.find((item) => item.formVersionId === formVersionId);
  if (!version) throw new EvaluationFormContractMockError(8503, '评价表单版本不存在。');
  return version;
}

/** 按 OpenAPI query 字段筛选并分页查询表单主体。 */
export async function mockGetEvaluationFormPage(params: EvaluationFormPageParams): Promise<EvaluationFormPage> {
  const code = params['query.formCode']?.trim().toLocaleLowerCase();
  const name = params['query.formName']?.trim().toLocaleLowerCase();
  const filtered = forms.filter(
    (form) =>
      (!code || form.formCode.toLocaleLowerCase() === code) &&
      (!name || form.formName.toLocaleLowerCase().includes(name)) &&
      (!params['query.formType'] || form.formType === params['query.formType']) &&
      (params['query.status'] === undefined || form.status === params['query.status'])
  );
  const start = Math.max(0, params.pageNum - 1) * params.pageSize;
  return {
    list: structuredClone(filtered.slice(start, start + params.pageSize)),
    total: filtered.length
  };
}

/** 查询评价表单主体详情，不展开版本内容。 */
export async function mockGetEvaluationFormDetail(evaluationFormId: number): Promise<EvaluationForm> {
  return structuredClone(findForm(evaluationFormId));
}

/** 创建尚未发布版本的评价表单主体。 */
export async function mockCreateEvaluationForm(data: EvaluationFormCreateRequest): Promise<number> {
  if (
    forms.some(
      (form) => form.formCode === data.formCode.trim()
    )
  ) {
    throw new EvaluationFormContractMockError(8502, '同机构下表单编码已存在。');
  }
  const evaluationFormId = nextFormId++;
  forms.push({
    evaluationFormId,
    formCode: data.formCode.trim(),
    formName: data.formName.trim(),
    formType: data.formType,
    currentVersionNo: 0,
    status: data.status,
    description: data.description,
    version: 0,
    createTime: new Date().toISOString()
  });
  return evaluationFormId;
}

/** 按表单主体乐观锁版本更新基础信息。 */
export async function mockUpdateEvaluationForm(
  evaluationFormId: number,
  data: EvaluationFormUpdateRequest
): Promise<void> {
  const form = findForm(evaluationFormId);
  if (form.version !== data.version) {
    throw new EvaluationFormContractMockError(8504, '评价表单版本冲突。');
  }
  form.formName = data.formName.trim();
  form.formType = data.formType;
  form.status = data.status;
  form.description = data.description;
  form.version += 1;
}

/** 仅允许删除没有任何发布版本的表单主体。 */
export async function mockDeleteEvaluationForm(evaluationFormId: number): Promise<void> {
  findForm(evaluationFormId);
  if (versions.some((version) => version.evaluationFormId === evaluationFormId)) {
    throw new EvaluationFormContractMockError(8507, '评价表单已有发布版本，不可删除。');
  }
  forms = forms.filter((form) => form.evaluationFormId !== evaluationFormId);
}

/** 发布不可变版本，并通过 evaluationItemCode 回填评分规则的真实项目 ID。 */
export async function mockPublishEvaluationForm(
  evaluationFormId: number,
  data: EvaluationFormPublishRequest
): Promise<EvaluationFormVersionDetail> {
  const form = findForm(evaluationFormId);
  if (form.version !== data.version) {
    throw new EvaluationFormContractMockError(8504, '评价表单版本冲突。');
  }
  if (data.items.length === 0) {
    throw new EvaluationFormContractMockError(8505, '评价项目不能为空。');
  }
  const itemCodes = new Set(data.items.map((item) => item.itemCode));
  const missingCode = (data.scoreRules ?? []).find((rule) => !itemCodes.has(rule.evaluationItemCode));
  if (missingCode) {
    throw new EvaluationFormContractMockError(8506, '评分规则引用的评价项目编码不存在。');
  }

  const formVersionId = nextVersionId++;
  const items: EvaluationItem[] = data.items.map((item) => ({
    ...structuredClone(item),
    evaluationItemId: nextItemId++,
    formVersionId,
    maxScore: item.maxScore ?? null,
    weight: item.weight ?? null
  }));
  const itemIdByCode = new Map(items.map((item) => [item.itemCode, item.evaluationItemId]));
  const scoreRules: EvaluationScoreRule[] = (data.scoreRules ?? []).map((rule) => {
    const { evaluationItemCode, ...ruleFields } = rule;
    return {
      ...structuredClone(ruleFields),
      scoreRuleId: nextRuleId++,
      formVersionId,
      evaluationItemId: itemIdByCode.get(evaluationItemCode)!,
      scoreChange: rule.scoreChange ?? null,
      weight: rule.weight ?? null
    };
  });
  const publishedTime = new Date().toISOString();
  const detail: EvaluationFormVersionDetail = {
    formVersionId,
    evaluationFormId,
    versionNo: form.currentVersionNo + 1,
    totalScore: data.totalScore,
    publishedStatus: 'PUBLISHED',
    publishedBy: 1,
    publishedTime,
    versionNote: data.versionNote ?? null,
    items,
    scoreRules
  };

  versions.push(structuredClone(detail));
  form.currentVersionNo = detail.versionNo;
  form.version += 1;
  return structuredClone(detail);
}

/** 查询指定表单的版本历史摘要，按版本号倒序返回。 */
export async function mockGetEvaluationFormVersions(
  evaluationFormId: number
): Promise<EvaluationFormVersionSummary[]> {
  findForm(evaluationFormId);
  return versions
    .filter((version) => version.evaluationFormId === evaluationFormId)
    .sort((left, right) => right.versionNo - left.versionNo)
    .map((version) =>
      structuredClone({
        formVersionId: version.formVersionId,
        evaluationFormId: version.evaluationFormId,
        versionNo: version.versionNo,
        publishedStatus: version.publishedStatus,
        publishedBy: version.publishedBy,
        publishedTime: version.publishedTime,
        versionNote: version.versionNote
      })
    );
}

/** 查询发布时冻结的评价表单版本详情。 */
export async function mockGetEvaluationFormVersionDetail(
  formVersionId: number
): Promise<EvaluationFormVersionDetail> {
  return structuredClone(findVersion(formVersionId));
}

resetEvaluationFormMockState();
