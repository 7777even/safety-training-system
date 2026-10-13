# Design：学员课程简介与受控附件下载

## Context

`CourseEnrollmentDetailVO` 当前由学员本人详情和管理端学习详情共同使用。报名关联的 `CourseOffering.courseVersionId`
指向冻结课程版本，`CourseContentSnapshot` 已包含完整 `course` 快照及 `downloadAssets`。其中附件快照只有 `assetId`、
`sortOrder`、`status`，没有 `courseDownloadAssetId`。

现有 `/asset/files/{assetId}/{fileInfoId}` 受 `asset:asset:list` 保护，只校验素材与文件关联，无法单独证明当前学员对某次课程报名拥有下载权。因此本
Change 在课程学习域新增受控下载入口，并继续复用底层私有存储读取能力。

当前 Git 身份为 `lims`，本 Change 只设计和实施后端、`docs/api/course.openapi.json` 及后端工程记录。

## API 设计

### 1. 聚合本人课程学习详情

扩展：

`GET /api/course/learning/enrollments/{enrollmentId}`

在现有 `CourseEnrollmentDetailVO` 增加：

- `course`：冻结课程简介。
- `downloadAssets`：冻结附件引用经当前素材与源文件事实补充后的附件元数据。

`course` 字段：

- `courseId`
- `courseCode`
- `courseName`
- `introduction`
- `teachingObjective`
- `syllabus`
- `assessmentMethod`
- `credit`（`BigDecimal`）
- `pointMode`
- `pointValue`（`BigDecimal`，与既有课程模型一致）
- `contentMode`
- `sourceDownloadMode`
- `coverFileId`

`downloadAssets` 每项字段：

- `assetId`：冻结附件标识，也是受控下载路径参数。
- `assetName`
- `assetType`
- `fileName`
- `mimeType`
- `fileSize`
- `fileInfoId`
- `sortOrder`
- `downloadable`
- `downloadUrl`
- `unavailableReason`

附件集合和排序必须来自开课冻结版本，不回读当前 `sts_course_download_asset` 配置。素材名称、类型、MIME、文件大小和
`fileInfoId` 使用当前机构内的素材及源文件事实补充。

`courseDownloadAssetId` 不进入学员响应。原因是历史冻结快照未保存该值，使用当前配置 ID 会让已开课内容受后续课程编辑影响，并导致已删除后重建的配置
ID 失真。`assetId` 已由冻结快照稳定表达该附件。

详情接口保持既有访问语义：有效登录且报名本人即可查看，包括已完成课程；本 Change 不因附件下载能力收紧详情页访问，也不新增后台权限码。管理端详情沿用同一
VO，可同时获得相同冻结课程与附件信息，最近会话差异保持不变。

### 2. 学员受控下载附件

新增：

`GET /api/course/learning/enrollments/{enrollmentId}/download-assets/{assetId}/file`

请求：

- 路径参数 `enrollmentId`：当前用户的报名 ID。
- 路径参数 `assetId`：详情响应中冻结附件的素材 ID。
- 可选请求头 `Range`：沿用底层单区间字节读取语义。

响应：

- 完整文件返回 `200`，合法单区间返回 `206`，非法或越界 Range 返回 `416`。
- `Content-Type` 使用安全解析后的素材 MIME，无法识别时使用 `application/octet-stream`。
- `Content-Disposition` 使用安全编码的源文件名并声明 `attachment`。
- 不使用统一 JSON `Result` 包装文件字节流。

`downloadUrl` 固定由后端生成当前受控路径：

`/api/course/learning/enrollments/{enrollmentId}/download-assets/{assetId}/file`

调用方不得拼接对象存储地址。`downloadable=false` 时 `downloadUrl=null`。

## 冻结事实与当前事实边界

以下事实读取冻结版本：

- 课程简介和课程下载模式。
- 附件是否属于该课程版本。
- 附件排序和附件启用状态。

以下事实读取当前数据：

- 素材是否仍存在、属于当前机构且启用。
- 素材处理状态、来源类型和当前源文件关联。
- 源文件元数据是否仍存在。
- 实际存储对象在下载时是否可读取。

这样既保持开课内容不可变，也允许素材停用、文件删除等安全操作即时阻止下载。

## 附件可用状态

按以下优先级计算 `downloadable` 和 `unavailableReason`：

1. 冻结课程 `sourceDownloadMode != ALLOW`：`SOURCE_DOWNLOAD_DISABLED`。
2. 冻结附件 `status != 1`：`ASSET_DISABLED`。
3. 当前机构内素材不存在或 `status != 1`：`ASSET_DISABLED`。
4. 素材 `processStatus != SUCCEEDED`：`ASSET_PROCESSING`。
5. 素材不是带源文件的 `UPLOAD` 来源或 `fileInfoId` 为空：`UNSUPPORTED_ASSET_TYPE`。
6. 源文件记录不存在：`FILE_NOT_FOUND`。
7. 当前未启用私有对象存储下载能力：`STORAGE_UNAVAILABLE`。
8. 全部满足：`downloadable=true`、`unavailableReason=null`，返回受控 `downloadUrl`。

详情查询只依据数据库事实计算状态，避免为每个附件向对象存储执行探测；实际对象缺失在下载时按 `FILE_NOT_FOUND`/HTTP 404
处理，不泄漏对象名。

## 下载授权与错误语义

下载服务按顺序执行：

1. 使用当前登录人和当前机构查询报名；报名不存在返回 `5071`。
2. 强制 `enrollment.userId == 当前登录人`；非本人返回 HTTP 403、业务码 `5081`。
3. 查询报名关联开课并校验当前允许学习/下载：取消、不可学习或报名已退选/过期时拒绝；已完成报名仍允许在开课有效规则内下载。
4. 加载开课冻结版本；缺失或不可解析返回 `5083`。
5. 校验冻结课程 `sourceDownloadMode=ALLOW`，且路径 `assetId` 存在于冻结附件并处于启用状态。
6. 依赖租户拦截器查询素材和文件，验证当前机构、素材启用、处理成功、UPLOAD 源文件及文件关联。
7. 通过私有存储输出文件；对象不存在统一按 404 处理。

新增课程学习错误码：

- `5087`：课程或当前报名状态不允许下载附件。
- `5088`：素材不属于该报名的冻结课程附件。
- `5089`：附件素材或源文件当前不可下载。

具体内部失败不向无权访问者泄漏附件是否存在；先完成本人和冻结归属校验，再返回附件状态错误。

## 分层设计

- `CourseLearningController`：详情接口保持原路径，返回扩展 VO。
- `CourseLearningDownloadController`：承载文件流、Range 与下载响应头，不承载业务权限规则；仅在私有存储配置可用时注册。
- `CourseLearningService`：从冻结快照组装课程和附件元数据，统一本人边界。
- `LearnerCourseDownloadService`：执行下载授权链并调用底层存储访问服务。
- VO：增加独立课程简介 VO 与学员下载附件 VO，不暴露 Entity。
- OpenAPI：详情 operation 及新下载 operation 分别归入真实 Controller tag；`x-permission=authenticated`。

## 数据影响

- 只读既有报名、开课、课程版本快照、素材和文件表。
- 不修改 Entity 映射、schema、索引或数据库约束。
- 不创建 migration。

## TDD 与测试设计

### 零 DB

1. `CourseLearningControllerWebSliceTest`：本人详情路径透传，扩展响应经真实 `ResultEasyTransAdvice` 保持 `Result`
   外壳，课程及附件字段序列化正确。
2. `CourseLearningServiceImplTest`：

- 本人详情从冻结快照返回课程简介和附件顺序。
- 不读取可变课程附件配置。
- 非本人报名拒绝且不继续查询快照/素材。
- 下载模式禁用、冻结附件禁用、素材禁用、处理中、无源文件、文件记录不存在分别返回稳定不可用原因。

3. `CourseLearningDownloadControllerTest`：完整下载、Range、`Content-Disposition`、404 与 416 响应行为。
4. `LearnerCourseDownloadServiceTest`：本人成功；他人报名、无效报名/开课、下载模式关闭、附件不在冻结快照、素材失效和文件不存在均拒绝，并验证无越权存储读取。

### 带 DB

本 Change 不新增自定义 SQL、Entity、Mapper、schema、索引或持久化事务，目标行为可由零 DB 测试覆盖；默认不新增机械重复的
IT。实现中若需要新增 Mapper SQL 或改变持久化路径，必须暂停并升级数据影响设计，再补相关 MySQL IT。

## 风险与取舍

- 详情接口需要批量读取素材与文件，必须避免逐附件查询；实现使用批量 ID 查询并在内存按冻结顺序组装。
- 文件流 Controller 受 `storage.type=minio` 条件控制，与现有素材文件访问能力保持一致；存储能力未启用时返回
  `STORAGE_UNAVAILABLE`，不伪造可下载地址。
- 历史快照可能引用已删除素材。详情返回不可用项而不是静默移除，便于调用方解释课程附件缺失。
- `ASSET_PROCESSING` 同时覆盖尚未成功完成处理的状态；下载接口使用统一 `5089`，不向学员暴露内部处理失败细节。

## 待人工确认

1. 同意使用冻结快照已有的 `assetId` 作为学员附件标识，不返回无法对历史快照稳定保证的 `courseDownloadAssetId`。
2. 同意详情可查看语义保持不变，下载操作单独执行更严格的报名/开课状态校验。
3. 同意附件详情不逐项探测对象存储，实际对象缺失在下载时返回 404。
