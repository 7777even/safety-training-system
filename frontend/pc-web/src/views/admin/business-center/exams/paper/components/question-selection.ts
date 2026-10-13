/** 题库列表可能只返回题目摘要；确认选择前必须补齐当前版本，才能组卷。 */
export interface SelectableQuestion {
  questionId: number;
  currentVersion?: unknown;
}

/** 将选中的题目统一解析为可直接写入试卷的详情，避免依赖列表响应的字段完整性。 */
export async function resolveSelectedQuestions<TQuestion extends SelectableQuestion>(
  questions: TQuestion[],
  selectedIds: number[],
  loadDetail: (questionId: number) => Promise<TQuestion>
): Promise<TQuestion[]> {
  const selected = questions.filter((question) => selectedIds.includes(question.questionId));
  return Promise.all(selected.map((question) => (question.currentVersion ? question : loadDetail(question.questionId))));
}
