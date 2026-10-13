# Tasks：学员课程学习界面最小实现

## Task 1：完成 PC 学员素材学习工作台

- 状态：已完成
- Owner：fxw11
- 输入：现有课程任务、素材播放信息和学习会话 API Contract
- 输出：PC 学习页、目录切换、素材预览/播放和学习进度上报
- 前后端影响：仅前端；不新增后端接口和数据库结构
- API 依赖：`GET /api/course/learning/enrollments/{enrollmentId}/tasks`、`GET /api/asset/assets/{assetId}/play-info`、`POST /api/course/learning/sessions`
- 数据影响：复用现有课程内容项进度和学习会话事实
- 验收标准：学员可进入课程、切换章节内容、查看断点、打开现有素材格式，并在切换/离开/完成时上报学习会话
- 测试方式：PC 定向测试、typecheck、lint、build、页面视觉检查

## 后续优化（不属于当前 Task）

## Task 2：完善课程学习页课程级信息
- 状态：已完成
- Owner：fxw11
- 输入：学员课程详情接口、已发布课程快照和现有素材详情/文件访问接口
- 输出：课程详情响应中的课程简介与附件引用、学习页“课程简介/课程附件”Tab
- 前后端影响：前端学习页与学员课程详情响应；不新增数据库结构和 URL
- API 依赖：`GET /api/course/learning/enrollments/{enrollmentId}`、`GET /api/asset/assets/{assetId}`、`GET /api/asset/files/{assetId}/{fileInfoId}`
- 数据影响：复用课程版本快照和素材数据，只读
- 验收标准：进入学习页可显示课程级学习摘要；课程简介可展示；课程附件按课程配置列出并通过受控文件接口下载；不授予学员后台课程查询权限
- 测试方式：前端定向测试、OpenAPI JSON 校验、PC typecheck/lint；后端零 DB Controller 测试和编译

## 本 Task 的素材复用约束

- 文件、视频和音频统一复用既有 `AssetViewer`；富文本、链接和直播使用资源详情契约。
- 预览不支持或失败时必须保留受控下载入口，不直接拼接未授权文件地址。

- 评论、笔记、课程公开统计和课程介绍接口。
- 实时心跳、防快进、断网恢复和异常关闭补报。
- 移动端同步改造与播放器高级能力。
