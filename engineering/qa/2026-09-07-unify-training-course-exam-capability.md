# QA：开课与考试各是一套核心能力

## 范围

OpenSpec Change `unify-training-course-exam-capability` Task 1～4。仅后端与 `docs/api/course.openapi.json`、
`docs/api/training.openapi.json`、`docs/api/exam.openapi.json`；前端以 OpenAPI 为准另开。

## 验收口径

1. 开课发布：已有 `courseVersionId` 则沿用，否则取课程 current；缺阅卷人 5068 且开课保持草稿。培训生成创建草稿后调用同一套
   `publishOffering`，再 TRAINING 报名。
2. 考试发布：按考试字段物化执行；无 `planContentId` 为 STANDALONE_*，有则为 TRAINING_*。培训生成创建草稿后调用
   `ExamService.publish`，不再由 Adapter 直接标已发布。
3. 「我的考试」不含 `TRAINING_EXAM` / `TRAINING_PRACTICE`；按 `examAttemptId` 读取培训 Attempt 仍可用。
4. 管理端开课/考试分页默认同时含独立与培训生成对象；列表项与详情暴露 `planContentId`（独立为空、培训非空）；可按该字段过滤。

## TDD 与实现证据

1. Task 1：先扩展开课发布与培训 Adapter/Provisioner 零 DB（已有版本不取 current、已发布只再校验阅卷人、插入 DRAFT、5068
   不报名），再改
   `CourseOfferingServiceImpl`、`TrainingCourseExecutionAdapter`、`TrainingExecutionProvisioner`。
2. Task 2：`AssessmentExecutionServiceImpl.buildFromExam` 按 `planContentId` 推导来源；`ensureTraining` 删除；培训 Adapter
   插入草稿后调用
   `examService.publish`。
3. Task 3：先写「我的考试排除培训来源、Attempt 仍可读」零 DB，再过滤 `pageMyTasks`。
4. Task 4：先写开课/考试分页含培训对象、暴露并按 `planContentId` 过滤的零 DB，再补 VO/Query 与分页 `eq`，同步 OpenAPI。

## 验证结果

- 受影响零 DB（均 0 失败 / 0 错误 / 0 跳过）：
  - Task 1：`CourseOfferingServiceImplTest` 8、`TrainingCourseExecutionAdapterTest` 5、`TrainingExecutionProvisionerTest`
    8、
    `TrainingExecutionServiceImplTest` 7。
  - Task 2：`ExamServiceImplTest` 36、`AssessmentExecutionServiceImplTest` 6、`TrainingAssessmentExecutionAdapterTest` 6、
    `TrainingExecutionProvisionerTest` 8。
  - Task 3：`ExamLearnerServiceImplTest` 14、`ExamLearnerControllerTest` 3。
  - Task 4：`CourseOfferingServiceImplTest` 11、`ExamServiceImplTest` 39。
- 带 DB IT：
  - Task 1～2：`TrainingMixedPlanLifecycleIT` 2 通过（生成后开课与计划专属考试均为 PUBLISHED，执行来源 TRAINING_*）。
  - `DraftExamParticipantLifecycleIT` 4 通过。
  - Task 4：无表结构/SQL 变更，未跑 IT。
- Admin 全量零 DB：未运行，以受影响测试为准。
- OpenAPI：course 发布/阅卷人/开课分页与详情、training 生成执行、exam 发布与学员 tasks/attempts、考试分页与详情已同步。
- `git diff --check`：通过。
- OpenAPI JSON：`python3` 解析 `course.openapi.json`、`exam.openapi.json` 通过。

## Review

- 规格符合性：通过。发布无培训专用互斥分支；培训 Adapter 调用开课/考试 publish；学员入口是查询条件；管理列表是能力对象全集并暴露
  `planContentId`。
- 代码质量：通过。无前端、无表结构、无新权限码。培训 Adapter 仍直接 insert 草稿开课/考试（未改走 HTTP create），与 Task 1/2
  「调用发布」验收一致。
- 需人工决策：无。Change 四个 Task 均已完成，可归档。
