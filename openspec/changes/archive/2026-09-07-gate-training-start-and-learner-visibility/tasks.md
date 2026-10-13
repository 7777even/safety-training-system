# Tasks：培训计划准备完成门禁与学员可见性

> 状态：已确认（2026-09-07）。Owner：fxw11。

## Task 1：全部培训内容准备完成后才能开始计划

- **状态**：已完成（2026-09-07）
- **Owner**：fxw11
- **输入**：本 Change 的设计；计划内容、执行关联、线下场次和既有 `start` 生命周期接口。
- **输出**：开始计划仅在全部内容均有实际执行安排时进入 `RUNNING`；失败返回可定位的未安排内容。
- **前后端影响**：后端、OpenAPI、PC 管理端执行面板。
- **API 依赖**：`POST /api/training/plans/{trainingPlanId}/start`、执行关联和线下场次查询接口。
- **数据影响**：无；不改表结构和 migration。
- **验收标准**：
  1. 缺少任一课程、考试、题库练习或线下内容安排时，开始返回 `8162`，计划保持 `PUBLISHED`。
  2. 全部内容安排完成后，开始成功且计划进入 `RUNNING`。
  3. 管理端部分安排时只显示继续安排和失败处理；全部安排时只显示开始计划。
  4. 刷新页面后准备进度和可操作按钮仍与真实安排事实一致。
- **测试方式**：已先为 `TrainingPlanLifecycleServiceImpl.start` 增加非必修内容未安排的失败测试并观察 Red；最小实现后运行受影响零 DB 测试 8/8、PC 培训链路回归 22/22、定向 lint 和 OpenAPI JSON 解析。

## Task 2：学员仅在执行中或结束后看到培训计划

- **状态**：已完成（2026-09-07）
- **Owner**：fxw11
- **输入**：本 Change 的设计；学员任务、任务详情、进入内容接口和 PC/移动培训入口。
- **输出**：`PUBLISHED` 计划不向学员展示；`RUNNING` 可执行；`ENDED` 仅查看结果。
- **前后端影响**：后端、OpenAPI、PC 学员端、移动学员端。
- **API 依赖**：`GET /api/training/learner/tasks`、`GET /api/training/learner/participants/{trainingParticipantId}`、学员进入内容接口。
- **数据影响**：无。
- **验收标准**：
  1. 学员任务列表与详情仅返回 `RUNNING/ENDED`。
  2. `PUBLISHED` 计划详情返回 `8155`，不泄露编排内容。
  3. `RUNNING` 显示“查看并执行”，`ENDED` 显示“查看结果”，PC 与移动端一致。
  4. 进入内容仅允许 `RUNNING`。
- **测试方式**：已先扩展 `TrainingWorkflowServiceImplTest` 观察 Red；后端受影响零 DB 测试与 Task 1 合计 25/25，PC 培训链路回归 23/23、两端 typecheck、定向 lint 和 OpenAPI JSON 解析通过。

## Task 3：同步契约、验收记录和真实登录态验证

- **状态**：自动化完成，待真实登录态验收（2026-09-07）
- **Owner**：fxw11
- **输入**：Task 1、Task 2 的实现与测试结果。
- **输出**：同步 OpenAPI、QA、Retro 和进度记录；提供完整正向与失败验收夹具。
- **前后端影响**：OpenAPI、OpenSpec、engineering 记录；无运行时代码新增。
- **API 依赖**：培训计划开始、学员任务、学员详情和进入内容接口。
- **数据影响**：无。
- **验收标准**：
  1. OpenAPI JSON 可解析，路径、枚举、错误码、字段说明和 example 与实现一致。
  2. 覆盖“4/5 安排失败不可开始且学员不可见”“5/5 安排成功后开始、学员可执行”“结束后仅看结果”“取消后不可见”。
  3. 自动化、双轴 Review、真实登录态手动验收分别如实记录。
- **测试方式**：OpenAPI JSON 解析、目标测试、QA/Retro 和 `git diff --check` 已完成；真实非生产环境人工验收待执行。
