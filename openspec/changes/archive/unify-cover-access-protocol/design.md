# Design：统一封面写入字段与受控读取协议

长期设计已写入 `docs/architecture/cover-access-protocol.md`。本文件只记录本 Change 相对当时现状的收口决策。

## Context

存储已经统一：`us_sys_file_info` + 私有对象。乱的是门面。

Change 前：

| 对象   | 写入                 | 受控 GET                               | 学员列表                          |
|------|--------------------|--------------------------------------|-------------------------------|
| 课程   | `coverFileId`；专用上传 | `GET /course/courses/{id}/cover`     | 暴露 `coverFileId`              |
| 考试   | `coverFileId`      | `GET /exam/learner/exams/{id}/cover` | 无 `coverFileId`，也无 `coverUrl` |
| 培训类型 | `imageFileId`      | 无                                    | 学员任务无类型信息                     |

授权必须绑业务对象，禁止 `GET /api/files/{fileId}`。

## Goals / Non-Goals

**Goals:**

1. 对外字段名统一为 `coverFileId`（管理端写/详情）。
2. 读取统一为业务对象封面 GET，原始字节、不包 Result。
3. 学员列表只给业务主键 + `coverUrl`（无图为 `null`）。
4. 培训类型补齐管理端与学员两套受控 GET。

**Non-Goals:**

- 不改前端。
- 不迁移历史文件对象。
- 不改课程快照内部 `coverFileId`。
- 不为试卷/问卷新增 GET。
- 不改素材 HLS `coverUrl` 实现。

## Decisions

### D1 写入：`coverFileId`

培训类型表列 `image_file_id` 重命名为 `cover_file_id`。Java 实体字段 `coverFileId` 按默认驼峰映射该列。Create/Update/VO/OpenAPI
同步改名。管理端课程、考试、试卷、问卷保持现有 `coverFileId`。

### D2 读取：业务对象 GET

路径只有业务 ID。成功：`Content-Type` / `Content-Length` / `Content-Disposition` / `ETag`，不包 Result。`download=true` 为附件。

新增：

- `GET /api/training/types/{trainingTypeId}/cover`，权限 `training:type:list`。类型不存在 `404/8001`；无图或文件不可读
  `404/8164`。
- `GET /api/training/learner/plans/{trainingPlanId}/cover`，权限 `training:learner:task`。可见性与任务列表一致（本人参训且计划
  `RUNNING`/`ENDED`），否则 `404/8155`；可见但无图 `404/8164`。

既有课程、考试封面 GET 保持路径与错误码。

### D3 学员列表：`coverUrl`，禁止 `coverFileId`

相对路径，有封面才给：

- 课程：`/api/course/courses/{courseId}/cover`
- 考试：`/api/exam/learner/exams/{examId}/cover`
- 培训：`/api/training/learner/plans/{trainingPlanId}/cover`，并返回 `trainingTypeId`

无封面：`coverUrl=null`，列表 200。不要 `hasCover` 并列字段。

受影响 VO：`LearnerCourseEnrollmentVO`、`LearnerCourseOfferingVO`、`LearnerCourseTasksVO`、`CourseLearningCourseVO`、
`ExamLearnerTaskVO`、`TrainingLearnerTaskVO`。

### D4 打开文件方式

培训类型封面来自通用文件上传，不套课程 `course-cover` 对象名校验。打开方式对齐考试：按 `coverFileId` 读 `SysFileInfo`，再
`StorageService.download`。对象缺失映射为该域封面不存在，不泄漏存储路径。

`8164` 为培训封面缺失专用码，不复用考试 `7071` 或课程 `5092`。

## Risks / Trade-offs

- 培训类型字段改名会打破现有管理端请求体。由前端按新 OpenAPI 适配；本协作方不改页面。
- 课程学员列表去掉 `coverFileId` 同样是破坏性契约，这是本次明确要求。
- 课程封面 GET 仍按机构租户校验、无独立学员权限码，与现状一致；`coverUrl` 指向该路径。

## Migration Plan

1. init 完整 SQL `schema-training.sql`：`sts_training_type` 列写为 `cover_file_id`。
2. Admin Flyway `V20260908192000__rename_training_type_cover_file_id.sql`：`CHANGE COLUMN image_file_id cover_file_id`。
3. `sts.init.schema-version` 更新为 `20260908192000`。
4. 调用方按新 OpenAPI 改字段名。历史文件对象不迁移，列值原样保留。
