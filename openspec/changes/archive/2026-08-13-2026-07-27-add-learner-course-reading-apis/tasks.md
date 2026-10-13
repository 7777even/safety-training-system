# Tasks：学员课程入口与学习任务读取接口

> 状态：待人工确认。确认前不得修改业务代码或 API Contract。
>
> Owner：lims

## Task 1：完成学员课程入口读取能力

- 状态：已完成（2026-07-27）
- Owner：lims
- 输入：确认后的 proposal/design；现有开课、报名、课程版本快照与参与范围事实
- 输出：“我的课程”和“学员可报名开课”分页接口、独立 Query/VO、Service/Mapper 查询及
  `docs/api/course.openapi.json` 对应契约
- 前后端影响：仅后端；前端以 OpenAPI 为准另开
- API 依赖：新增 `GET /course/learning/my-enrollments`、
  `GET /course/learning/offerings`
- 数据影响：只读既有课程、开课、版本、报名、分类和参与范围数据；不改数据库
- 验收标准：
  - [x] “我的课程”只返回当前机构、当前登录人的报名，不接受或信任外部 `userId`
  - [x] 支持 `pageNum`、`pageSize` 和 `query.learningStatus`
  - [x] 列表字段完整返回确认后的契约字段，课程展示信息来自冻结版本快照
  - [x] 可报名列表支持关键字、课程分类、开课状态和分页
  - [x] 只返回已发布、启用、有效期内、允许当前用户自主报名的 OPEN/LINK 开课
  - [x] 配置参与范围时校验当前用户属于该范围；已有报名的开课不返回
  - [x] 不虚构剩余人数，`enrollmentAvailability` 与当前可报名语义一致
  - [x] OpenAPI 满足 Controller tag、接口/字段中文说明、参数和响应 example、错误码及
    `x-permission`
- 测试方式：按 TDD 先观察 Controller/Service/Mapper 目标测试失败，再最小实现；
  运行受影响零 DB 测试和课程 Mapper IT
- 验证结果（2026-07-27）：
  - Red：新增测试首次因 Learner Query/VO 尚不存在而在 testCompile 失败，已观察预期失败
  - Green：`CourseLearningControllerWebSliceTest` 4 个、
    `CourseLearnerReadingServiceImplTest` 2 个、既有 `CourseLearningServiceImplTest` 5 个，
    共 11 个零 DB 用例通过
  - IT：`CourseEnrollmentMapperIT` 3 个真 MySQL 用例通过，包含本人和学习状态分页隔离
  - Review：规格符合性“通过”；代码质量“通过”

## Task 2：完成本人学习任务清单能力

- 状态：已完成（2026-07-27）
- Owner：lims
- 输入：Task 1；开课冻结版本快照；内容项进度事实；确认后的可学习状态规则
- 输出：本人学习任务接口、章节/内容项/引用/进度 VO、快照与进度组装服务及 OpenAPI 契约
- 前后端影响：仅后端；前端以 OpenAPI 为准另开
- API 依赖：新增 `GET /course/learning/enrollments/{enrollmentId}/tasks`
- 数据影响：只读既有报名、开课、版本快照和内容项进度；不改数据库
- 验收标准：
  - [x] 仅报名本人可以读取；其他登录用户不能获得课程或任务信息
  - [x] 返回报名上下文、课程和开课名称、章节与内容项任务
  - [x] 章节和内容项来自开课冻结的课程版本快照，不读取可变草稿
  - [x] 返回标题、必修标记、排序、进度、可学习状态及必要素材/考试/直播引用
  - [x] 没有进度记录的快照内容项仍返回，并使用 NOT_STARTED 和零时长默认事实
  - [x] 取消、退选、过期和停用内容按设计返回不可学习原因
  - [x] 不实现顺序解锁，不返回未实现的素材播放、预览或下载地址
  - [x] OpenAPI 路径、字段、example、本人边界和错误码与实现一致
- 测试方式：按 TDD 先观察本人边界、快照组装和状态规则测试失败，再最小实现；
  运行受影响零 DB 测试，涉及真实 JSON/Mapper 的路径运行相关 IT
- 验证结果（2026-07-27）：
  - Red：新增测试首次因 `LearnerCourseTasksVO` 和服务方法尚不存在而在 testCompile 失败
  - Green：`CourseLearningControllerWebSliceTest` 5 个、
    `CourseLearnerReadingServiceImplTest` 5 个、既有 `CourseLearningServiceImplTest` 5 个，
    共 15 个零 DB 用例通过
  - IT：`CourseEnrollmentMapperIT` 3 个、`CourseVersionMapperIT` 1 个，共 4 个真 MySQL 用例通过
  - Review：规格符合性“通过”；代码质量“通过”

## Task 3：完成课程读取接口验收与文档闭环

- 状态：已完成（2026-07-27）
- Owner：lims
- 输入：Task 1～2；OpenSpec 验收标准；OpenAPI 编写规范
- 输出：目标测试结果、双轴 Review、QA、Retro、OpenSpec Task 状态及进度表验收证据
- 前后端影响：仅后端；前端以 OpenAPI 为准另开
- API 依赖：校验 `docs/api/course.openapi.json` 与三个新增接口实现一致
- 数据影响：验证既有 schema 与查询，不创建 migration
- 验收标准：
  - [x] 零 DB 与相关 IT 通过，实际命令、用例数和未运行项如实记录
  - [x] 后端纵向链路“列出可报名课程 → 自助报名 → 我的课程 → 读取冻结任务 → 上报学习会话”可验证
  - [x] 规格符合性 Review 结论为“通过”
  - [x] 代码质量 Review 结论为“通过”
  - [x] QA、Retro、OpenSpec 和进度表状态及验收证据一致
- 测试方式：
  - `mvn -pl sts-service-admin -am test`
  - `mvn -pl sts-service-admin -am -Pintegration-test test`
  - `openspec validate 2026-07-27-add-learner-course-reading-apis`
  - `git diff --check`
- 验证结果（2026-07-27）：
  - 全量零 DB：370 个通过，0 失败
  - 完整 MySQL IT：85 个通过，0 失败
  - QA：`engineering/qa/2026-07-27-learner-course-reading.md`
  - Retro：`engineering/retro/2026-07-27-learner-course-reading.md`
  - 规格符合性 Review：通过
  - 代码质量 Review：通过
