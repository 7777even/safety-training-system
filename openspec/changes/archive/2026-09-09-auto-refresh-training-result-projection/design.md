# Design：来源事实写入时自动刷新培训内容结果投影

## Context

M3 已建立 `sts_training_participant_content_result` 和 `synchronize(planId)`。生成执行时只 `ensurePending`；学员详情和结果
GET 只读投影。课程完成、考试交卷、线下确认都不调用同步，内容项一直保持 `PENDING`。

课程域不得直接写培训 Mapper；考试 `ExamAttemptServiceImpl` 已被 `TrainingAssessmentExecutionAdapter` 依赖，若再注入
`TrainingResultSyncService` 会形成 Spring 循环依赖。

## Goals / Non-Goals

**Goals:**

- 来源事实与对应投影在同一事务提交。
- 刷新粒度是一名参训人的一个内容项，并重算该人聚合字段。
- 独立开课/独立考试不碰培训投影。
- 管理员全量同步与 `end` 仍可用。

**Non-Goals:**

- 读路径懒同步。
- 学时口径、首次作答、缺勤折算、应到名单、场次状态机、监管下钻。
- 内容完成后提前写参训人 `PASSED/FAILED`。

## Decisions

### D1：单行入口复用既有投影算法

`TrainingResultSyncService.synchronizeParticipantContent(trainingParticipantId, planContentId)` 复用
`upsertProjection` / `readDraft` / `applyAggregates`。

- 参训人已 `resultFinalizedTime`：直接返回。
- 内容必须属于该参训人所属计划，否则 400/8120。
- 只 upsert 这一行，再按该人全部投影行重建 `onlineHours` / `offlineHours` / 分项均分。
- 同源且字段未变时不递增 `resultRevision`。
- 不修改参训人最终结论字段。

不选每次调用 `synchronize(planId)`：一次交卷会扫全计划所有人。

### D2：写路径触发，读路径只读

| 来源    | 触发点                                                                  | 定位                                                                                                      |
|-------|----------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| 课程    | `recomputeEnrollment` 后学习状态变化（`NOT_STARTED→LEARNING` 或 `→COMPLETED`） | `enrollment.trainingParticipantId` + `offering.planContentId`                                           |
| 考试/题库 | 培训考生开考、交卷终态、成绩发布                                                     | `candidate.trainingParticipantId` + `exam.planContentId`（否则 `candidate.sourceBusinessId`；`null`/`0` 不发） |
| 线下    | 确认签到、upsert 已评分成绩                                                    | 请求/记录中的参训人 + `session.planContentId`                                                                    |

课程心跳只改秒数、状态不变时不刷新，避免学习上报把投影写热。

不选读时同步：监管/后台不打开学员页时仍旧；也会掩盖漏挂钩。

### D3：考试用同步领域事件，课程/线下直接调用

- 考试：`ExamAttemptServiceImpl` 发布 `TrainingParticipantContentFactChangedEvent`，培训监听器在同一线程调用
  `synchronizeParticipantContent`。默认同步事件会加入当前事务；禁止 `@Async` 和 `AFTER_COMMIT`。
- 课程：直接调用 `TrainingResultRefresh`（由 SyncService 实现）。课程学习不依赖考试 Adapter，无循环依赖。
- 线下确认/成绩：同域直接调用接口。

`trainingParticipantId` 或 `planContentId` 为空则不发事件、不调用。

刷新失败必须回滚来源事实，避免交卷成功而投影仍 `PENDING`。

### D4：契约只改说明

`POST /training/plans/{id}/results/synchronize` 说明改为全量补账/重算，不作为学员闭环前置。学员参训详情与结果 GET
说明：来源事实写入后投影已刷新，查询不触发同步。无新路径、无新错误码、无 DDL。

## Risks / Trade-offs

- 漏挂钩仍会 `PENDING` → 用写路径零 DB 断言事件/刷新调用；全量同步保留补账。
- 考试事件监听器抛错回滚交卷 → 接受；投影写入应轻量。
- 课程完成同时从 `NOT_STARTED` 到 `COMPLETED` 只发一次 → 可接受。
- 历史已交卷仍 `PENDING` 的行不会自动回放 → 管理员全量同步一次即可。

## Migration Plan

无需 migration。部署后新事实自动刷新；存量用一次 `results/synchronize`。回滚即去掉写路径调用，行为回到人工同步。

## Open Questions

无。学时口径、首次作答等明确延期。
