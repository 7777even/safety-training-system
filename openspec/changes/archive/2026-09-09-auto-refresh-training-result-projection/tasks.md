# Tasks：来源事实写入时自动刷新培训内容结果投影

> 状态：已完成。Owner：lims。仅后端；前端以 OpenAPI 为准另开。

## Task 1：完成单行投影幂等刷新

- [x] 1.1 完成单行投影幂等刷新

- **状态**：已完成
- **Owner**：lims
- **输入**：design D1；既有 `upsertProjection` / `readDraft` / `applyAggregates`。
- **输出**：`synchronizeParticipantContent(trainingParticipantId, planContentId)`；`TrainingResultRefresh`；冻结跳过；错误内容
  400/8120。
- **前后端影响**：仅后端；前端以 OpenAPI 为准另开。
- **API 依赖**：无新路径。
- **数据影响**：无 DDL。
- **验收标准**：
  1. 刷新指定内容项并重建该参训人聚合。
  2. 已冻结参训人不更新投影。
  3. 内容不属于该计划返回 400/8120。
  4. 字段未变不递增 `resultRevision`。
- **测试方式**：Red → Green 扩展 `TrainingResultSyncServiceImplTest`。

## Task 2：完成课程与考试写路径自动刷新

- [x] 2.1 完成课程与考试写路径自动刷新

- **状态**：已完成
- **Owner**：lims
- **输入**：design D2、D3。
- **输出**：课程学习状态变化调用刷新；培训考生开考/交卷/成绩发布发布同步事件并由监听器刷新；独立开课/考试不触发。
- **前后端影响**：仅后端；前端以 OpenAPI 为准另开。
- **API 依赖**：无新路径。
- **数据影响**：无。
- **验收标准**：
  1. 培训报名 `NOT_STARTED→LEARNING` 触发刷新；仅秒数变化不触发。
  2. 培训考生交卷触发刷新；无 `trainingParticipantId` 不触发。
  3. 成绩发布对培训考生触发刷新。
  4. 禁止异步或 after-commit 监听。
- **测试方式**：Red → Green 扩展 `CourseLearningServiceImplTest`、`ExamAttemptServiceImplTest` 与监听器零 DB。

## Task 3：完成线下写路径自动刷新并同步契约说明

- [x] 3.1 完成线下写路径自动刷新并同步契约说明

- **状态**：已完成
- **Owner**：lims
- **输入**：design D2、D4。
- **输出**：确认签到与保存线下成绩调用刷新；OpenAPI 将计划级同步标为补账；学员详情/结果说明只读已刷新投影。
- **前后端影响**：仅后端；前端以 OpenAPI 为准另开。
- **API 依赖**：既有结果同步与查询路径，仅改 description。
- **数据影响**：无。
- **验收标准**：
  1. 教师确认签到后调用单行刷新。
  2. 教师保存线下成绩后调用刷新。
  3. OpenAPI 不再把人工同步写成看见结果的唯一途径。
- **测试方式**：Red → Green 扩展签到/成绩零 DB；`git diff --check`。

## 完成门槛

1. 规格符合性：写路径刷新；读路径不懒同步；无新 API/权限/DDL。
2. 代码质量：考试不注入 SyncService 造成循环依赖；无前端改动。
