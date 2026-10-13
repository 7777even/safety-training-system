# Tasks：开课与考试各是一套核心能力

> 状态：已完成。Owner：lims。

## 1. 完成开课发布一条规则并供培训生成调用

- [x] 1.1 完成开课发布一条规则并供培训生成调用

- **状态**：已完成
- **Owner**：lims
- **输入**：design D1；`CourseOfferingService.publishOffering`、`TrainingCourseExecutionAdapter`。
- **输出**：发布使用「已有 `courseVersionId` 则用已有，否则取 current」；培训生成创建草稿并调用该发布再 TRAINING 报名；5068
  保持草稿；同步 OpenAPI。
- **前后端影响**：仅后端；前端以 OpenAPI 为准另开。
- **API 依赖**：开课发布与阅卷人接口不新增培训专用路径；生成失败项仍带 `offeringId`。
- **数据影响**：无表结构变更。
- **验收标准**：
  1. 独立开课发布仍取 current 版本。
  2. 培训开课创建时写入冻结版本，发布不得改写。
  3. 缺阅卷人时开课保持 DRAFT，返回 5068。
  4. 配齐后重试走同一 `publishOffering` 再报名。
  5. Adapter 不再把新开课直接标为 PUBLISHED。
- **测试方式**：Red → Green 扩展开课发布与培训生成零 DB；`TrainingMixedPlanLifecycleIT`。

## 2. 完成考试发布按考试字段物化执行

- [x] 2.1 完成考试发布按考试字段物化执行

- **状态**：已完成
- **Owner**：lims
- **输入**：design D2；`ExamService.publish`、执行建造、`TrainingAssessmentExecutionAdapter`。
- **输出**：独立考试与计划专属考试共用 `publish`；`sourceType` 由 `planContentId`/`examType` 推导；培训生成调用
  create/publish/bind；同步 OpenAPI。
- **前后端影响**：仅后端；前端以 OpenAPI 为准另开。
- **API 依赖**：不新增培训专用发布 HTTP。
- **数据影响**：无。
- **验收标准**：
  1. 独立考试发布后仍为 STANDALONE_*。
  2. 计划专属考试发布后为 TRAINING_*，且经过 publish，而非 Adapter 改状态。
  3. 考生在发布后通过绑人能力写入。
- **测试方式**：Red → Green 扩展 `ExamServiceImplTest`、`TrainingAssessmentExecutionAdapterTest`。

## 3. 完成学员考试入口按来源查询

- [x] 3.1 完成学员考试入口按来源查询

- **状态**：已完成
- **Owner**：lims
- **输入**：design D4；`ExamLearnerServiceImpl.pageMyTasks`。
- **输出**：「我的考试」只含独立考试来源；培训 Attempt 作答仍可用；同步 exam OpenAPI。
- **前后端影响**：仅后端；前端以 OpenAPI 为准另开。
- **API 依赖**：保持 tasks 路径，更新 description 为入口口径。
- **数据影响**：无。
- **验收标准**：
  1. 培训来源任务不出现在我的考试。
  2. 独立考试仍出现。
  3. 培训 enter 的 Attempt 可作答。
- **测试方式**：Red → Green 扩展学员考试读取零 DB。

## 4. 完成开课与考试列表作为能力对象列表

- [x] 4.1 完成开课与考试列表暴露 planContentId 且默认包含培训对象

- **状态**：已完成
- **Owner**：lims
- **输入**：design D3；开课/考试分页与详情 VO。
- **输出**：列表默认含培训生成对象；VO 暴露 `planContentId`；可按该字段过滤；同步 OpenAPI。
- **前后端影响**：仅后端；前端以 OpenAPI 为准另开。
- **API 依赖**：现有分页；字段与可选过滤。
- **数据影响**：无。
- **验收标准**：
  1. 默认列表同时含独立与培训生成对象。
  2. 培训对象 `planContentId` 非空，独立对象为空。
  3. 按 `planContentId` 可过滤。
- **测试方式**：Red → Green 扩展开课/考试分页与详情零 DB。

## 完成门槛

1. 规格符合性：发布无培训专用互斥分支语义；列表不藏对象；学员入口是查询条件。
2. 代码质量：无前端；无表结构变更。
