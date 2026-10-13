# Spec Delta：cover-access-protocol

## ADDED Requirements

### Requirement: 封面受控读取绑定业务对象

系统 MUST 以 `GET /api/{域}/{业务对象}/{id}/cover` 读取封面。路径 MUST 只含业务主键，MUST NOT 接受客户端传入
`fileInfoId`。成功 MUST 返回原始图片字节及 `Content-Type`、`Content-Length`、`Content-Disposition`、`ETag`，MUST NOT 包
Result。无封面或文件不可读 MUST 返回该域 404。

#### Scenario：学员按计划读取类型封面

- **GIVEN** 当前用户是该计划参训人且计划为 RUNNING 或 ENDED，类型已关联可读封面文件
- **WHEN** 调用 `GET /api/training/learner/plans/{trainingPlanId}/cover`
- **THEN** 返回图片字节且不包 Result

#### Scenario：不可见计划隐藏为任务不存在

- **WHEN** 计划对当前学员不可见（含未参训、DRAFT、PUBLISHED、CANCELLED、ARCHIVED）
- **THEN** 返回 404/`8155`
- **AND** 不读取存储对象

#### Scenario：可见计划没有封面

- **GIVEN** 计划对当前学员可见但类型未关联封面或文件不可读
- **WHEN** 调用学员计划封面 GET
- **THEN** 返回 404/`8164`

### Requirement: 学员列表用 coverUrl 而不暴露 coverFileId

学员课程、考试、培训列表与任务读模型 MUST 使用相对路径 `coverUrl`，MUST NOT 返回 `coverFileId`。无封面时 `coverUrl` MUST 为
`null`。

#### Scenario：有封面的培训任务

- **GIVEN** 可见计划绑定的培训类型有封面文件
- **WHEN** 查询 `GET /api/training/learner/tasks`
- **THEN** 返回 `trainingTypeId` 与 `coverUrl=/api/training/learner/plans/{trainingPlanId}/cover`

#### Scenario：课程学员列表不再返回文件主键

- **WHEN** 查询我的课程、可报名开课、学习任务或学习课程详情
- **THEN** 有封面时 `coverUrl` 为 `/api/course/courses/{courseId}/cover`
- **AND** 响应不含 `coverFileId`

#### Scenario：考试任务给出封面入口

- **GIVEN** 独立考试有封面且当前学员可见
- **WHEN** 查询我的考试任务
- **THEN** `coverUrl` 为 `/api/exam/learner/exams/{examId}/cover`

## MODIFIED Requirements

### Requirement: 培训类型对外封面字段为 coverFileId

培训类型创建、更新、详情 MUST 使用 `coverFileId` 表示封面文件主键。MUST NOT 再使用 `imageFileId`。表列 MUST 为
`sts_training_type.cover_file_id`。管理端 MUST 提供 `GET /api/training/types/{trainingTypeId}/cover`。

#### Scenario：创建类型写入封面

- **WHEN** 创建培训类型并传入 `coverFileId`
- **THEN** 详情返回同一 `coverFileId`
- **AND** 该值持久化到 `sts_training_type.cover_file_id`
- **AND** 管理端封面 GET 可读取该文件
