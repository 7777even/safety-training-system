# Proposal：素材真实转码与媒体派生后端闭环

## 背景

素材模块已经具备元数据 CRUD、文件关联、处理状态门禁、转码任务表和失败重试入口，但当前文件仅由
`LocalStorageAdapter` 写入应用机磁盘，音视频任务由 `TranscodeStub` 在同一事务内同步标记成功，不产生可播放派生文件，也不提取
`duration_seconds` 或 `page_count`。测试机虽已部署 MinIO、FFmpeg 和 OnlyOffice Document
Server，现有后端尚未接入，无法形成真实上传、转码、预览和失败恢复闭环。

路线图 M5 已按后端口径关闭，进度表 `COURSE-02` 已满足“在 M5 后创建独立素材转码 Change”的启动条件。本 Change 以
`docs/architecture/asset-transcode-design.md` 的已确认决策为基础，实施阶段 10 的后端能力。

## 目标

1. 以 MinIO 替换测试/部署环境的本地文件存储，并保留 local 配置供开发和单元测试使用。
2. 将音视频和可转换文档纳入可抢占、可恢复的异步任务调度，真实调用 FFmpeg/ffprobe 和 OnlyOffice。
3. 生成 HLS、封面、MP3、PDF 等派生文件，回写素材时长或页数，并提供受控的播放/预览与 Range 文件访问接口。
4. 完成失败分类、超时回收和人工重试，避免进程异常、应用重启或多实例竞争造成任务重复执行或永久卡住。
5. 交付完整 `docs/api/system-admin.openapi.json` 与 `docs/api/asset.openapi.json`、测试机配置说明、分层自动化测试和真实环境冒烟证据。

## 用户价值

- 管理员上传音视频或文档后能够看到可信的处理状态，并在失败后安全重试。
- 学习模块可获得稳定的播放/预览载体，以及可信的音视频时长和文档页数。
- 文件不再依赖单台应用机磁盘，多实例能够共享源文件和派生文件。
- 运维人员可以从任务状态、失败分类和受控日志定位 MinIO、FFmpeg、OnlyOffice 或源文件问题。

## 功能范围

1. MinIO 单请求上传、下载、删除和 URL/对象访问适配；配置、健康检查与 bucket 初始化策略。
2. MinIO multipart 分片上传、断点续传、完成、取消和过期清理；校验文件类型、大小与 checksum。
3. DB 轮询调度、条件抢占、受限并发、运行超时、应用重启恢复和人工重试。
4. VIDEO 转为 H.264/AAC 720p 单码率 HLS 并生成封面；AUDIO 转为 128kbps MP3；回写时长。
5. doc/docx/ppt/pptx 经 OnlyOffice 转为 PDF 并回写页数；原生 PDF 解析页数；xls/xlsx 保持原文件直读且不进入转码链。
6. 派生文件登记、素材处理结果原子落库、播放/预览信息查询和支持 HTTP Range 的鉴权文件代理。
7. 同步素材 OpenAPI、部署说明、目标自动化测试、MinIO 集成测试和测试机真机冒烟。

## 非目标范围

- 不修改任何前端页面、Mock、联调代码或 UI；前端以 OpenAPI 为准另开。
- 不实施 WebSocket 前端订阅、管理端播放器、学员播放器或页面预览组件。
- 不实施多码率自适应 HLS、DRM、字幕、AI 处理、直播转推、云厂商转码或 PDF 线性化。
- 不实施离线站内信；仅保留后续可订阅的领域事件边界。
- 不修改课程、考试、培训或学习完成业务规则；这些模块只消费本 Change 形成的稳定素材事实。
- 不操作生产环境或生产数据，不把测试机密钥写入仓库。

## 需人工确认的实施决策

1. 允许新增 MinIO Java SDK 和 PDFBox 生产依赖；具体兼容版本在实现 Task 1 前按当前 Spring Boot/JDK 版本锁定。
2. 测试/部署环境使用 `storage.type=minio`；`local` 仅保留为开发和零 DB 测试适配器。
3. MinIO 使用私有 bucket，客户端不获得永久对象直链；播放/预览统一经过后端鉴权代理。
4. 视频输出采用 H.264/AAC 720p 单码率 HLS、10 秒切片；音频输出采用 128kbps MP3。
5. 转码调度默认并发 2、任务运行超时 30 分钟、自动重试 0 次，只允许人工重试。
6. 文档转换使用 OnlyOffice Document Server HTTP API；JWT 必须启用，源文件仅通过短期内部访问令牌回拉。
7. 已确认新增 multipart 上传会话表，通过 migration 持久化调用人、机构、provider uploadId、对象键、文件声明、状态和到期时间。
8. 已确认使用 AWS SDK v2 S3 Client 统一访问 MinIO，避免依赖 MinIO SDK 非公开 multipart API。
9. 已人工确认（2026-08-06 追加）：素材目录的 Word 上传只接受结构可安全解析的标准 `.docx`；旧 `.doc`，以及包含旧式 VML
   文本框的 `.docx`，在形成 `fileInfoId` 前拒绝，并提示用户在 WPS/Word 中整理浮动文本框后另存为 DOCX，或直接上传 PDF。
   不按 `Application=WPS` 一刀切，避免拒绝不含风险结构的正常 WPS 文档。
