# QA：统一封面写入字段与受控读取协议

## 范围

OpenSpec Change：`unify-cover-access-protocol`。

覆盖培训类型 `coverFileId`、类型/学员计划受控封面 GET、课程学员 VO 用 `coverUrl` 替换 `coverFileId`、考试学员任务增加
`coverUrl`，以及 `docs/api/training.openapi.json`、`course.openapi.json`、`exam.openapi.json`。用户确认后将
`sts_training_type.image_file_id` 重命名为 `cover_file_id`。未改权限码或前端。

## 自动化验证

- 后端零 DB（Task 1～3）：
  `mvn -pl sts-service-admin -am '-Dtest=TrainingTypeServiceImplTest,TrainingTypeControllerTest,TrainingWorkflowServiceImplTest,TrainingWorkflowControllerTest,TrainingWorkflowControllerWebSliceTest,CourseLearnerReadingServiceImplTest,LearnerCourseResourceAssemblerTest,ExamLearnerServiceImplTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`
  → 62 项通过，0 失败、0 错误、0 跳过。
- 后端零 DB（Task 4.1 回归）：
  `mvn -pl sts-service-admin -am '-Dtest=TrainingTypeServiceImplTest,TrainingTypeControllerTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`
  → 8 项通过，0 失败、0 错误、0 跳过。
- `docs/api/training.openapi.json`、`course.openapi.json`、`exam.openapi.json` JSON 解析通过。
- `git diff --check` 通过。

## 覆盖场景

1. 培训类型创建写入 `coverFileId`；管理端按类型 ID 读取封面为原始字节；无图 `404/8164`。
2. 学员任务仅 RUNNING/ENDED；有封面时 `coverUrl=/api/training/learner/plans/{id}/cover`，无封面为 `null`。
3. 学员按可见计划读取封面；不可见 `404/8155` 且不读存储。
4. 我的课程/学习任务 `coverUrl=/api/course/courses/{courseId}/cover`，不再返回 `coverFileId`。
5. 我的考试任务有封面时 `coverUrl=/api/exam/learner/exams/{examId}/cover`。
6. 培训类型封面列 `cover_file_id`：Mapper 写入/回读一致；已有库升级保留原文件 ID；空库 init baseline `20260908192000`。

## MySQL IT

在 `backend/` 执行：

-
`mvn -pl sts-service-admin -am -Pintegration-test '-Dtest=TrainingTypeMapperIT,FlywayMigrationIT' '-Dsurefire.failIfNoSpecifiedTests=false' test`
→ `TrainingTypeMapperIT` 3 项、`FlywayMigrationIT` 3 项，全部通过。
-
`mvn -pl sts-service-admin-init -am -Pintegration-test '-Dtest=AdminInitFlywayIT' '-Dsurefire.failIfNoSpecifiedTests=false' test`
→ `AdminInitFlywayIT` 1 项通过，baseline `20260908192000`。

## 真实登录态待验收

需要在非生产环境由前端按 OpenAPI 对接后验证：

1. 管理端类型详情用 `coverFileId` 编辑，展示走类型封面 GET。
2. 学员培训/课程/考试列表用 `coverUrl` 加载图片，不再拼 fileId。
3. 不可见培训计划封面返回 8155，无图返回 8164。
