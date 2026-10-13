# Tasks：统一封面写入字段与受控读取协议

> 状态：已完成。Owner：lims。用户已确认协议与改库。

## 1. 完成培训类型封面字段与受控读取

- [x] 1.1 完成培训类型封面字段与受控读取

- **状态**：已完成
- **Owner**：lims
- **输入**：design D1、D2、D4；培训类型 CRUD 与学员任务列表。
- **输出**：对外 `coverFileId`；`GET /training/types/{id}/cover`；`GET /training/learner/plans/{id}/cover`；学员任务
  `trainingTypeId`、`coverUrl`；错误码 `8164`；同步 `docs/api/training.openapi.json`。
- **前后端影响**：仅后端；前端以 OpenAPI 为准另开。
- **API 依赖**：新封面 GET；类型写入字段改名；学员任务字段新增。
- **数据影响**：列重命名见 Task 4.1。实体映射 `cover_file_id`。
- **验收标准**：
  1. 创建/更新/详情使用 `coverFileId`，不再出现 `imageFileId`。
  2. 管理端按类型 ID 读取封面，成功为原始字节；无图 `404/8164`。
  3. 学员按可见计划 ID 读取封面；不可见 `404/8155`；可见无图 `404/8164`。
  4. 学员任务有封面时 `coverUrl` 为计划封面路径，无封面为 `null`，不含 `coverFileId`。
- **测试方式**：Red → Green 扩展类型与工作流零 DB；Controller 透传封面 GET。

## 2. 完成课程学员封面契约收口

- [x] 2.1 完成课程学员封面契约收口

- **状态**：已完成
- **Owner**：lims
- **输入**：design D3；课程学员 VO 与 OpenAPI。
- **输出**：`LearnerCourseEnrollmentVO`、`LearnerCourseOfferingVO`、`LearnerCourseTasksVO`、`CourseLearningCourseVO`
  以 `coverUrl` 替换 `coverFileId`；同步 `docs/api/course.openapi.json`。
- **前后端影响**：仅后端；前端以 OpenAPI 为准另开。
- **API 依赖**：既有 `GET /course/courses/{courseId}/cover`。
- **数据影响**：无。课程快照内部仍存 `coverFileId`。
- **验收标准**：
  1. 有封面时学员 VO `coverUrl=/api/course/courses/{courseId}/cover`。
  2. 无封面时 `coverUrl` 为 `null`。
  3. 管理端课程创建/详情仍使用 `coverFileId`。
- **测试方式**：Red → Green 扩展课程学员组装与阅读零 DB。

## 3. 完成考试学员任务封面入口

- [x] 3.1 完成考试学员任务封面入口

- **状态**：已完成
- **Owner**：lims
- **输入**：design D3；既有学员考试封面 GET。
- **输出**：`ExamLearnerTaskVO` 增加 `coverUrl`；同步 `docs/api/exam.openapi.json`。
- **前后端影响**：仅后端；前端以 OpenAPI 为准另开。
- **API 依赖**：既有 `GET /exam/learner/exams/{examId}/cover`。
- **数据影响**：无。
- **验收标准**：
  1. 有封面任务 `coverUrl=/api/exam/learner/exams/{examId}/cover`。
  2. 无封面任务 `coverUrl` 为 `null`，仍不返回 `coverFileId`。
- **测试方式**：Red → Green 扩展 `ExamLearnerServiceImplTest`。

## 4. 完成培训类型封面列重命名

- [x] 4.1 完成培训类型封面列重命名

- **状态**：已完成
- **Owner**：lims
- **输入**：用户确认改库；课程/考试列均为 `cover_file_id`。
- **输出**：init `schema-training.sql` 列名为 `cover_file_id`；Flyway
  `V20260908192000__rename_training_type_cover_file_id.sql`；`sts.init.schema-version=20260908192000`；实体去掉
  `@TableField("image_file_id")`。
- **前后端影响**：仅后端；前端以 OpenAPI 为准另开。
- **API 依赖**：无新接口。
- **数据影响**：`sts_training_type.image_file_id` 重命名为 `cover_file_id`，列值原样保留。
- **验收标准**：
  1. 空库 init 直接得到 `cover_file_id`，无 `image_file_id`。
  2. 已有库执行 V 后列名为 `cover_file_id`，原文件 ID 仍在该列。
  3. Entity `coverFileId` 读写映射到 `cover_file_id`。
- **测试方式**：Red → Green 扩展 `TrainingTypeMapperIT`；`FlywayMigrationIT` 升级路径断言列名与取值。

## 完成门槛

1. 规格符合性：无通用文件下载；学员列表无 `coverFileId`；培训错误码不复用 7071。
2. 代码质量：无前端、无新权限码；DDL 仅重命名培训类型封面列。
3. 契约：受影响 OpenAPI 满足四条铁律。
