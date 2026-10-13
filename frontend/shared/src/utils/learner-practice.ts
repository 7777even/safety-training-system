import type { PracticeQuestion } from '../types/learner-practice';
/** 反馈标签来自受控响应，空值不能推断对错。 */
export function practiceFeedback(value: PracticeQuestion['evaluationResult']): string {
  return value === 'AUTO_CORRECT' ? '回答正确' : value === 'AUTO_WRONG' ? '回答错误' : value === 'NOT_EVALUATED' ? '仅记录答案，不自动判定' : '';
}
/** 将服务端已允许查看的标准答案转为中文可读文本。 */
export function practiceStandardAnswer(json: string | null): string {
  if (!json) return '';
  try {
    const value = JSON.parse(json);
    if (value.blanks?.length) return value.blanks.map((b: { blankNo: number; answers: string[] }) => `第${b.blankNo}空：${b.answers.join(' / ')}`).join('；');
    return value.correctOptions?.length ? value.correctOptions.join('、') : value.standardAnswer || '';
  } catch { return ''; }
}
/** 两端按同一受控字段筛选；模拟提交前不存在错误反馈。 */
export function filterPracticeQuestions(questions: PracticeQuestion[], filter: string): PracticeQuestion[] {
  return questions.filter(q => filter === 'ALL' || filter === 'UNANSWERED' && !q.answer
    || filter === 'WRONG' && q.evaluationResult === 'AUTO_WRONG' || filter === 'FAVORITE' && q.favorite);
}
/** 服务端时间为本地时间，展示时不引入时区转换。 */
export function practiceRecordTime(value: string | null): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '尚未提交';
}
/** 累计会话秒数转为可读时分秒，不作为正式学习时长。 */
export function practiceRecordDuration(seconds: number): string {
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor(seconds % 3600 / 60);
  return `${hours ? `${hours}小时` : ''}${minutes}分${seconds % 60}秒`;
}
/** 历史来源文案兼容旧会话未保存分类的情形。 */
export function practiceSourceLabel(type: string | null): string {
  return ({ SPECIAL_PRACTICE: '专项训练', BANK_PRACTICE: '题库练习', STANDALONE_EXAM: '正式考试',
    TRAINING_EXAM: '培训考试', COURSE_QUIZ: '课程测试', WRONG_QUESTION: '错题再练', PRACTICE: '分配练习' } as Record<string, string>)[type || ''] || '练习题源';
}
