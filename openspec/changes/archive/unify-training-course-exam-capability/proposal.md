# Proposal：开课与考试各是一套核心能力，培训只是调用方

## Why

培训不是「另一套开课/考试」，也不是在原流程上打 `if (培训)` 补丁。开课能力和考试能力各只有一个核心：草稿 → 配置 → 发布 →
人员。独立管理端和培训计划生成是两个调用方，传入不同的版本、时间和名单。当前培训生成旁路开课/考试发布，自己插入已发布对象。正确做法是调用同一套创建、发布和人员接口。

架构说明见 `docs/architecture/training-course-exam-capability.md`。

## What Changes

- **开课能力唯一**：创建草稿、配测验阅卷人、发布、报名。发布规则只有一条——开课已有 `courseVersionId`
  就用它，没有则取课程当前版本。培训生成调用这套能力（创建时带上冻结版本），不再自己 `insert PUBLISHED`。
- **考试能力唯一**：创建草稿、配策略/阅卷人、发布、绑考生。发布时按考试自身字段物化 `AssessmentExecution`（来源、试卷/题库、策略），不再分
  `refreshStandalone` / `ensureTraining` 两条发布。培训生成调用这套能力（创建时带上 `planContentId` 和冻结快照）。
- 管理端开课列表、考试列表就是该能力的列表；培训生成的对象自然在其中。`planContentId` 是普通字段，可过滤，不是平行产品入口。
- 学员「我的课程 / 我的考试 / 我的培训」是三个入口，读同一套报名/考生事实，各自带来源条件。
- 报名/绑人带 `source`。培训生成调用开课报名、考试绑人。开课可约束允许的报名来源（指派开课只接受 ASSIGN，培训开课只接受
  TRAINING）。

## Capabilities

### New Capabilities

- （无）

### Modified Capabilities

- `training-execution-loop`：培训生成改为调用开课/考试核心能力，不得旁路发布。
- `training-course-offering-lifecycle`：培训课先有草稿开课，再配阅卷人，再发布，再报名。
- `learner-exam-reading`：「我的考试」入口只读独立考试来源；培训考试从「我的培训」进入。
- `learner-course-reading`：「我的课程」入口只读独立开课报名；培训课从「我的培训」进入。
- `course-assessment-source-separation`：课程内测验与计划级考试仍是两套考核对象；阅卷人配在开课/考试草稿上，与谁调用无关。

## Impact

- 课程：`CourseOfferingService` 成为培训生成的创建/发布/报名入口；Adapter 不再直接标已发布。
- 考试：`ExamService.publish` 与执行物化合成一条；Adapter 调用 create/publish/bind。
- 培训：`TrainingExecutionProvisioner` 只编排调用顺序。
- 契约：`docs/api/course.openapi.json`、`docs/api/exam.openapi.json`、`docs/api/training.openapi.json`。
- 无表结构变更。无新权限码。
- 仅后端；前端以 OpenAPI 为准另开。

## 功能项追溯

- **功能项 16**：考试任务有人员、阅卷人、时间；培训生成的是一次考试任务，用同一套考试能力。
- **功能项 17**：主观题阅卷人配在开课或考试上，发布前必须齐。
- **功能项 22**：课程章节测验/题库是课程能力，随开课走。
- **功能项 48**：我的课程、我的考试、我的培训是三个学员入口。
- **功能项 56**：我的培训进入后仍用课程学习/考试作答能力。

## 目标

1. 开课发布、考试发布各只有一条实现，两个调用方共用。
2. 培训生成的开课/考试就是普通开课/考试，能在管理端用原接口配置阅卷人、查看详情。
3. `PUBLISHED` 只表示该开课/考试已发布。
4. 学员三个入口读同一事实、不同来源条件。

## 非目标范围

- 不改前端、Mock、联调。
- 不把课程章节测验合并成计划 Exam，也不把计划考试改成章节。
- 不在计划内容行上另做阅卷人 API。
- 不回迁存量已发布培训开课为草稿。
- 不改线下、档案、证书。
