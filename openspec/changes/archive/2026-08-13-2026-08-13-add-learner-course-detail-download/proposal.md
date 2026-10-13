# Proposal：补齐学员课程简介与受控附件下载

## 背景

学员课程学习详情接口 `GET /api/course/learning/enrollments/{enrollmentId}`
当前只返回报名、进度、评价和学习会话摘要，课程简介需要调用方另行查询管理端课程接口，课程附件也缺少学员侧的读取与下载入口。

现有开课冻结版本已经保存课程简介、课程下载模式和附件引用，因此可以直接从报名关联的冻结版本组装学习详情，不需要新增获取
`courseId` 的接口，也不应回读后续可变的课程草稿。现有通用素材文件接口要求 `asset:asset:list`
，不能作为学员下载入口；仅去掉该权限又无法证明素材来自本人所学课程。

## 目标

1. 在本人报名学习详情中聚合冻结版本的课程简介和少量附件元数据。
2. 提供课程学习域专用的附件文件下载接口，完整校验报名本人、冻结附件归属、课程下载模式、素材机构与文件有效性。
3. 继续使用登录态和报名本人边界，不向学员开放后台课程或素材管理权限。
4. 同步 `docs/api/course.openapi.json` 的字段、示例、权限和错误/不可用状态说明。

## 用户价值

- 学员一次请求即可渲染课程学习详情、课程简介和附件列表。
- 学员通过稳定的课程学习域地址下载文件，不接触对象存储地址，也不需要后台素材权限。
- 已开课内容继续以冻结版本为准，不受后续课程草稿或附件配置修改污染。

## 功能范围

- 扩展 `GET /api/course/learning/enrollments/{enrollmentId}`，新增 `course` 和 `downloadAssets`。
- 新增 `GET /api/course/learning/enrollments/{enrollmentId}/download-assets/{assetId}/file`，输出受控源文件流。
- 课程简介与附件集合读取开课冻结课程版本快照。
- 附件元数据按冻结引用关联当前机构内素材和源文件记录，并返回 `downloadable`、`downloadUrl`、`unavailableReason`。
- 学员本人、报名/开课状态、课程下载模式、冻结附件归属、素材状态和源文件存在性校验。
- 零 DB Controller Web Slice、Service 权限/状态测试，以及 OpenAPI、QA、Retro 和进度记录闭环。

## 非目标范围

- 仅后端；前端以 OpenAPI 为准另开，不包含页面、Mock、联调或 UI 验收。
- 不新增获取 `courseId` 的接口，不修改既有学习任务接口。
- 不向学员授予 `course:course:list`、`course:item:list` 或 `asset:asset:list`。
- 不返回 MinIO、S3 或其他对象存储直链，不签发长期公开地址。
- 不修改课程、素材、文件或快照数据库结构，不创建 migration。
- 不改变报名、开课、完成判定、课程发布或素材转码业务规则。
- 不把附件二进制内容聚合到详情响应。

