# 封面访问设计

> 长期架构说明。业务对象封面的写入字段、受控读取和学员列表展示约定。
>
> 实施以已确认的 `openspec/changes/archive/unify-cover-access-protocol/` 为准。契约以 `docs/api/course.openapi.json`、
> `docs/api/exam.openapi.json`、`docs/api/training.openapi.json` 为准。
>
> 素材音视频 HLS 封面属于转码派生文件，见 `docs/architecture/asset-transcode-design.md`，不并入本协议。

## 1. 目的

封面文件已经统一存 `us_sys_file_info` 和私有对象。乱的是门面：有的域叫 `imageFileId`，有的列表把内部文件主键交给调用方，有的有受控
GET、有的没有。调用方拿到 fileId 也无法安全直读私有对象。

本文约束三件事：

1. **写入字段统一**：管理端创建、更新、详情用 `coverFileId`。
2. **读取绑业务对象**：按业务主键受控下载原始图片，不提供通用 `GET /api/files/{fileId}`。
3. **学员列表不暴露文件主键**：给业务主键和相对路径 `coverUrl`。

## 2. 存储与门面

| 层  | 职责                                 | 本设计是否改                                             |
|----|------------------------------------|----------------------------------------------------|
| 存储 | `us_sys_file_info` + 私有对象（MinIO 等） | 不迁移历史对象。培训类型列 `image_file_id` 重命名为 `cover_file_id` |
| 门面 | 字段名、授权路径、列表展示                      | 统一                                                 |

课程快照、考试资源快照等内部冻结 JSON 可以继续存 `coverFileId`。那是快照内部字段，不是学员列表契约。

## 3. 决策

### D1 写入：`coverFileId`

管理端创建/更新请求和详情 VO 一律使用 `coverFileId`，表示 `us_sys_file_info.file_info_id`。

培训类型表列为 `cover_file_id`，Java 实体与 OpenAPI 均为 `coverFileId`，与课程、考试、试卷、问卷对齐。

详情回传 `coverFileId` 只为编辑回写。展示图片必须走受控 GET，不得把 fileId 拼成对象 URL。

课程专用上传 `POST /api/course/covers` 仍可存在，它只负责把图片落入封面文件记录，再由课程对象引用 `coverFileId`。

### D2 读取：业务对象 GET，不包 Result

```text
GET /api/{域}/{业务对象}/{id}/cover?download=false
```

- 路径只有业务主键，不接受客户端传入 `fileInfoId`。
- 成功：原始图片字节，HTTP 200，不包 `Result`。响应头含 `Content-Type`、`Content-Length`、`Content-Disposition`、`ETag`。
- `download=false`（默认）为 `inline`；`download=true` 为 `attachment`。
- `Content-Type` 按文件后缀：`image/jpeg`、`image/png`、`image/webp`，其余为 `application/octet-stream`。
- 失败仍走统一异常处理：真实 HTTP 状态 + `Result` 外壳。这是成功路径的例外，不是失败路径的例外。见
  `docs/architecture/api-standard.md`。

授权必须能回答「当前用户能否看这个业务对象」。对象不可见时，返回该域「对象不存在」类 404，不泄漏是否有封面。可见但无图或文件不可读，返回该域「封面不存在」404。

禁止：

- `GET /api/files/{fileId}` 或任何只凭 fileId 读私有对象的接口。
- 把内部 `objectName`、bucket、预签名外链写进学员列表。

### D3 学员列表：`coverUrl`

学员任务、选课、学习摘要只给：

- 业务主键（`courseId` / `examId` / `trainingPlanId` 等）
- 相对路径 `coverUrl`

有封面：`coverUrl` 等于该对象受控 GET 路径。无封面：`coverUrl` 为 `null`，列表仍 200。不要并列 `hasCover`，不要返回
`coverFileId`。

`coverUrl` 是相对 API 路径，例如 `/api/course/courses/10/cover`。调用方带当前登录态请求该路径。

### D4 打开文件

按对象上的 `coverFileId` 读 `SysFileInfo`，再 `StorageService.download(objectName)`。对象缺失、空
objectName、存储读取失败，一律映射为该域封面不存在，不把存储路径写进错误消息。

课程封面上传仍校验 `course-cover` 对象名。培训类型封面来自通用文件上传，打开时不套课程对象名规则。

## 4. 当前路径与错误码

| 对象       | 写入                | 受控 GET                                                   | 授权                               | 对象不可见      | 无封面        |
|----------|-------------------|----------------------------------------------------------|----------------------------------|------------|------------|
| 课程       | 管理端 `coverFileId` | `GET /api/course/courses/{courseId}/cover`               | 当前机构课程                           | `404/5011` | `404/5092` |
| 考试       | 管理端 `coverFileId` | `GET /api/exam/learner/exams/{examId}/cover`             | 学员任务可见（已发布/进行中/已结束且本人 Candidate） | `404/7001` | `404/7071` |
| 培训类型     | 管理端 `coverFileId` | `GET /api/training/types/{trainingTypeId}/cover`         | `training:type:list`             | `404/8001` | `404/8164` |
| 培训计划（学员） | 类型上的封面            | `GET /api/training/learner/plans/{trainingPlanId}/cover` | 本人参训且计划 `RUNNING`/`ENDED`        | `404/8155` | `404/8164` |

学员列表 `coverUrl`：

| 入口                         | VO                                                                                                    | 有封面时的 `coverUrl`                                     |
|----------------------------|-------------------------------------------------------------------------------------------------------|------------------------------------------------------|
| 我的课程 / 可报名开课 / 学习任务 / 学习课程 | `LearnerCourseEnrollmentVO`、`LearnerCourseOfferingVO`、`LearnerCourseTasksVO`、`CourseLearningCourseVO` | `/api/course/courses/{courseId}/cover`               |
| 我的考试                       | `ExamLearnerTaskVO`                                                                                   | `/api/exam/learner/exams/{examId}/cover`             |
| 我的培训                       | `TrainingLearnerTaskVO`（另给 `trainingTypeId`）                                                          | `/api/training/learner/plans/{trainingPlanId}/cover` |

培训学员封面读的是计划所属**培训类型**的图，不是计划另存一份封面。路径用计划 ID，是因为学员授权绑在参训关系上，不能只拿
`trainingTypeId` 越权读类型封面。

各域封面缺失码不得混用：课程 `5092`、考试 `7071`、培训 `8164`。

## 5. 调用约定

管理端编辑：

1. 详情读取 `coverFileId` 回填。
2. 预览走对应业务对象封面 GET。
3. 保存只提交 `coverFileId`。空表示清除或未设置，语义以各域 OpenAPI 为准。

学员展示：

1. 列表/任务用 `coverUrl`。为 `null` 则走无封面占位，不要再请求封面 GET。
2. 有 `coverUrl` 时用当前登录态 GET 该相对路径，按 `Content-Type` 显示图片。
3. 不要缓存或拼接 fileId。

## 6. 非目标

- 不提供通用文件下载。
- 不改培训类型 DDL 列名，不迁移历史对象键。
- 不把素材 HLS/派生封面并进同一 Controller。素材 `coverUrl` 仍是受控媒体路径，见转码设计。
- 试卷、问卷管理端写入已用 `coverFileId`。本期不为它们新增受控 GET；需要展示时另开 Change，路径形态仍须符合 D2。
- 不改前端页面。调用方以 OpenAPI 为准另开。

## 7. 相关文档

- 契约：`docs/api/course.openapi.json`、`docs/api/exam.openapi.json`、`docs/api/training.openapi.json`
- Result 包装与例外：`docs/architecture/api-standard.md`
- 培训类型职责：`docs/architecture/training-layer-responsibility.md`
- 学员三入口：`docs/architecture/training-course-exam-capability.md`
- Change：`openspec/changes/archive/unify-cover-access-protocol/`
