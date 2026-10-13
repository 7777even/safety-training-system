/**
 * 培训计划绑定评价表单的前端预检。
 *
 * 后端在保存绑定（PUT /api/training/plans/{id}/evaluation-forms）时会执行
 * TrainingPlanPublishValidator.validateCompositeBindingsOrThrow，任一条计分内容判据不满足即整单回滚并返回 8128。
 * 本模块把同一组判据前移到界面，让管理员在点击保存前就知道差在哪、该回哪一步修改。
 *
 * 与后端 compositeConfigurationIssue 一一对应；后端规则变化时必须同步本文件。
 * 这里只做判断，不访问接口、不修改入参，便于直接单测。
 */

/** 综合评价只接受这三类计划内容作为成绩来源，题库练习恒不参与计分。 */
export const COMPOSITE_SOURCE_TYPES = ['COURSE', 'EXAM', 'OFFLINE'] as const;

/** 来源类别中文名，与阶段内容面板的类别称谓保持一致。 */
const SOURCE_TYPE_TEXT: Record<string, string> = {
  COURSE: '课程',
  EXAM: '考试',
  OFFLINE: '线下培训'
};

/** 步骤索引：0 = 绑定评价表单，1 = 阶段与内容。 */
const BINDING_STEP = 0;
const STAGE_STEP = 1;

/** 权重合计的浮点容差，与后端「合计为 1」的判据保持一致。 */
const WEIGHT_EPSILON = 0.000001;

export interface PrecheckStageContent {
  contentType: string;
  isScored?: number | null;
  scoreAggregationMode?: string | null;
  scoreWeight?: number | null;
}

export interface PrecheckStage {
  contents?: PrecheckStageContent[] | null;
}

export interface PrecheckScoreRule {
  sourceType: string;
  calculationType: string;
  weight?: number | null;
}

export interface PrecheckVersionRules {
  scoreRules?: PrecheckScoreRule[] | null;
}

export interface PrecheckBinding {
  formVersionId: number;
  usageType: string;
}

export interface CompositeBindingPrecheckInput {
  /** 阶段与内容是否为已保存状态；未保存时前端内存与后端库不一致，来源判断不可信。 */
  stagesSaved: boolean;
  bindings: PrecheckBinding[];
  /** 已加载的评价表单版本详情；键不存在或值为 undefined 表示该版本详情尚未加载完成。 */
  versionDetails: Record<number, PrecheckVersionRules | undefined>;
  /** 仍在请求中的版本号，用于把「读取中」和「确实缺失」区分开，避免刚选完版本就提示重新选择。 */
  loadingVersionIds?: number[];
  stages: PrecheckStage[];
}

export interface CompositeBindingIssue {
  /** true 表示后端必然拒绝保存，未处理前不应提交。 */
  blocked: boolean;
  /** 涉及的成绩来源类别，便于界面定位到具体卡片。 */
  sourceType?: string;
  /** 面向管理员的中文说明，包含问题原因和前往修改的位置。 */
  message: string;
  /** 建议用户回到的步骤索引：0 = 绑定评价表单，1 = 阶段与内容。 */
  targetStep: number;
}

/** 来源类别兜底文案，避免界面出现英文枚举或 undefined。 */
function sourceText(sourceType: string): string {
  return SOURCE_TYPE_TEXT[sourceType] ?? sourceType;
}

/** 权重统一按两位小数呈现，与内容权重输入口径一致。 */
function weightText(value: number): string {
  return value.toFixed(2);
}

/**
 * 收集绑定评价表单前必须处理的问题，顺序即建议的处理顺序。
 * 返回空数组表示前端判据全部通过，可以直接提交给后端。
 */
export function collectCompositeBindingIssues(input: CompositeBindingPrecheckInput): CompositeBindingIssue[] {
  const issues: CompositeBindingIssue[] = [];
  const bindings = input.bindings ?? [];

  // 阶段内容未保存时，前端的 isScored / 汇总方式可能还没落库，先要求保存再判定来源。
  if (!input.stagesSaved) {
    issues.push({
      blocked: true,
      message: '“阶段与内容”有未保存的改动，请先在步骤 2 保存，再绑定评价表单。',
      targetStep: STAGE_STEP
    });
    return issues;
  }

  // 同一用途重复绑定：请求内重复或唯一键冲突都会让后端整单回滚（8133）。
  const usageCounts = new Map<string, number>();
  for (const binding of bindings) {
    usageCounts.set(binding.usageType, (usageCounts.get(binding.usageType) ?? 0) + 1);
  }
  for (const [usageType, count] of usageCounts) {
    if (count > 1) {
      issues.push({
        blocked: true,
        message: `评价用途 ${usageType} 同时绑定了 ${count} 个表单，同一用途只能保留一个。`,
        targetStep: BINDING_STEP
      });
    }
  }

  // 未绑定综合评价时不触发 8128；专家检查或其他用途由后端另行校验。
  const compositeBindings = bindings.filter((binding) => binding.usageType === 'COMPOSITE');
  if (!compositeBindings.length) return issues;

  const contents = (input.stages ?? []).flatMap((stage) => stage.contents ?? []);

  for (const binding of compositeBindings) {
    const versionDetail = input.versionDetails[binding.formVersionId];
    if (!versionDetail) {
      const loading = (input.loadingVersionIds ?? []).includes(binding.formVersionId);
      issues.push({
        blocked: true,
        message: loading
          ? `正在读取评价表单版本 ${binding.formVersionId} 的计分规则，请稍候再保存。`
          : `评价表单版本 ${binding.formVersionId} 的冻结规则尚未加载完成，请重新选择该版本或刷新页面后再保存。`,
        targetStep: BINDING_STEP
      });
      continue;
    }

    const rules = versionDetail.scoreRules ?? [];
    if (!rules.length) {
      issues.push({
        blocked: true,
        message: '综合评价表单必须至少配置一条计分规则，请到评价表单页补充后发布新版本。',
        targetStep: BINDING_STEP
      });
      continue;
    }

    // 后端逐条判定规则，任一条不合规即拒绝，因此这里也逐条给出结论。
    const seenSources = new Set<string>();
    let ruleWeightTotal = 0;
    // 规则本身不合规时不再判定权重合计：与后端「首错即返回」一致，避免给出 0.00 这类误导结论。
    let hasInvalidRule = false;
    for (const rule of rules) {
      const sourceType = rule.sourceType;
      const label = sourceText(sourceType);
      if (
        rule.calculationType !== 'WEIGHT' ||
        rule.weight == null ||
        rule.weight <= 0 ||
        !(COMPOSITE_SOURCE_TYPES as readonly string[]).includes(sourceType) ||
        seenSources.has(sourceType)
      ) {
        hasInvalidRule = true;
        issues.push({
          blocked: true,
          sourceType,
          message: `综合评价表单规则不合规：来源${label}必须使用“权重折算”并填写大于 0 的权重，且同一来源只能出现一次。`,
          targetStep: BINDING_STEP
        });
        continue;
      }
      seenSources.add(sourceType);
      ruleWeightTotal += rule.weight;

      // 该来源必须存在「计入综合成绩」的同类计划内容，这是 8128 最常见的来源。
      const scoredContents = contents.filter((content) => content.isScored === 1 && content.contentType === sourceType);
      if (!scoredContents.length) {
        issues.push({
          blocked: true,
          sourceType,
          message: `表单要求${label}成绩，但计划中没有勾选“计入综合成绩”的${label}内容。请回到步骤 2 为${label}内容打开“计入综合成绩”并保存。`,
          targetStep: STAGE_STEP
        });
        continue;
      }

      const modes = new Set(scoredContents.map((content) => content.scoreAggregationMode ?? ''));
      if (modes.size !== 1 || modes.has('')) {
        issues.push({
          blocked: true,
          sourceType,
          message: `${label}计分内容必须统一选择“算术平均”或“加权平均”，请回到步骤 2 调整后保存。`,
          targetStep: STAGE_STEP
        });
        continue;
      }

      const mode = [...modes][0];
      if (mode === 'AVERAGE') {
        if (scoredContents.some((content) => content.scoreWeight != null)) {
          issues.push({
            blocked: true,
            sourceType,
            message: `${label}计分内容已选择“算术平均”，不能再填写内容权重，请回到步骤 2 清空权重后保存。`,
            targetStep: STAGE_STEP
          });
        }
        continue;
      }

      if (mode === 'WEIGHTED_AVERAGE') {
        const total = scoredContents.reduce((sum, content) => sum + (content.scoreWeight ?? 0), 0);
        if (
          scoredContents.some((content) => content.scoreWeight == null || content.scoreWeight <= 0) ||
          Math.abs(total - 1) > WEIGHT_EPSILON
        ) {
          issues.push({
            blocked: true,
            sourceType,
            message: `${label}计分内容已选择“加权平均”，要求每一项权重大于 0 且合计为 1（当前合计 ${weightText(total)}），请回到步骤 2 调整后保存。`,
            targetStep: STAGE_STEP
          });
        }
        continue;
      }

      issues.push({
        blocked: true,
        sourceType,
        message: `${label}计分内容的汇总方式无法用于综合成绩计算，请回到步骤 2 重新选择“算术平均”或“加权平均”。`,
        targetStep: STAGE_STEP
      });
    }

    if (!hasInvalidRule && Math.abs(ruleWeightTotal - 1) > WEIGHT_EPSILON) {
      issues.push({
        blocked: true,
        message: `综合评价表单各来源的表单层权重必须合计为 1（当前合计 ${weightText(ruleWeightTotal)}），请到评价表单页调整后发布新版本再绑定。`,
        targetStep: BINDING_STEP
      });
    }
  }

  return issues;
}

/**
 * 预检未通过时决定跳回的步骤。
 * 「阶段与内容」是唯一需要用户回退补数据的位置，存在该类问题时优先跳转，改完再回到绑定页。
 */
export function resolvePrecheckTargetStep(issues: CompositeBindingIssue[], fallbackStep = BINDING_STEP): number {
  const blocked = issues.filter((issue) => issue.blocked);
  if (!blocked.length) return fallbackStep;
  return blocked.some((issue) => issue.targetStep === STAGE_STEP) ? STAGE_STEP : BINDING_STEP;
}
