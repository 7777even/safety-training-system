/** 本人练习契约，与Question OpenAPI一致。 */
export type PracticeMode = 'PRACTICE' | 'SIMULATION';
export interface PracticeAnswer { text?: string | null; selected: string[]; blanks: Record<string, string> }
export interface PracticeQuestion {
  itemId: number; questionId: number; questionVersionId: number;
  type: 'SINGLE' | 'MULTIPLE' | 'JUDGE' | 'FILL' | 'ESSAY'; stem: string;
  options: { key: string; content: string }[]; blankCount: number | null;
  analysis: string | null; correctAnswerJson: string | null;
  answer: PracticeAnswer | null; evaluationResult: 'AUTO_CORRECT' | 'AUTO_WRONG' | 'NOT_EVALUATED' | null;
  favorite: boolean; sourceType: string | null; sourceName: string | null; wrongKey: string | null;
}
export interface PracticeSession {
  sessionId: number; candidateId: number | null; sourceQuestionId: number | null; title: string; mode: PracticeMode; status: 'ANSWERING' | 'SUBMITTED' | 'CLEARED';
  startedTime: string; submittedTime: string | null; correctCount: number; wrongCount: number;
  unassessedCount: number; accuracyRate: number | null; usedSeconds: number; questions: PracticeQuestion[];
  /** 冻结题源与真实进度；记录列表不携带题目答案。 */
  totalCount: number; answeredCount: number; questionStem: string | null;
  sourceType: string; sourceName: string | null;
}
export interface PracticeTask { candidateId: number; title: string; kind: 'SPECIAL_PRACTICE' | 'BANK_PRACTICE' }
/** 收藏题目只提供安全摘要及本人会话入口，不包含答案与解析。 */
export interface FavoriteQuestion {
  questionId: number; questionVersionId: number | null; type: PracticeQuestion['type'] | null;
  stem: string; sourceType: string | null; sourceName: string | null;
  sessionId: number | null; available: boolean;
}
export interface PracticeStart { candidateId?: number; questionId?: number; mode: PracticeMode; clientToken: string }
export interface LearnerPage<T> { list: T[]; total: number }
