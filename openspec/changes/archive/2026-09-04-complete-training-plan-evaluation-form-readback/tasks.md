# Tasks：培训计划评价表单绑定读回闭环

> 状态：已完成（2026-09-04 已人工确认并验收）。Owner：lims。

## Task 1：完成培训计划评价表单绑定的保存与读回闭环

- 状态：已完成
- Owner：lims
- 输入：功能项 34、40；既有计划详情、评价表单整体绑定、不可变版本详情接口；本 Change Proposal、Design 和 Spec Delta。
- 输出：计划详情中的评价表单绑定摘要、正确的整体替换软删除、同步后的 `training.openapi.json` 和自动化测试。
- 前后端影响：仅后端；前端以 OpenAPI 为准另开。
- API 依赖：修改既有 `GET /api/training/plans/{trainingPlanId}` 响应；复用既有
  `PUT /api/training/plans/{trainingPlanId}/evaluation-forms` 和 `GET /api/evaluation/forms/versions/{formVersionId}`
  ；不新增路径或权限。
- 数据影响：不修改表、字段、索引或初始化数据；复用 `sts_training_plan_evaluation_form`、`sts_evaluation_form_version` 和
  `sts_evaluation_form`。
- 验收标准：
  - [x] 保存评价表单绑定后，重新查询计划详情可读回全部当前有效绑定。
  - [x] 每条绑定返回 `planEvaluationFormId`、`trainingPlanId`、`formVersionId`、`evaluationFormId`、`formName`、`versionNo`、
    `publishedStatus`、`usageType`、`isRequired` 和 `status`。
  - [x] 未绑定或清空绑定后返回 `evaluationForms: []`，不返回 `null`。
  - [x] 绑定摘要按 `usageType`、`planEvaluationFormId` 稳定排序；完整项目和评分规则继续通过既有版本详情接口获取。
  - [x] 重复保存同一用途时旧绑定写为 `deleted=plan_evaluation_form_id`，仅保留本次提交的有效绑定，不触发旧数据造成的唯一约束冲突。
  - [x] 计划版本冲突返回 409/8104，且不改变原绑定；任一版本未发布或不存在时事务回滚。
  - [x] 计划删除时绑定使用相同的主键值软删除语义。
  - [x] 计划查看权限只能读取当前可见计划已经绑定的摘要；完整版本详情继续要求 `evaluation:form:list`。
  - [x] 绑定指向的版本或表单异常缺失时返回 HTTP 500/8132，服务端记录计划 ID 与绑定 ID，不静默丢弃或返回残缺详情。
  - [x] `training.openapi.json` 与 Controller/VO 一致，新增字段均有中文说明和 example，计划详情成功响应包含有效绑定示例。
  - [x] 零 DB、相关 MySQL IT、OpenAPI 校验、OpenSpec strict 和 `git diff --check` 全部通过。
- 测试方式：按 TDD 先增加计划详情读回、空列表、重复整体替换、版本冲突和真库逻辑删除失败测试；随后完成最小实现；运行
  `mvn -pl sts-service-admin -am test` 的受影响目标测试及 `mvn -pl sts-service-admin -am -Pintegration-test test` 的相关
  IT。

## 完成门槛

1. 人工确认本 Change 后才能实施。
2. 实现后完成规格符合性 Review：严格对应本 Task、OpenAPI 和既有权限边界。
3. 实现后完成代码质量 Review：检查事务回滚、租户隔离、稳定排序、空集合、查询次数、逻辑删除和测试遗漏。
4. Review 通过后更新本文件状态，再更新进度表 TRAIN-04 的验收证据与行更新时间，并记录 QA / Retro。

## 验收记录

- TDD Red：新增计划详情读回测试因 `TrainingPlanVO` 缺少 `evaluationForms`、Service 缺少摘要装配依赖而编译失败。
- 零 DB：Admin 全量 863 个用例通过，0 失败、0 错误，1 个既有环境型用例跳过；目标服务 17 个用例通过。
- MySQL IT：`TrainingPlanServiceImplIT` 2 个用例通过，覆盖保存读回、重复替换、唯一约束、主键值软删除和失败回滚。
- Contract：OpenAPI JSON、引用及关键字段/example 断言通过；OpenSpec strict 与 `git diff --check` 通过。
- 规格符合性 Review：通过。
- 代码质量 Review：通过。
