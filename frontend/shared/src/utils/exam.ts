/** 考试域「枚举 → 中文文案」的跨端统一口径，避免各端散落映射并回显裸枚举码。 */

/** exam OpenAPI 的 examType 枚举与中文文案映射，管理端下拉与学员端只读展示共用同一份口径。 */
const EXAM_TYPE_TEXT: Readonly<Record<string, string>> = {
  ONLINE: '线上考试',
  SPECIAL_PRACTICE: '专项练习',
  BANK_PRACTICE: '题库练习',
  OFFLINE: '线下考试'
};

/** 未识别取值的安全兜底，绝不放行原始枚举码（如 UNKNOWN_TYPE）到界面。 */
const EXAM_TYPE_FALLBACK = '考试';

/**
 * 将 examOpenAPI 的 examType 转为中文文案。
 * 契约只约定四类取值；null、空串或未识别值统一降级为「考试」，不暴露内部码。
 */
export function getExamTypeText(examType: string | null | undefined): string {
  if (!examType) return EXAM_TYPE_FALLBACK;
  return EXAM_TYPE_TEXT[examType] ?? EXAM_TYPE_FALLBACK;
}
