// TODO replace real api：考试后端接口联调通过后删除本文件及 API Mock 分支。
import type { ApiResponse } from '@shared/types';
import type {
  BindExamSelectionPayload,
  ExamCandidate,
  ExamCandidatePageQuery,
  ExamCreatePayload,
  ExamDrawRulePayload,
  ExamItem,
  ExamPage,
  ExamPageQuery,
  ExamReviewerPayload,
  ExamReviewer,
  ExamUpdatePayload,
  OfflineScoreRecordResult,
  OfflineScoresPayload
} from '@/api/exam';

const success = <T>(data: T): ApiResponse<T> => ({ code: 0, message: 'success', data });
const failed = <T>(code: number, message: string): ApiResponse<T> => ({ code, message, data: null as T });
/** 线下考试成绩允许录入的考试状态，与真实 Contract 一致。 */
const RECORDABLE_PUBLISH_STATUS = ['PUBLISHED', 'RUNNING', 'ENDED'];
const store = {
  exams: [] as ExamItem[],
  candidates: new Map<number, ExamCandidate[]>(),
  reviewers: new Map<number, number[]>(),
  nextExamId: 3,
  nextCandidateId: 1
};

/** Mock 试卷版本的默认规则与总分，用于模拟后端物化考试生效值。 */
const PAPER_VERSION_DEFAULTS: Record<number, { durationSeconds: number; passRatio: number; questionOrderMode: string; multiSelectScoring: string; totalScore: number; coverFileId?: number }> = {
  1: { durationSeconds: 3600, passRatio: 0.6, questionOrderMode: 'IMPORT', multiSelectScoring: 'ALL_OR_NOTHING', totalScore: 100 }
};

type ResolvedRuleValues = Pick<ExamItem, 'paperVersionId' | 'durationSeconds' | 'passRatio' | 'passScore' | 'questionOrderMode' | 'multiSelectScoring'>;

/** 按创建/草稿更新契约解析本场规则；绑卷时允许本场覆盖及格分。 */
function resolveRuleValues(data: ExamCreatePayload | ExamUpdatePayload, existing?: ExamItem): ResolvedRuleValues | undefined {
  const examType = existing?.examType ?? ('examType' in data ? data.examType : undefined);
  const bankPractice = examType === 'BANK_PRACTICE';
  const paperVersionId = data.paperVersionId === undefined ? existing?.paperVersionId : data.paperVersionId ?? undefined;
  if (paperVersionId !== undefined) {
    const defaults = PAPER_VERSION_DEFAULTS[paperVersionId];
    if (!defaults) return undefined;
    const preserveExisting = data.paperVersionId === undefined && existing;
    const durationSeconds = data.durationSeconds ?? (preserveExisting ? existing.durationSeconds : defaults.durationSeconds);
    const passRatio = data.passRatio ?? (preserveExisting ? existing.passRatio : defaults.passRatio);
    const questionOrderMode = data.questionOrderMode ?? (preserveExisting ? existing.questionOrderMode : defaults.questionOrderMode);
    const multiSelectScoring = data.multiSelectScoring ?? (preserveExisting ? existing.multiSelectScoring : defaults.multiSelectScoring);
    return {
      paperVersionId,
      durationSeconds,
      passRatio,
      passScore: data.passScore ?? (preserveExisting && data.passRatio === undefined
        ? existing.passScore
        : Math.round(defaults.totalScore * passRatio * 100) / 100),
      questionOrderMode,
      multiSelectScoring
    };
  }
  if (data.durationSeconds === undefined || !data.questionOrderMode || !data.multiSelectScoring
    || (!bankPractice && (data.passRatio === undefined || data.passScore === undefined))) return undefined;
  return {
    paperVersionId: undefined,
    durationSeconds: data.durationSeconds,
    passRatio: bankPractice ? 0 : data.passRatio!,
    passScore: bankPractice ? 0 : data.passScore!,
    questionOrderMode: data.questionOrderMode,
    multiSelectScoring: data.multiSelectScoring
  };
}

/** 重置浏览器会话的考试 Mock 种子数据。 */
export function resetExamMock(): void {
  store.exams = [
    {
      examId: 1,
      organizationId: 1,
      examCode: 'EXAM-001',
      examName: '安全生产基础考试',
      examType: 'ONLINE',
      paperVersionId: 1,
      durationSeconds: 3600,
      passRatio: 0.6,
      passScore: 60,
      credit: 1,
      questionOrderMode: 'IMPORT',
      multiSelectScoring: 'ALL_OR_NOTHING',
      isNoticeEnabled: 1,
      noticeContent: '请独立完成考试。',
      publishStatus: 'DRAFT',
      status: 1,
      version: 0,
      createTime: '2026-07-28T09:00:00'
    },
    {
      examId: 2,
      organizationId: 1,
      examCode: 'EXAM-002',
      examName: '可选已发布考试',
      examType: 'ONLINE',
      paperVersionId: 1,
      durationSeconds: 3600,
      passRatio: 0.6,
      passScore: 60,
      credit: 1,
      questionOrderMode: 'IMPORT',
      multiSelectScoring: 'ALL_OR_NOTHING',
      isNoticeEnabled: 1,
      publishStatus: 'PUBLISHED',
      publishedTime: '2026-07-28T10:00:00',
      status: 1,
      version: 0,
      createTime: '2026-07-28T09:30:00'
    }
  ];
  store.candidates.clear();
  store.reviewers.clear();
  store.nextExamId = 3;
  store.nextCandidateId = 1;
}

export async function mockGetExamPage(query: ExamPageQuery): Promise<ApiResponse<ExamPage>> {
  const keyword = query['query.examName']?.trim();
  const list = store.exams.filter(
    (item) =>
      (!query['query.examType'] || item.examType === query['query.examType']) &&
      (!query['query.publishStatus'] || item.publishStatus === query['query.publishStatus']) &&
      (!keyword || item.examName.includes(keyword)) &&
      (query['query.status'] === undefined || item.status === query['query.status'])
  );
  const start = (query.pageNum - 1) * query.pageSize;
  return success({
    list: list.slice(start, start + query.pageSize).map((item) => structuredClone(item)),
    total: list.length
  });
}

export async function mockGetExam(examId: number): Promise<ApiResponse<ExamItem>> {
  const item = findExam(examId);
  return item ? success(structuredClone(item)) : failed(7001, '考试不存在。');
}

export async function mockCreateExam(data: ExamCreatePayload): Promise<ApiResponse<number>> {
  if (store.exams.some((item) => item.examCode === data.examCode)) return failed(7002, '考试编码已存在。');
  const drawRulesError = validateDrawRules(data.examType, data.drawRules);
  if (drawRulesError) return failed(drawRulesError.code, drawRulesError.message);
  const rules = resolveRuleValues(data);
  if (!rules) return failed(data.paperVersionId ? 7005 : 1001, data.paperVersionId ? '试卷版本不存在或不可用。' : '未绑定试卷时请填写完整考试规则。');
  const definitionInput: Omit<ExamCreatePayload, 'paperVersionId'> = { ...data };
  const definition: Omit<ExamItem, 'examId' | 'publishStatus' | 'version' | 'createTime'> = {
    ...definitionInput,
    ...rules,
    organizationId: 1,
    coverFileId: data.coverFileId ?? PAPER_VERSION_DEFAULTS[rules.paperVersionId ?? -1]?.coverFileId,
    policy: data.policy ? { ...data.policy } : undefined
  };
  const examId = store.nextExamId++;
  store.exams.push({ ...definition, examId, publishStatus: 'DRAFT', version: 0, createTime: new Date().toISOString() });
  return success(examId);
}

export async function mockUpdateExam(examId: number, data: ExamUpdatePayload): Promise<ApiResponse<void>> {
  const item = findExam(examId);
  if (!item) return failed(7001, '考试不存在。');
  if (item.publishStatus !== 'DRAFT') return failed(7003, '仅草稿考试可编辑或删除。');
  if (item.version !== data.version) return failed(1001, '数据已被更新，请刷新后重试。');
  const drawRulesError = validateDrawRules(item.examType, data.drawRules);
  if (drawRulesError) return failed(drawRulesError.code, drawRulesError.message);
  const rules = resolveRuleValues(data, item);
  if (!rules) {
    const nextPaperVersionId = data.paperVersionId === undefined ? item.paperVersionId : data.paperVersionId;
    return failed(nextPaperVersionId ? 7005 : 1001, nextPaperVersionId ? '试卷版本不存在或不可用。' : '未绑定试卷时请填写完整考试规则。');
  }
  const definition = { ...data, ...rules, policy: data.policy ? { ...data.policy } : undefined };
  Object.assign(item, definition, { version: item.version + 1 });
  return success(undefined);
}

export async function mockDeleteExam(examId: number): Promise<ApiResponse<void>> {
  const index = store.exams.findIndex((item) => item.examId === examId);
  if (index < 0) return failed(7001, '考试不存在。');
  if (store.exams[index].publishStatus !== 'DRAFT') return failed(7003, '仅草稿考试可编辑或删除。');
  store.exams.splice(index, 1);
  store.candidates.delete(examId);
  store.reviewers.delete(examId);
  return success(undefined);
}

export async function mockBindExamSelection(examId: number, data: BindExamSelectionPayload): Promise<ApiResponse<void>> {
  const exam = findExam(examId);
  if (!exam) return failed(7001, '考试不存在。');
  if (data.selection.selectionType !== 'USER' || !data.selection.subjectIds.length) return failed(1001, '请选择至少一名考生。');
  store.candidates.set(
    examId,
    [...new Set(data.selection.subjectIds)].map((userId) => ({
      examCandidateId: store.nextCandidateId++,
      examId,
      userId,
      learnerName: `用户${userId}`,
      learnerCode: String(userId),
      learnerOrganizationId: exam.organizationId,
      learnerOrganizationName: `机构${exam.organizationId}`,
      assessmentExecutionId: 1000 + examId,
      sourceType: 'STANDALONE_EXAM',
      candidateStatus: 'PENDING',
      attemptCount: 0,
      version: 0
    }))
  );
  return success(undefined);
}

export async function mockGetExamCandidates(
  examId: number,
  params: ExamCandidatePageQuery = { pageNum: 1, pageSize: 20 }
): Promise<ApiResponse<ExamPage<ExamCandidate>>> {
  if (!findExam(examId)) return failed(7001, '考试不存在。');
  const keyword = params['query.learnerKeyword']?.trim();
  const filtered = (store.candidates.get(examId) ?? []).filter(
    (item) =>
      (!keyword || `${item.learnerName ?? ''}${item.learnerCode ?? ''}`.includes(keyword)) &&
      (!params['query.candidateStatus'] || item.candidateStatus === params['query.candidateStatus'])
  );
  const start = (params.pageNum - 1) * params.pageSize;
  return success({ list: structuredClone(filtered.slice(start, start + params.pageSize)), total: filtered.length });
}

/** 整体替换 Mock 批阅人；空数组与真实 Contract 一致，表示清空。 */
export async function mockReplaceExamReviewers(examId: number, data: ExamReviewerPayload): Promise<ApiResponse<void>> {
  if (!findExam(examId)) return failed(7001, '考试不存在。');
  // 条件范围依赖真实组织数据，Mock不伪造展开成功。
  if (data.selection) return failed(7070, '条件评卷范围请关闭接口 Mock 后使用真实服务。');
  if (!data.reviewerUserIds) return failed(7070, '请选择评卷人范围或固定名单。');
  store.reviewers.set(examId, [...new Set(data.reviewerUserIds)]);
  return success(undefined);
}

/** 模拟读取当前考试批阅人，提供人员配置页所需展示字段。 */
export async function mockGetExamReviewers(examId: number): Promise<ApiResponse<ExamReviewer[]>> {
  const exam = findExam(examId);
  if (!exam) return failed(7001, '考试不存在。');
  return success(
    (store.reviewers.get(examId) ?? []).map((reviewerUserId, index) => ({
      examReviewerId: index + 1,
      assessmentExecutionId: 1000 + examId,
      examId,
      reviewerUserId,
      reviewerName: `用户${reviewerUserId}`,
      reviewerCode: String(reviewerUserId),
      reviewerOrganizationId: exam.organizationId,
      reviewerOrganizationName: `机构${exam.organizationId}`,
      assignmentSource: 'USER'
    }))
  );
}

export async function mockPublishExam(examId: number): Promise<ApiResponse<void>> {
  const item = findExam(examId);
  if (!item) return failed(7001, '考试不存在。');
  if (item.publishStatus !== 'DRAFT') return failed(7004, '当前状态不可发布。');
  if (item.examType === 'ONLINE' && !item.paperVersionId) return failed(7005, '线上考试未绑定有效的已发布试卷版本。');
  const drawRulesError = validateDrawRules(item.examType, item.drawRules);
  if (drawRulesError) return failed(drawRulesError.code, drawRulesError.message);
  item.publishStatus = 'PUBLISHED';
  item.publishedTime = new Date().toISOString();
  return success(undefined);
}

/** 模拟线下成绩录入：覆盖已有成绩并把考生置为已通过/未通过。 */
export async function mockRecordOfflineScores(
  examId: number,
  data: OfflineScoresPayload
): Promise<ApiResponse<OfflineScoreRecordResult>> {
  const exam = findExam(examId);
  if (!exam) return failed(7001, '考试不存在。');
  if (!RECORDABLE_PUBLISH_STATUS.includes(exam.publishStatus)) return failed(7006, '考试尚未发布，无法录入成绩。');
  if (exam.examType !== 'OFFLINE') return failed(7069, '仅线下考试支持录入成绩。');
  const candidates = store.candidates.get(examId) ?? [];
  for (const item of data.scores) {
    const candidate = candidates.find((row) => row.examCandidateId === item.examCandidateId);
    if (!candidate) return failed(7065, '考生不属于该考试。');
    const maxScore = Math.max(100, exam.passScore ?? 100);
    if (item.finalScore < 0 || item.finalScore > maxScore) {
      return failed(7068, `成绩必须落在 0 到 ${maxScore} 之间。`);
    }
  }
  data.scores.forEach((item) => {
    const candidate = candidates.find((row) => row.examCandidateId === item.examCandidateId);
    if (!candidate) return;
    candidate.finalScore = item.finalScore;
    candidate.bestScore = item.finalScore;
    candidate.candidateStatus = item.finalScore >= exam.passScore ? 'PASSED' : 'FAILED';
    candidate.attemptCount = Math.max(candidate.attemptCount, 1);
    candidate.resultPublishedTime = new Date().toISOString();
  });
  return success({ examId, recordedCount: data.scores.length, skippedCount: 0 });
}

function findExam(examId: number): ExamItem | undefined {
  return store.exams.find((item) => item.examId === examId);
}

/** 题库练习必须配置抽题规则，且及格分不得超过抽题总分；与真实 Contract 校验口径一致。 */
function validateDrawRules(examType: string, drawRules: ExamDrawRulePayload[] | undefined): { code: number; message: string } | undefined {
  if (examType !== 'BANK_PRACTICE') return undefined;
  if (!drawRules?.length) return { code: 7063, message: '题库练习必须配置抽题规则。' };
  return undefined;
}

resetExamMock();
