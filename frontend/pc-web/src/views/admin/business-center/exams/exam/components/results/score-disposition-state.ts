/** 成绩处置表格所需的只读逐题核对行。 */
export interface ScoreDispositionQuestionRow {
  questionNo: number;
  questionType: string;
  answerText: string;
  autoScore: number | null;
  manualScore: number | null;
  finalScore: number | null;
  score: number;
  gradingStatus: string | undefined;
}

interface ScoreDispositionAnswer {
  answerText: string | null;
  items?: Array<{ itemKey: string }>;
  autoScore?: number | null;
  manualScore?: number | null;
  finalScore?: number | null;
  gradingStatus?: string;
}

interface ScoreDispositionQuestion {
  sortOrder: number;
  questionType: string;
  score: number;
  gradingMode: string;
  answer?: ScoreDispositionAnswer | null;
}

interface ScoreDispositionAttempt {
  scorePublishStatus: 'PUBLISHED' | 'UNPUBLISHED' | 'EFFECTIVE';
  questions: ScoreDispositionQuestion[];
}

interface RetakeEligibility {
  eligible: boolean;
  reasons: string[];
}

/** 将作答快照转换为教师核对答案和得分的稳定表格结构。 */
export function toScoreDispositionQuestionRows(questions: ScoreDispositionQuestion[]): ScoreDispositionQuestionRow[] {
  return questions.map((question, index) => {
    const answer = question.answer;
    const answerText = answer?.answerText || answer?.items?.map((item) => item.itemKey).join('、') || '未作答';
    return {
      questionNo: question.sortOrder || index + 1,
      questionType: question.questionType,
      answerText,
      autoScore: answer?.autoScore ?? null,
      manualScore: answer?.manualScore ?? null,
      finalScore: answer?.finalScore ?? null,
      score: question.score,
      gradingStatus: answer?.gradingStatus
    };
  });
}

/** 主观题未人工批阅完成时，正式成绩不得发布。 */
export function hasPendingManualReview(questions: ScoreDispositionQuestion[]): boolean {
  return questions.some((question) => question.gradingMode === 'MANUAL' && question.answer?.gradingStatus !== 'MANUAL_GRADED');
}

/** 已发布或仍有待阅主观题时，隐藏发布可执行状态。 */
export function canPublishScore(attempt: ScoreDispositionAttempt): boolean {
  return !['PUBLISHED', 'EFFECTIVE'].includes(attempt.scorePublishStatus) && !hasPendingManualReview(attempt.questions);
}

/** 开补考入口必须同时满足后端资格与当前用户权限。 */
export function shouldShowRetakeAction(eligibility: RetakeEligibility | undefined, hasRetakePermission: boolean): boolean {
  return Boolean(eligibility?.eligible && hasRetakePermission);
}

/** 将服务端返回的资格原因转为页面提示，避免前端重新推断业务规则。 */
export function getRetakeEligibilityHint(eligibility: RetakeEligibility | undefined): string {
  if (!eligibility) return '暂无法取得补考资格判定，不能开通补考。';
  return eligibility.eligible ? '已满足补考策略，可为该考生开通补考。' : eligibility.reasons.join('；');
}
