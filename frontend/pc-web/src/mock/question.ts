// TODO replace real api

import type {
  KnowledgePoint,
  KnowledgePointCreatePayload,
  Question,
  QuestionAssetPayload,
  QuestionBank,
  QuestionBankCategory,
  QuestionBankCategoryCreatePayload,
  QuestionBankCreatePayload,
  QuestionBankPageQuery,
  QuestionBankUpdatePayload,
  QuestionCreatePayload,
  QuestionImportPayload,
  QuestionImportResult,
  QuestionPage,
  QuestionPageQuery,
  QuestionUpdatePayload
} from '@/api/question';

/** 与 Result 契约一致的题库 Mock 响应。 */
type QuestionMockResponse<TData> = { code: number; message: string; data: TData };

/** 当前浏览器会话内的题库 Mock 数据。 */
const store = {
  knowledgePoints: [] as KnowledgePoint[],
  categories: [] as QuestionBankCategory[],
  banks: [] as QuestionBank[],
  questions: [] as Question[],
  nextKnowledgePointId: 1,
  nextCategoryId: 1,
  nextBankId: 1,
  nextQuestionId: 1,
  nextImportBatchId: 1
};

function success<TData>(data: TData): QuestionMockResponse<TData> {
  return {code: 0, message: 'success', data};
}

function failure<TData>(code: number, message: string, data: TData): QuestionMockResponse<TData> {
  return {code, message, data};
}

/** 重置为确定性的开发种子数据。 */
export function resetQuestionMock(): void {
  store.knowledgePoints.splice(0, store.knowledgePoints.length, {
    knowledgePointId: 1,
    organizationId: 1,
    parentId: 0,
    knowledgeCode: 'SAFE-BASE',
    knowledgeName: '通用安全基础',
    sortOrder: 1,
    status: 1,
    version: 0
  });
  store.categories.splice(0, store.categories.length, {
    bankCategoryId: 1,
    organizationId: 1,
    categoryName: '通用安全',
    description: '全员必修安全知识题库。',
    sortOrder: 1,
    status: 1
  });
  store.banks.splice(0, store.banks.length, {
    questionBankId: 1,
    organizationId: 1,
    bankCode: 'QB-SAFE-001',
    bankName: '安全生产基础题库',
    bankCategoryId: 1,
    difficulty: 'EASY',
    questionOrderMode: 'TYPE',
    archiveStatus: 'ACTIVE',
    status: 1,
    version: 0,
    knowledgePointIds: [1]
  });
  store.questions.splice(0, store.questions.length, {
    questionId: 1,
    organizationId: 1,
    questionBankId: 1,
    questionCode: 'Q-SAFE-001',
    currentVersionNo: 1,
    archiveStatus: 'ACTIVE',
    status: 1,
    version: 0,
    knowledgePointIds: [1],
    currentVersion: {
      questionVersionId: 1,
      questionId: 1,
      versionNo: 1,
      questionType: 'SINGLE',
      stem: '进入生产区域前应当佩戴什么？',
      analysis: '进入生产区域前应按规定佩戴个人防护用品。',
      standardAnswer: '安全帽',
      difficulty: 'EASY',
      gradingMode: 'AUTO',
      publishedStatus: 'PUBLISHED',
      options: [
        {questionOptionId: 1, optionKey: 'A', optionContent: '安全帽', isCorrect: 1, sortOrder: 1},
        {questionOptionId: 2, optionKey: 'B', optionContent: '耳机', isCorrect: 0, sortOrder: 2}
      ]
    }
  });
  store.nextKnowledgePointId = 2;
  store.nextCategoryId = 2;
  store.nextBankId = 2;
  store.nextQuestionId = 2;
  store.nextImportBatchId = 1;
}

function cloneQuestion(question: Question): Question {
  return {
    ...question,
    knowledgePointIds: [...(question.knowledgePointIds ?? [])],
    currentVersion: question.currentVersion
      ? {
          ...question.currentVersion,
          options: question.currentVersion.options?.map((option) => ({...option})) ?? [],
          blankAnswers: question.currentVersion.blankAnswers?.map((item) => ({...item})) ?? [],
          keywords: question.currentVersion.keywords?.map((item) => ({...item})) ?? [],
          assets: question.currentVersion.assets?.map((item) => ({...item})) ?? []
        }
      : undefined
  };
}

export async function mockGetKnowledgePointTree(): Promise<QuestionMockResponse<KnowledgePoint[]>> {
  return success(store.knowledgePoints.filter((item) => item.organizationId === 1).map((item) => ({...item})));
}

export async function mockCreateKnowledgePoint(data: KnowledgePointCreatePayload): Promise<QuestionMockResponse<number>> {
  const knowledgePointId = store.nextKnowledgePointId++;
  store.knowledgePoints.push({...data, organizationId: 1, knowledgePointId, version: 0});
  return success(knowledgePointId);
}

export async function mockGetQuestionBankCategories(): Promise<QuestionMockResponse<QuestionBankCategory[]>> {
  return success(store.categories.filter((item) => item.organizationId === 1).map((item) => ({...item})));
}

export async function mockCreateQuestionBankCategory(data: QuestionBankCategoryCreatePayload): Promise<QuestionMockResponse<number>> {
  const bankCategoryId = store.nextCategoryId++;
  store.categories.push({...data, bankCategoryId});
  return success(bankCategoryId);
}

export async function mockGetQuestionBankPage(params: QuestionBankPageQuery): Promise<QuestionMockResponse<QuestionPage<QuestionBank>>> {
  const keyword = params['query.bankName']?.trim().toLocaleLowerCase();
  const filtered = store.banks.filter(
    (item) =>
      (!keyword || item.bankName.toLocaleLowerCase().includes(keyword)) &&
      (params['query.bankCategoryId'] === undefined || item.bankCategoryId === params['query.bankCategoryId']) &&
      (params['query.archiveStatus'] === undefined || item.archiveStatus === params['query.archiveStatus']) &&
      (params['query.status'] === undefined || item.status === params['query.status'])
  );
  const start = Math.max(0, (params.pageNum - 1) * params.pageSize);
  return success({
    list: filtered.slice(start, start + params.pageSize).map((item) => ({...item})),
    total: filtered.length
  });
}

export async function mockGetQuestionPage(params: QuestionPageQuery): Promise<QuestionMockResponse<QuestionPage<Question>>> {
  const filtered = store.questions.filter(
    (item) =>
      (params['query.questionBankId'] === undefined || item.questionBankId === params['query.questionBankId']) &&
      (!params['query.questionCode'] || item.questionCode.includes(params['query.questionCode'])) &&
      (params['query.archiveStatus'] === undefined || item.archiveStatus === params['query.archiveStatus']) &&
      (params['query.publishedStatus'] === undefined ||
        item.currentVersion?.publishedStatus === params['query.publishedStatus']) &&
      (params['query.status'] === undefined || item.status === params['query.status'])
  );
  const start = Math.max(0, (params.pageNum - 1) * params.pageSize);
  return success({list: filtered.slice(start, start + params.pageSize).map(cloneQuestion), total: filtered.length});
}

export async function mockGetQuestionDetail(questionId: number): Promise<QuestionMockResponse<Question>> {
  const question = store.questions.find((item) => item.questionId === questionId);
  return question ? success(cloneQuestion(question)) : failure(5011, '题目不存在或已删除。', null as never);
}

export async function mockCreateQuestionBank(data: QuestionBankCreatePayload): Promise<QuestionMockResponse<number>> {
  if (store.banks.some((item) => item.bankCode === data.bankCode)) return failure(5012, '题库编码已存在。', 0);
  const questionBankId = store.nextBankId++;
  store.banks.push({...data, questionBankId, version: 0});
  return success(questionBankId);
}

export async function mockUpdateQuestionBank(
  questionBankId: number,
  data: QuestionBankUpdatePayload
): Promise<QuestionMockResponse<null>> {
  const bank = store.banks.find((item) => item.questionBankId === questionBankId);
  if (!bank) return failure(5011, '题库不存在或已删除。', null);
  if (bank.version !== data.version) return failure(2040, '数据已被更新，请刷新后重试。', null);
  Object.assign(bank, {
    bankName: data.bankName,
    bankCategoryId: data.bankCategoryId,
    difficulty: data.difficulty,
    questionOrderMode: data.questionOrderMode,
    status: data.status,
    knowledgePointIds: [...(data.knowledgePointIds ?? [])],
    version: data.version + 1
  });
  return success(null);
}

export async function mockCreateQuestion(data: QuestionCreatePayload): Promise<QuestionMockResponse<number>> {
  if (!store.banks.some((item) => item.questionBankId === data.questionBankId)) return failure(5011, '题库不存在或已删除。', 0);
  if (store.questions.some((item) => item.questionCode === data.questionCode)) return failure(5012, '题目编码已存在。', 0);
  const questionId = store.nextQuestionId++;
  store.questions.push({
    questionId,
    organizationId: 1,
    questionBankId: data.questionBankId,
    questionCode: data.questionCode,
    currentVersionNo: 1,
    archiveStatus: 'ACTIVE',
    status: 1,
    version: 0,
    knowledgePointIds: [...(data.knowledgePointIds ?? [])],
    currentVersion: {
      questionVersionId: questionId,
      questionId,
      versionNo: 1,
      questionType: data.questionType,
      stem: data.stem,
      analysis: data.analysis,
      standardAnswer: data.standardAnswer,
      difficulty: data.difficulty,
      gradingMode: data.gradingMode,
      publishedStatus: 'DRAFT',
      versionNote: data.versionNote,
      isUnorderedAnswerAllowed: data.isUnorderedAnswerAllowed,
      isCaseIgnored: data.isCaseIgnored,
      isFullHalfWidthIgnored: data.isFullHalfWidthIgnored,
      options: data.options?.map((option, index) => ({...option, questionOptionId: index + 1})) ?? []
    }
  });
  return success(questionId);
}

export async function mockUpdateQuestion(questionId: number, data: QuestionUpdatePayload): Promise<QuestionMockResponse<null>> {
  const question = store.questions.find((item) => item.questionId === questionId);
  if (!question) return failure(5011, '题目不存在或已删除。', null);
  if (question.version !== data.version) return failure(2040, '数据已被更新，请刷新后重试。', null);
  if (!question.currentVersion) return failure(5011, '题目当前版本不存在。', null);
  if (question.currentVersion.publishedStatus === 'PUBLISHED') return failure(6009, '已发布题目版本不可编辑。', null);
  question.currentVersion = {
    ...question.currentVersion,
    ...data,
    options: data.options?.map((option, index) => ({...option, questionOptionId: index + 1})) ?? [],
    publishedStatus: 'DRAFT'
  };
  question.knowledgePointIds = [...(data.knowledgePointIds ?? [])];
  question.version += 1;
  return success(null);
}

export async function mockReplaceQuestionAssets(questionId: number, data: QuestionAssetPayload[]): Promise<QuestionMockResponse<null>> {
  const question = store.questions.find((item) => item.questionId === questionId);
  if (!question?.currentVersion) return failure(5011, '题目不存在或已删除。', null);
  question.currentVersion.assets = data.map((item, index) => ({
    ...item,
    questionAssetId: index + 1,
    questionVersionId: question.currentVersion!.questionVersionId
  }));
  return success(null);
}

export async function mockImportQuestions(
  questionBankId: number,
  data: QuestionImportPayload
): Promise<QuestionMockResponse<QuestionImportResult>> {
  if (!store.banks.some((item) => item.questionBankId === questionBankId)) return failure(5011, '题库不存在或已删除。', null as never);
  let successRows = 0;
  for (const row of data.rows) {
    if (!row.questionCode || !row.stem) continue;
    const result = await mockCreateQuestion({
      ...row,
      questionBankId,
      gradingMode: 'AUTO'
    });
    if (result.code === 0) successRows += 1;
  }
  return success({
    importBatchId: store.nextImportBatchId++,
    importStatus: successRows === data.rows.length ? 'SUCCESS' : 'PARTIAL_SUCCESS',
    totalRows: data.rows.length,
    successRows,
    failureRows: data.rows.length - successRows
  });
}

export async function mockPublishQuestion(questionId: number): Promise<QuestionMockResponse<null>> {
  const question = store.questions.find((item) => item.questionId === questionId);
  if (!question?.currentVersion) return failure(5011, '题目不存在或已删除。', null);
  question.currentVersion.publishedStatus = 'PUBLISHED';
  question.currentVersion.publishedTime = '2026-07-28T10:00:00';
  return success(null);
}

resetQuestionMock();
