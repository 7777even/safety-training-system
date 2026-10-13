import {getExamTypeText} from '@shared/utils/exam';

/** 考试域统一的 label-value 字典项，表单提交 value、界面展示 label。 */
export interface ExamEnumOption<T extends string = string> {
  label: string;
  value: T;
}

/** 按字典值读取中文文案，未知值保留原值以便识别 Contract 新增枚举。 */
export function getExamEnumLabel<T extends string>(
  value: T | null | undefined,
  options: ReadonlyArray<ExamEnumOption<T>>,
  fallback = '其他'
): string {
  if (!value) return '—';
  return options.find((item) => item.value === value)?.label ?? fallback;
}

/** 题型字典，与 question/paper OpenAPI 当前值一致。 */
export const QUESTION_TYPE_OPTIONS = [
  {label: '单选题', value: 'SINGLE'},
  {label: '多选题', value: 'MULTIPLE'},
  {label: '判断题', value: 'JUDGE'},
  {label: '填空题', value: 'FILL'},
  {label: '简答题', value: 'ESSAY'}
] as const;

/** 题目难度字典，与 question OpenAPI 当前值一致。 */
export const QUESTION_DIFFICULTY_OPTIONS = [
  {label: '简单', value: 'EASY'},
  {label: '中等', value: 'MEDIUM'},
  {label: '困难', value: 'HARD'}
] as const;

/** 随机抽题难度字典，与 PaperDrawRuleVO 的枚举一致。 */
export const DRAW_DIFFICULTY_OPTIONS = [
  {label: '不限', value: 'ALL'},
  {label: '简单', value: 'EASY'},
  {label: '普通', value: 'NORMAL'},
  {label: '困难', value: 'HARD'},
  {label: '很难', value: 'VERY_HARD'}
] as const;

/** OpenAPI 当前只约定按导入顺序，后续扩值时在此统一补充。 */
export const QUESTION_ORDER_MODE_OPTIONS = [{label: '按组卷顺序', value: 'IMPORT'}] as const;

/** OpenAPI 当前只约定全对得分，后续扩值时在此统一补充。 */
export const MULTI_SELECT_SCORING_OPTIONS = [{label: '全部选对才得分', value: 'ALL_OR_NOTHING'}] as const;

/** 考试类型字典，与 exam OpenAPI 的 examType 枚举一致。 */
export const EXAM_TYPE_OPTIONS = [
  {label: '线上考试', value: 'ONLINE'},
  {label: '线下考试', value: 'OFFLINE'},
  {label: '专项练习', value: 'SPECIAL_PRACTICE'},
  {label: '题库练习', value: 'BANK_PRACTICE'}
] as const;

/** 试卷组卷类型字典。 */
export const PAPER_TYPE_OPTIONS = [
  {label: '固定试卷', value: 'FIXED', description: '每次使用同一批从题库选择的题目。'},
  {label: '随机试卷', value: 'RANDOM', description: '按照题库与筛选规则随机抽题。'},
  {label: '自定义试卷', value: 'CUSTOM', description: '固定题目与随机抽题规则的组合。'}
] as const;

/** 发布状态字典，供试卷和考试只读详情复用。 */
export const PUBLISH_STATUS_OPTIONS = [
  {label: '草稿', value: 'DRAFT'},
  {label: '已发布', value: 'PUBLISHED'},
  {label: '已归档', value: 'ARCHIVED'}
] as const;

/** 补考范围与考试策略 retakeScope 的既有业务值保持一致。 */
export const RETAKE_SCOPE_OPTIONS = [
  {label: '仅不及格人员', value: 'FAILED'},
  {label: '不及格或未参加人员', value: 'FAILED_OR_ABSENT'},
  {label: '仅未参加人员', value: 'ABSENT'}
] as const;

/** 补考成绩取值方式。 */
export const RETAKE_SCORE_MODE_OPTIONS = [
  {label: '取最高分', value: 'HIGHEST'},
  {label: '取最近一次', value: 'LATEST'}
] as const;

/** 答题详情可见范围。 */
export const ANSWER_DETAIL_MODE_OPTIONS = [
  {label: '交卷后可见', value: 'SUBMITTED'},
  {label: '成绩发布后可见', value: 'PUBLISHED'},
  {label: '始终不可见', value: 'NEVER'}
] as const;

/** 考生状态当前 Contract 示例值。 */
export const CANDIDATE_STATUS_OPTIONS = [
  {label: '待考试', value: 'PENDING'},
  {label: '未开始', value: 'NOT_STARTED'},
  {label: '进行中', value: 'IN_PROGRESS'},
  {label: '已交卷', value: 'SUBMITTED'},
  {label: '待批阅', value: 'PENDING_REVIEW'},
  {label: '待发布成绩', value: 'PENDING_PUBLISH'},
  {label: '练习已完成', value: 'COMPLETED'},
  {label: '已完成', value: 'FINISHED'},
  {label: '已通过', value: 'PASSED'},
  {label: '未通过', value: 'FAILED'}
] as const;

/** 批阅人配置来源，与 ExamReviewer.assignmentSource Contract 一致。 */
export const REVIEWER_ASSIGNMENT_SOURCE_OPTIONS = [
  {label: '部门配置', value: 'DEPT'},
  {label: '用户组配置', value: 'GROUP'},
  {label: '全部人员', value: 'ALL'},
  {label: '角色配置', value: 'ROLE'},
  {label: '标签配置', value: 'TAG'},
  {label: '岗位配置', value: 'POSITION'},
  {label: '指定人员', value: 'USER'}
] as const;

/** 学员考试任务状态。 */
export const LEARNER_TASK_STATUS_OPTIONS = [
  {label: '未开始', value: 'NOT_STARTED'},
  {label: '作答中', value: 'IN_PROGRESS'},
  {label: '等待成绩', value: 'WAITING_RESULT'},
  {label: '成绩已发布', value: 'RESULT_PUBLISHED'}
] as const;

/** ExamLearnerTaskVO.availableAction 的服务端建议操作。 */
export const LEARNER_EXAM_ACTION = {
  START: 'START',
  CONTINUE: 'CONTINUE',
  WAIT_REVIEW: 'WAIT_REVIEW',
  VIEW_RESULT: 'VIEW_RESULT'
} as const;

/** 任务状态对应的展示标签颜色，避免页面散落枚举判断。 */
export function getLearnerTaskTagType(taskStatus: string): 'success' | 'warning' | 'info' {
  if (taskStatus === 'RESULT_PUBLISHED') return 'success';
  if (taskStatus === 'IN_PROGRESS') return 'warning';
  return 'info';
}

/** 考试类型必须显示为中文；文案与学员端共用 `@shared/utils/exam`，未识别值不暴露原始枚举。 */
export function getExamTypeLabel(examType: string): string {
  return getExamTypeText(examType);
}

/** 题型中文文案；未识别值给出可操作提示，不回显原始枚举码。 */
export function getQuestionTypeLabel(questionType: string): string {
  return QUESTION_TYPE_OPTIONS.find((item) => item.value === questionType)?.label ?? '未选择题型';
}

/** 抽题难度中文文案；为空表示不限难度。 */
export function getDrawDifficultyLabel(difficulty?: string): string {
  if (!difficulty) return '不限';
  return DRAW_DIFFICULTY_OPTIONS.find((item) => item.value === difficulty)?.label ?? '不限';
}

/** 抽题规则的展示用最小结构；考试侧与试卷侧规则字段一致，此处只声明展示所需字段。 */
export interface DrawRuleLike {
  questionType: string;
  difficulty?: string;
  knowledgePointId?: number;
  drawCount?: number;
  scorePerQuestion?: number;
}

/** 抽题规则合计题数与分值，供管理端核对及格分上限与总分来源。 */
export function sumDrawRules(rules: readonly DrawRuleLike[]): { count: number; score: number } {
  return rules.reduce(
    (summary, rule) => ({
      count: summary.count + (rule.drawCount || 0),
      score: summary.score + (rule.drawCount || 0) * (rule.scorePerQuestion || 0)
    }),
    { count: 0, score: 0 }
  );
}

/** 抽题分值按整数优先展示，避免出现 4.50 这类冗余小数。 */
export function formatDrawRuleScore(value: number): string {
  return Number.isInteger(value) ? String(value) : value.toFixed(2);
}

/** 单条题库练习规则的中文描述；题库练习只记录作答与反馈，不展示分值。 */
export function getExamDrawRuleText(rule: DrawRuleLike): string {
  const knowledge = rule.knowledgePointId ? `知识点 #${rule.knowledgePointId}` : '不限知识点';
  return `${getQuestionTypeLabel(rule.questionType)} · 难度${getDrawDifficultyLabel(rule.difficulty)} · ${knowledge} · 抽 ${rule.drawCount || 0} 题`;
}

/** 候选人与作答状态在任务卡中只显示中文标签。 */
export function getCandidateStatusLabel(status: string): string {
  return CANDIDATE_STATUS_OPTIONS.find((item) => item.value === status)?.label ?? '其他状态';
}

export function getAttemptStatusLabel(status: string): string {
  return ATTEMPT_STATUS_OPTIONS.find((item) => item.value === status)?.label ?? '其他状态';
}

/** 考试尝试状态。 */
export const ATTEMPT_STATUS_OPTIONS = [
  {label: '作答中', value: 'ANSWERING'},
  {label: '已交卷', value: 'SUBMITTED'},
  {label: '待批阅', value: 'PENDING_REVIEW'},
  {label: '待发布成绩', value: 'PENDING_PUBLISH'},
  {label: '结果已生效', value: 'RESULT_EFFECTIVE'},
  {label: '已发布', value: 'PUBLISHED'}
] as const;

/** 成绩发布状态。 */
export const SCORE_PUBLISH_STATUS_OPTIONS = [
  {label: '未发布', value: 'UNPUBLISHED'},
  {label: '已发布', value: 'PUBLISHED'},
  {label: '已生效', value: 'EFFECTIVE'}
] as const;

/** 单题批阅状态。 */
export const GRADING_STATUS_OPTIONS = [
  {label: '待批阅', value: 'PENDING_REVIEW'},
  {label: '待人工批阅', value: 'PENDING_MANUAL'},
  {label: '自动判分完成', value: 'AUTO_GRADED'},
  {label: '人工批阅完成', value: 'MANUAL_GRADED'}
] as const;

/** 成绩发布方式。 */
export const SCORE_PUBLISH_TYPE_OPTIONS = [
  {label: '单次发布', value: 'SINGLE'},
  {label: '人工发布', value: 'MANUAL'},
  {label: '自动发布', value: 'AUTO'}
] as const;

/** 成绩发布流水的结果快照。 */
export const SCORE_RESULT_OPTIONS = [
  {label: '通过', value: 'PASSED'},
  {label: '未通过', value: 'FAILED'},
  {label: '不判定', value: 'NOT_APPLICABLE'}
] as const;

/** 作答端实际会写入的反作弊事件类型；未命中时管理端回退显示原始事件码。 */
export const ANTICHEAT_EVENT_TYPE_OPTIONS = [
  {label: '切离考试页面', value: 'SWITCH_AWAY'},
  {label: '复制粘贴内容', value: 'COPY'}
] as const;

/** OpenAPI 当前已给出的反作弊处置动作。 */
export const ANTICHEAT_ACTION_OPTIONS = [
  {label: '已记录', value: 'RECORD'},
  {label: '警告', value: 'WARN'}
] as const;
