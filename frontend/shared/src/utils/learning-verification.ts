/**
 * 学习防挂机验证题的跨端统一口径：随机算术题、随机调度间隔与作答时限。
 * 纯函数、无运行时依赖，PC 与移动端学习页共用；弹窗与计时门控由各端实现。
 */

/** 验证题最小调度间隔（毫秒）：不定时弹出，在区间内均匀随机。 */
export const VERIFICATION_MIN_INTERVAL_MS = 60_000;
/** 验证题最大调度间隔（毫秒）。 */
export const VERIFICATION_MAX_INTERVAL_MS = 180_000;
/** 验证题作答时限（秒）：超时未答对按挂机结束当前学习会话。 */
export const VERIFICATION_ANSWER_TIMEOUT_SECONDS = 60;

/** 验证题运算符号；乘号使用全角乘号与页面文案一致。 */
export type VerificationOperation = '+' | '-' | '×';

export interface LearningVerificationQuestion {
  /** 题干文案，如「23 + 47 = ?」。 */
  prompt: string;
  /** 正确答案。 */
  answer: number;
}

/**
 * 生成一道两位数以内的算术验证题：加法取两位数操作数，减法保证结果非负，
 * 乘法取一位数操作数，整体保证可快速口算，避免把验证成本做成学习负担。
 */
export function buildArithmeticQuestion(random: () => number = Math.random): LearningVerificationQuestion {
  const operations: VerificationOperation[] = ['+', '-', '×'];
  const operation = operations[Math.min(operations.length - 1, Math.floor(random() * operations.length))];
  const between = (min: number, max: number): number => min + Math.floor(random() * (max - min + 1));

  if (operation === '+') {
    const left = between(10, 99);
    const right = between(10, 99);
    return { prompt: `${left} + ${right} = ?`, answer: left + right };
  }
  if (operation === '-') {
    const left = between(20, 99);
    const right = between(10, left - 1);
    return { prompt: `${left} - ${right} = ?`, answer: left - right };
  }
  const left = between(2, 9);
  const right = between(2, 9);
  return { prompt: `${left} × ${right} = ?`, answer: left * right };
}

/** 在调度区间内取一个均匀随机的间隔（毫秒）。 */
export function nextVerificationDelayMs(random: () => number = Math.random): number {
  const ratio = Math.min(1, Math.max(0, random()));
  return Math.round(VERIFICATION_MIN_INTERVAL_MS + ratio * (VERIFICATION_MAX_INTERVAL_MS - VERIFICATION_MIN_INTERVAL_MS));
}
