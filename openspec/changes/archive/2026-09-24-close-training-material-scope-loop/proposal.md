# Proposal：培训资料范围收口与学员侧只读

## 状态

- **已实施完成（Task 1～6，2026-09-24）。** 2026-09-24 已由文祥确认六项口径：
  1. **奖惩佐证收口到奖惩记录**：培训资料里去掉「奖惩佐证」类型，奖惩佐证统一挂在奖惩记录上，并在档案的奖惩记录里能看、能预览；
  2. **停用资料不进档案**，行为与直觉一致；
  3. 学员端**只开放「一般资料」（GENERAL）**；
  4. 资料**支持在线预览**；
  5. **新增学员侧只读端点**承载学员查看；
  6. **停用资料学员端不可见**。
- 验证证据：`engineering/qa/2026-09-24-close-training-material-scope-loop.md`；
  复盘 `engineering/retro/2026-09-24-close-training-material-scope-loop.md`。
- 功能项依据：中山 34（培训人员及附件资料上传）、36（支持上传培训过程中产生的影像、培训附件、奖惩评价、综合评价等资料）、
  44（档案详情与一期一档均须包含「奖惩、影像记录、附件」）、48（员工学习端 > 课程培训 > 培训计划：
  「培训计划内容按阶段进行展示，点击阶段内容可进行学习，**支持查看已上传的附件资料等内容**」）。
- 本 Change 只做**资料范围（类型 + 可见性 + 状态口径）与学员侧只读**；
  档案文件格式、综合分口径、证书签发语义与按阶段重组学员视图**不在本 Change**（见「非目标」）。

## 现状与问题

1. **奖惩佐证双通道，且档案侧无处可看。** 奖惩记录自带 `evidenceFileId`（`GET /api/training/reward-penalties/{rewardPenaltyId}/evidence`
   已实现并支持预览），培训资料又有一个 `REWARD_PENALTY`（奖惩佐证）类型——同一件佐证存在两个上传口。
   同时档案三个视角共用 `ArchiveFactsSections.vue`，其奖惩表只有「类型 / 标题 / 分数变动 / 发生日期 / 说明」，
   **没有佐证列**；`getTrainingRewardEvidence` 全前端只有计划工作台在调用。后端 `ArchiveServiceImpl:480` 已经返回了
   `evidenceFileId`，是「数据有、页面没呈现」。
2. **停用资料口径与直觉相反。** 档案取奖惩是 `status = 1`（`ArchiveServiceImpl:318`），但取资料**完全不过滤 `status`**
   （`ArchiveServiceImpl:319-320`，归档素材登记 `:607-611` 同样不过滤）。管理员在计划页把误传的图片「停用」后，
   它在档案的「影像记录」与「附件清单」里依然出现。契约对资料 `status` 的说明是「0 禁用，禁用后不参与新的引用和选择」，
   停用即不应再作为可读事实呈现。
3. **学员完全看不到资料。** 学员端 PC 门户 `views/portal/training/detail.vue` 与移动端 `pages/training/*` 一个字都没有；
   `TrainingLearnerParticipantDetailVO` 只有 `participant / plan / contents / results`，没有资料；
   资料读取端点（`/api/training/attachments`、`/{id}/file`）全部要求管理端 `training:attachment:list`。
   功能项 48 明确要求学员可查看已上传的附件资料，属**未覆盖需求**（`2026-09-24-close-training-material-loop`
   的 proposal / design / spec-delta 从头到尾没有出现「学员」，既未实现也未登记延期）。
4. **类型枚举四处分散。** `REWARD_PENALTY` 同时出现在服务端校验集合（`TrainingAttachmentServiceImpl:44`）、
   契约枚举与描述（`docs/api/training.openapi.json`、`docs/api/archive.openapi.json`）、
   DDL 列注释（`schema-training.sql:977`）与前端三处（`constants/enum-options.ts`、`api/training-process.ts`、
   `PlanProcessResultsPanel.vue`），收口必须四类资产同步，否则出现「契约允许、服务端拒绝」的裂缝。

## 目标与边界

1. 培训资料类型收敛为 `GENERAL`、`IMAGE`、`VIDEO`、`EVALUATION` 四类；「奖惩佐证」不再作为资料类型可选。
2. 奖惩佐证只走奖惩记录的 `evidenceFileId`；档案的奖惩表提供佐证预览入口，复用既有受控端点，不复制文件、不新增存储。
3. 档案与学员侧读取资料一律只取 `status = 1`；管理端工作台保留停用资料的展示与维护（可改回启用）。
4. 新增学员侧只读端点：只返回当前登录学员**本人**参训计划下、类型为 `GENERAL`、且 `status = 1` 的资料；
   文件经受控端点**按记录**读取，不暴露可直接访问的文件地址。
5. 学员端 PC 门户与移动端培训详情展示「培训资料」并支持**在线预览**；学员侧**不提供下载**入口。
6. 不新增权限（沿用 `training:learner:task`）、不新增业务表、不改综合分与证书签发语义。

## 非目标

- **不动评价表单的成绩来源枚举**：`REWARD_PENALTY` 这个字面量在两处互不相关的枚举里都存在——
  (a) `TrainingAttachmentType`（培训资料类型，本 Change 收口对象）；
  (b) `EvaluationSourceType`（评价表单「成绩来源类型」，`REWARD_PENALTY` = 「奖惩记录」，`docs/api/evaluation-form.openapi.json`、
  `evaluation-form/workspace`、`evaluation-form/version`、`PlanEvaluationPanel.vue`、`api/evaluation-form.ts`）。
  (b) 与资料类型同名但**语义无关**，MUST 保持不变；实施时只允许改动指向 `TrainingAttachmentType` 的四处资产。
- 不把学员侧「培训内容 + 资料」按阶段重组展示（功能项 48 的「按阶段进行展示」另一半），另开 Change。
- 不向学员开放 `IMAGE` / `VIDEO` / `EVALUATION` 资料；学员侧一律不开放下载。
- 不做影像转码、派生文件与媒体播放器（属 M5 之后独立 Change）。
- 不恢复签到口径，不改档案文件格式；仅因过滤条件变化而同步统计数值。

## 实施顺序

- Task 1：后端资料类型收口与既有数据迁移（含契约、DDL 注释与 schema 版本同步）。
- Task 2：后端档案口径统一（资料只取启用）+ 档案奖惩事实补佐证可读性。
- Task 3：后端学员侧只读端点（列表 + 受控文件）。
- Task 4：PC 前端三处（工作台类型选项、档案奖惩佐证列、门户培训资料区块）。
- Task 5：移动端培训详情培训资料区块。
- Task 6：纵向验收、双轴 Review、QA、Retro 与状态同步。
