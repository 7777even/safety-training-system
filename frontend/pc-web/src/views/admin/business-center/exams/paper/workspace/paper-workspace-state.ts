/** 试卷工作台的固定阶段，按创建、组卷、发布的业务顺序定义。 */
export type PaperWorkspaceStage = 'basic' | 'compose' | 'publish';

/** 保存基础信息或明确确认进入发布后的唯一允许流转，避免页面自行拼接阶段字符串。 */
export type PaperWorkspaceTransition = 'basic-saved' | 'preview-and-publish';

/** 预览按钮对应的两种用户意图：仅查看，或查看后进入发布确认。 */
export type PaperWorkspacePreviewAction = 'preview-only' | 'preview-and-publish';

/** 未获得服务端 paperId 前只能填写基础信息，后续阶段不允许发起业务请求。 */
export function canEnterPaperWorkspaceStage(paperId: number, stage: PaperWorkspaceStage): boolean {
  return stage === 'basic' || paperId > 0;
}

/** 根据已经成功完成的工作流动作确定下一阶段。 */
export function getPaperWorkspaceNextStage(transition: PaperWorkspaceTransition): PaperWorkspaceStage {
  return transition === 'basic-saved' ? 'compose' : 'publish';
}

/** 根据预览意图确定阶段：普通预览留在当前阶段，明确确认才进入发布。 */
export function getPaperWorkspaceStageAfterPreview(
  currentStage: PaperWorkspaceStage,
  action: PaperWorkspacePreviewAction
): PaperWorkspaceStage {
  return action === 'preview-and-publish' ? getPaperWorkspaceNextStage(action) : currentStage;
}
