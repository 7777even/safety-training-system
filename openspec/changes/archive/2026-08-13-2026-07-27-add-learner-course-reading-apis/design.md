# Design：学员课程入口与学习任务读取接口

## Context

现有 `CourseLearningController` 可读取本人单条报名进度，`CourseOfferingController` 提供需要
`course:offering:list` 的管理端开课分页。课程发布后，`CourseOffering.courseVersionId` 冻结版本，
`CourseVersion.contentSnapshotJson` 保存课程、分类、章节和内容项快照。

当前 Git 身份为 `lims`，本 Change 仅设计和实施后端及 `docs/api/course.openapi.json`。

## API 设计

### 1. 我的课程

`GET /api/course/learning/my-enrollments`

参数：

- `pageNum`、`pageSize`
- `query.learningStatus`：可选，精确匹配现有报名学习状态

每项返回：

- `enrollmentId`
- `offeringId`
- `courseId`
- `courseName`
- `offeringName`
- `courseType`
- `startTime`、`endTime`
- `learningStatus`
- `progressPercent`
- `lastStudyTime`
- `completedTime`
- `coverFileId`（快照中无值时为 `null`）

查询规则：

1. 强制使用当前机构和 `StpUtil.getLoginIdAsLong()`，不接受调用方传入 `userId`。
2. 只返回未逻辑删除的本人报名，并关联同机构开课。
3. 课程展示字段读取开课冻结版本快照；快照不存在按数据异常处理，不回退到可变课程草稿。
4. 按 `lastStudyTime` 降序、报名时间降序稳定排序。
5. 访问时沿用既有过期口径：结束时间已过且报名未完成时，返回现有服务判定后的 `EXPIRED` 状态；不在本 Change 新建状态规则。

### 2. 学员可报名开课

`GET /api/course/learning/offerings`

参数：

- `pageNum`、`pageSize`
- `query.keyword`：匹配冻结快照课程名称或开课名称
- `query.categoryId`：匹配冻结快照 `categoryIds`
- `query.offeringStatus`：可选；学员可见范围内只接受/返回当前可报名状态

每项返回：

- `offeringId`
- `courseId`
- `courseName`
- `offeringName`
- `courseType`
- `startTime`、`endTime`
- `enrollmentType`
- `enrollmentAvailability`：当前仅为 `AVAILABLE`
- `coverFileId`

查询规则：

1. 只返回当前机构内 `publishStatus=PUBLISHED`、`status=1`、当前时间位于
   `[startTime, endTime]`（空边界视为不限制）的开课。
2. 只返回 `enrollmentType in (OPEN, LINK)` 且 `isSelfEnrollAllowed=1` 的开课。
3. 开课配置了 `audienceId` 时，必须复用 `AudienceResolveService.contains(audienceId, userId)`；
   当前用户不在参与范围则不返回。
4. 当前用户已有未删除报名时不再返回该开课，避免把不可重复报名项目展示为可报名。
5. 课程名称、分类和封面读取冻结版本快照，不读取后续可变草稿。
6. 当前数据模型没有容量或名额字段，因此不返回 `remainingCount`；返回
   `enrollmentAvailability=AVAILABLE` 表示通过当前全部报名条件。
7. 按开课开始时间升序、开课主键降序稳定排序。

`LINK` 当前沿用既有已确认规则，与 `OPEN` 使用同一自助报名资格；本 Change 不新增链接令牌。

### 3. 本人学习任务

`GET /api/course/learning/enrollments/{enrollmentId}/tasks`

返回：

- 报名上下文：`enrollmentId`、`offeringId`、`courseId`、`courseName`、`offeringName`、
  `courseType`、`startTime`、`endTime`、`learningStatus`、`progressPercent`、`coverFileId`
- `chapters`：按快照 `sortOrder` 返回
- 每章 `chapterId`、`chapterName`、`sortOrder`
- 每个内容项 `courseItemId`、`itemType`、`itemTitle`、`isRequired`、`sortOrder`
- 学习事实：`progressStatus`、`studySeconds`、`lastPositionSeconds`、`lastPageNo`、
  `completedTime`、`lastStudyTime`
- 可学习状态：`learnable`、`unavailableReason`
- 必要引用：`assetId`、`examId`、直播快照信息（存在时）

组装规则：

1. 先按当前机构查询报名，再强制校验 `enrollment.userId == 当前登录人`；非本人统一按既有本人访问错误语义拒绝。
2. 通过报名关联开课，并读取 `courseVersionId` 对应的 `contentSnapshotJson`。
3. 章节和内容项结构完全来自冻结快照；进度按 `courseItemId` 左连接，尚无进度的内容项返回
   `progressStatus=NOT_STARTED` 和零学习时长。
4. `learnable=true` 仅表示报名和开课当前允许进入该任务：

- 开课已取消、报名已退选或已过期时为 `false`；
- 快照章节/内容项停用时为 `false`；
- 其余为 `true`。

5. `unavailableReason` 使用稳定枚举：`OFFERING_CANCELLED`、`ENROLLMENT_WITHDRAWN`、
   `ENROLLMENT_EXPIRED`、`CONTENT_DISABLED`；可学习时为 `null`。
6. 本 Change 不新增前置章节、顺序解锁或试看规则；不得根据列表顺序自行推导锁定。
7. 素材只返回已有稳定标识 `assetId`，不拼装尚未实现的播放、预览或下载地址。

## 分层设计

- Controller：新增三个 GET 映射，接收分页/路径参数，不承载过滤与本人规则。
- Service：新增学员读取编排，统一获取当前机构和当前用户，执行资格、本人和快照规则。
- Mapper：为分页和排除已报名开课提供目标查询；分类过滤使用冻结版本快照关联所需的最小查询方案。
- DTO/VO：学员读取使用独立 Query/VO，不复用管理端 VO，不暴露 Entity。
- OpenAPI：三个 operation 归入 `CourseLearningController` tag，`x-permission` 标记为
  `authenticated`，说明登录态与本人/资格校验。

## 权限与数据边界

- 不增加 `@SaCheckPermission`；接口要求有效登录态。
- “我的课程”和“学习任务”均不接受 `userId`。
- 学习任务必须验证报名本人；不能只验证机构。
- 可报名列表必须同时满足机构、发布、有效期、自助报名方式、参与范围和未报名条件。
- 不根据前端路由、角色名称或客户端传参决定数据范围。

## 错误语义

- `5071`：报名不存在。
- 沿用既有本人访问错误码：报名不属于当前登录人。
- `5061`：开课不存在。
- `5083`：开课未绑定有效课程版本快照或快照不可解析。
- 分页、状态枚举等参数错误使用统一参数错误 `1001`。

实现时若发现既有本人错误码与 OpenAPI 不一致，只对齐文档与现有实现，不新增相近重复错误码。

## 数据影响

- 只读能力，不新增或修改表、索引和字段。
- 不创建 migration。
- 复用报名、开课、课程版本、内容项进度、参与范围和课程分类既有事实。

## 测试设计

### 零 DB

- Controller standalone MockMvc：分页默认值、`query.*` 绑定、路径参数透传和 Service 委托。
- Service Mockito：
  - 我的课程强制当前用户且学习状态过滤正确。
  - 可报名开课执行发布、有效期、OPEN/LINK、自助允许、参与范围和排除已报名过滤。
  - 学习任务本人允许、非本人拒绝。
  - 冻结快照章节/内容项与进度正确合并，无进度项返回默认事实。
  - 取消、退选、过期和停用内容返回正确可学习状态。

### 带 DB

- Mapper IT：我的课程真实分页与本人隔离。
- Mapper IT：可报名列表关键字、分类、有效期和排除已报名过滤。
- 快照读取继续复用真实 `sts_course_version.content_snapshot_json`，不在测试资源维护第二份 DDL。

## 风险与取舍

- JSON 快照分类过滤的 SQL 可移植性较弱：实现限定当前 MySQL 8 真库，并由 IT 证明；若现有 Mapper
  更适合先分页候选再解析，必须保证过滤发生在分页前，不能产生错误 total。
- 没有容量模型：本 Change 只能表达“当前可报名”，不能承诺剩余人数。
- `LINK` 暂无令牌：保持既有已确认语义，避免在读取接口中引入新的权限模型。

## 待人工确认

1. 同意当前无容量模型时以 `enrollmentAvailability=AVAILABLE` 表达“剩余状态”，不返回剩余人数。
2. 同意已报名开课从可报名列表排除。
3. 同意任务的可学习状态只依据现有开课/报名/内容状态，不新增顺序解锁规则。

