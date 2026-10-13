# Tasks：素材真实转码与媒体派生后端闭环

> 状态：已人工确认（2026-08-04）；Task 1 已确认 migration 与 AWS SDK v2 S3 Client 路线。
>
> Owner：lims
>
> 执行约束：确认后一次只执行一个 Task；单次最多连续完成三个 Task。每个行为采用 Red → Green → Refactor，并在完成后更新本文件与进度表。

## Task 1：完成 MinIO 文件存储与可恢复上传

- 状态：已完成（2026-08-04）
- Owner：lims
- 输入：现有 `StorageService`/`LocalStorageAdapter`、`us_sys_file_info`、测试机 MinIO、已确认私有 bucket 和 10MB 分片参数。
- 输出：MinIO S3 存储适配、存储配置与健康检查、兼容单文件上传、multipart 初始化/续传/完成/取消/过期清理、
  `docs/api/system-admin.openapi.json`、部署配置和自动化测试。
- 前后端影响：仅后端；前端以 OpenAPI 为准另开。
- API 依赖：保留 `POST /api/system/files`；新增 `/api/system/files/multipart/**` 四类 operation；权限统一为
  `sys:file:upload`。
- 数据影响：新增 `sts_file_multipart_upload` migration，持久化 uploadId、调用人、机构、文件声明、状态和到期时间；分片明细由
  MinIO `ListParts` 提供。
- 验收标准：
  - [x] `storage.type=local|minio` 可显式切换，MinIO 配置缺失、bucket 不存在或不可达时快速失败。
  - [x] MinIO bucket 保持私有，对象键由后端生成，不暴露 access/secret key。
  - [x] 单文件上传、下载、删除和文件信息登记在 MinIO 模式真实可用。
  - [x] 分片中断后可恢复已上传 part，完成后大小与 checksum 一致，取消和 24h 过期清理会 abort multipart。
  - [x] uploadId、checksum 或 fileInfoId 不能用于跨用户/跨机构秒传或读取他人文件。
  - [x] OpenAPI 满足分组、中文说明、参数/请求/响应 example、错误码和 `x-permission`。
- 测试方式：TDD 零 DB 覆盖参数与权限；MinIO 集成测试覆盖 put/get/range/delete/multipart resume/complete/abort；执行 Admin
  目标零 DB 与受影响 IT。
- 决策记录（2026-08-04）：人工确认新增 multipart 会话表，并采用 AWS SDK v2 S3 Client 统一接入 MinIO；进入 TDD 实施。
- 当前验证（2026-08-04）：
  - Red：目标测试因 S3 网关、multipart DTO/VO、会话实体/Mapper、Service 和 Controller 不存在而按预期编译失败。
  - Green：Admin 零 DB 全量 447/447 通过；Task 1 目标零 DB 14/14 通过。
  - MySQL/MinIO IT：`FileMultipartUploadMigrationIT` 1/1；`AwsS3StorageGatewayIT` 4/4，覆盖私有对象读写删除、multipart
    续传/完成/checksum/abort 和禁用自动建桶快速失败。
  - 契约：`system-admin.openapi.json` JSON 与内部 `$ref` 校验、`openspec validate --strict`、`git diff --check` 通过。
  - Review：规格符合性“通过”；代码质量“通过”。无前端修改、无无关重构；Task 2 及真实转码未提前实施。

## Task 2：完成异步任务调度与失败恢复

- 状态：已完成（2026-08-04）
- Owner：lims
- 输入：Task 1；现有素材状态机、`sts_asset_transcode_job`、`TranscodeStub` 与重试接口。
- 输出：`TranscodeProvider` SPI、异步 DB 轮询调度、条件抢占、并发限制、超时巡检、失败分类、人工重试和 stub 测试 Provider。
- 前后端影响：仅后端；前端以 OpenAPI 为准另开。
- API 依赖：`POST /api/asset/assets` 改为异步语义；`GET /api/asset/transcode-jobs` 增加 `jobStatus`；重试错误契约同步更新。
- 数据影响：复用现有任务表和索引；如条件抢占或恢复需要新增字段/索引，暂停并申请 migration 决策。
- 验收标准：
  - [x] 创建音视频或可转换文档后返回 PENDING/UPLOADED，不在请求事务内执行转码。
  - [x] 两个调度实例竞争同一任务时只有一个实例成功抢占。
  - [x] 执行并发默认不超过 2，超过 30 分钟的 RUNNING 被回收为 FAILED/TIMEOUT。
  - [x] 失败分类稳定，failureReason 已限长脱敏；自动重试为 0，人工重试递增 retryCount。
  - [x] 应用重启不会把任务永久留在 RUNNING，也不会自动重复处理已成功任务。
- 测试方式：TDD 零 DB 覆盖调度编排、状态机、失败和重试；MySQL IT 覆盖真实条件抢占、并发唯一性、超时回收和状态持久化。
- 当前验证（2026-08-04）：
  - Red：异步创建、Provider 编排和超时恢复目标测试先因旧同步桩行为及缺少调度组件而失败。
  - Green：Task 2 目标零 DB 12/12；Admin 零 DB 全量 455/455；`TranscodeJobMapperIT` 2/2。
  - MySQL IT 首轮发现注解 SQL 错误转义 `<`，修复后验证 PENDING 条件抢占唯一、成功终态不可被迟到超时覆盖及超时查询持久化。
  - 契约与静态检查：`asset.openapi.json` JSON 校验、`openspec validate --strict`、`git diff --check` 通过。
  - Review：规格符合性“通过”；代码质量“通过”。补充 URL 查询串与凭据脱敏、终态竞争回归保护；无前端修改、无新增 migration、未提前实施
    Task 3 的真实转码。

## Task 3：完成音视频真实转码与派生结果

- 状态：已完成（2026-08-04）
- Owner：lims
- 输入：Task 1～2；测试机 FFmpeg/ffprobe；设计方案的视频、音频参数。
- 输出：`FfmpegProvider`、VIDEO HLS/封面、AUDIO MP3、时长提取、派生文件登记、成功结果原子落库和失败补偿。
- 前后端影响：仅后端；前端以 OpenAPI 为准另开。
- API 依赖：不新增独立供应商接口；素材详情和任务列表返回真实元数据与失败信息。
- 数据影响：复用素材、派生文件、任务和文件信息表；不新增 migration。
- 验收标准：
  - [x] VIDEO 产出可播放的 720p H.264/AAC HLS、10 秒 ts 分片和 jpg 封面。
  - [x] AUDIO 产出 128kbps MP3；音视频 durationSeconds 与 ffprobe 结果误差不超过 1 秒。
  - [x] ProcessBuilder 不经过 shell，命令路径和临时目录安全，超时会终止进程树并清理任务目录。
  - [x] 所有产物上传 MinIO 后才原子提交派生记录、素材元数据和 SUCCEEDED 终态。
  - [x] 失败不产生半成品派生记录；重复提交不产生重复当前派生文件，也不覆盖旧成功产物。
- 测试方式：TDD 零 DB 覆盖命令、超时、结果与补偿；MySQL/MinIO IT 覆盖派生落库；测试机以含音轨视频和非 MP3 音频样本执行真机冒烟。
- 当前验证（2026-08-04）：
  - Red：目标测试先因 `FfmpegCommandFactory`、`ExternalProcessRunner` 等实现不存在而编译失败。
  - Green：Task 3 目标零 DB 12/12；Admin 零 DB 全量 462/462。
  - 真实 FFmpeg：本机 `/usr/bin/ffmpeg`、`/usr/bin/ffprobe` 生成 H.264/AAC HLS、TS、JPG 和 128kbps MP3，时长验证通过。
  - MySQL IT：`TranscodeJobMapperIT` 2/2、`TranscodeResultServiceIT` 1/1，验证终态竞争、派生登记、durationSeconds 与
    Job/Asset 原子成功状态。
  - 存储：复用 Task 1 已通过的 MinIO put/get/delete IT；Provider 使用任务隔离前缀，失败及数据库提交异常触发对象清理。
  - Review：规格符合性“通过”；代码质量“通过”。MinIO 异常稳定映射 `STORAGE_ERROR`，进程不经过 shell，临时目录删除限制在任务子目录；无前端修改、无
    migration、未提前实施 Task 4。

## Task 4：完成文档预览转换与元数据

- 状态：进行中（2026-08-06；复杂 DOCX 上传兼容性限制已完成，待用重新另存的标准 DOCX 完成视觉验收）
- Owner：lims
- 输入：Task 1～2；测试机 OnlyOffice 9.2、JWT、中文字体与容器网络；PDFBox。
- 输出：`DocPreviewProvider`、一次性内部源文件端点、Word 上传兼容性预检、docx/ppt/pptx 转 PDF、原生 PDF 页数解析、Excel
  例外规则和结果落库。
- 前后端影响：仅后端；前端以 OpenAPI 为准另开。
- API 依赖：内部源文件端点不作为前端 Contract；素材详情/任务接口同步文档真实状态和 pageCount。
- 数据影响：复用现有表，不新增 migration。
- 已确认追加范围（2026-08-06）：素材目录拒绝旧 `.doc` 及含旧式 VML 文本框的 `.docx`，错误码 5006；单文件与 multipart
  均须在形成可引用 `fileInfoId` 前完成预检及失败补偿；已按 TDD 实现并同步 `system-admin.openapi.json`。
- 验收标准：
  - [ ] doc/docx/ppt/pptx 创建 DOCUMENT_PREVIEW 并生成可打开 PDF，中文无方框且版式可用。
  - [x] OnlyOffice JWT、一次性令牌、主机白名单、超时和产物大小限制有效，不能形成任意文件读取或 SSRF。
  - [x] 原生 PDF 不建 Job并解析 pageCount；xls/xlsx 不建 Job、不生成 pageCount，按 Excel 原文件标识交付。
  - [x] PDF pageCount 与真实页数一致，错误按 SOURCE_INVALID/TRANSCODE_ERROR/STORAGE_ERROR 分类。
  - [x] 失败不保留半成品派生记录或永久有效的源文件令牌。
  - [x] 旧 `.doc` 与含旧式 VML 文本框的素材 `.docx` 在上传阶段返回 400/5006；标准 DOCX 与非素材目录附件不受误伤。
- 测试方式：TDD 零 DB 覆盖请求/JWT/令牌/错误映射；MySQL/MinIO IT 覆盖结果原子性；测试机用含中文 docx、pptx、pdf、xlsx 冒烟。
- 当前验证（2026-08-04）：
  - Red：PDF 页数目标测试先因 `PdfPageCounter` 不存在而编译失败。
  - Green：Task 4 目标零 DB 16/16；Admin 零 DB 全量 466/466；`TranscodeResultServiceIT` 1/1。
  - 已验证：PDFBox 真实双页 PDF 与非法 PDF、原生 PDF 同步 pageCount、Excel 不建任务、HS256 JWT、下载协议/主机白名单、200MB
    流式上限、PREVIEW/pageCount 原子提交及失败对象清理；Redis 一次性令牌代码已实现，待真机回拉验证。
  - 未执行：经批准只读执行 `docker ps`，测试机当前没有 OnlyOffice 容器，也未提供 `ONLYOFFICE_*` 参数，无法执行中文
    DOCX/PPTX → PDF、字体和容器回拉真机冒烟；不得据此勾选前两条验收标准或标记 Task 完成。
  - Review：规格符合性“需修改/补验收”（仅缺真机证据）；代码质量“通过”。无前端修改、无 migration、未进入 Task 5。
  - 2026-08-06 真机补验：本机 MinIO `19000`、OnlyOffice Document Server 9.2 `28090` 健康；修复内部一次性源文件路由被全局
    登录拦截导致的 401，并以 Red → Green 新增 `SaTokenConfigTest`。Admin 零 DB 472/472 通过。
  - 中文 DOCX 经人工重试由 FAILED → RUNNING → SUCCEEDED，生成 16 页、1,605,841 字节 PDF；中文字体嵌入且文本可提取，
    play-info 与 206 Range（`%PDF-1.7`）通过。失效源文件令牌无登录重放返回 404。
  - 64MB 中文 PPTX 经 7 个 10MB 分片上传并从中断处补传成功，转换为 66 页、23,148,597 字节 PDF；中文字体嵌入且前三页
    文本可提取。JWT、主机白名单、超时、产物大小和一次性令牌的既有自动化测试与本次容器回拉共同通过。
  - Review：2026-08-06 独立双轴 Review 结论为规格符合性“通过”、代码质量“通过”；精确放行一次性回拉路径，普通业务路由
    仍受登录保护。无前端修改、无 API 或 migration 变化。
  - 2026-08-06 视觉复核纠正：中文 DOCX 虽可打开、可提取中文且嵌入字体，但原文件元数据为 24 页，OnlyOffice 仅生成 16 页；
    多页存在浮动文本框重排叠字。源文件由 WPS 生成，包含 120 个浮动锚点和 72 个旧式 VML 文本框，因此第一条验收重新取消勾选。
  - 2026-08-06 兼容性限制：Red 阶段先后观察校验器缺失及 ZIP/页眉、`.vml`、目录 entry、补偿失败等边界测试共 6 项失败；Green
    后兼容性目标测试
    17/17、Admin 零 DB 488/488、全量 MySQL/MinIO IT 117/117。真实接口以原异常 DOCX 验证返回 HTTP 400/code 5006，
    测试库未新增文件记录；新增 MinIO IT 验证 multipart 合并对象删除、会话转 `ABORTED` 且不登记文件。WPS 生成但不含 VML
    文本框的 `招标文件.docx` 真实上传返回 200，测试记录和对象随后已删除。
  - 2026-08-10 租户回拉缺陷：OnlyOffice 免登录回拉请求虽经过 `TenantContextFilter`，但没有登录会话，内部 Controller 直接查询
    `sts_asset_transcode_job` 时因缺少租户上下文被拒绝。按 TDD 将 Redis 一次性令牌载荷从 `jobId:fileInfoId` 调整为
    `organizationId:jobId:fileInfoId`；签发时保留 5 分钟 TTL，消费时仍通过 Lua 原子 GET+DEL。Controller 消费可信令牌后用
    `TenantContext.callWithTenant` 包住任务、文件查询及下载，正常和异常路径均在 `finally` 恢复/清理上下文。Red 阶段因旧签名和
    两字段 Grant 编译失败；Green 后目标测试 4/4、Admin 零 DB 565/565 通过。无 API Contract、数据库或前端变化。

## Task 5：完成受控播放预览与 Range 文件访问

- 状态：已完成（2026-08-04；用户明确暂缓 Task 4 真机测试并继续下一任务）
- Owner：lims
- 输入：Task 3～4 的真实派生文件；现有机构与素材权限规则。
- 输出：`play-info`、私有文件代理、HLS 相对分片访问、HTTP Range、管理端素材归属校验和完整 OpenAPI。
- 前后端影响：仅后端；前端以 OpenAPI 为准另开。
- API 依赖：新增 `GET /api/asset/assets/{assetId}/play-info` 与受控 `/api/asset/files/**`；管理端权限为
  `asset:asset:list`。
- 数据影响：只读既有素材、派生和文件信息，不新增 migration。
- 验收标准：
  - [x] 仅 SUCCEEDED 且属于当前机构的素材返回 play-info，响应包含格式、地址、封面、durationSeconds/pageCount。
  - [x] 未授权调用不能通过枚举 fileInfoId、对象键或 HLS 分片读取其他机构文件。
  - [x] 完整请求返回 200；合法单 Range 返回 206 和正确字节；非法、多区间或越界请求返回 416。
  - [x] HLS m3u8 内的相对 ts 路径能够经过相同权限边界读取。
  - [x] 响应不暴露 MinIO 凭据、bucket 或永久地址，文件名和响应头不存在注入风险。
- 测试方式：TDD standalone MockMvc 覆盖 HTTP 契约与权限；MinIO IT 覆盖真实字节流和 Range；测试机用 curl、ffprobe 或播放器验证
  HLS/MP3/PDF。
- 当前验证（2026-08-04）：
  - Red：Range 目标测试先因 `ByteRangeParser` 和 `ByteRange` 不存在而编译失败。
  - Green：Task 5 目标零 DB 4/4；Admin 零 DB 全量 470/470；`AwsS3StorageGatewayIT` 4/4，新增真实 MinIO Range 字节断言。
  - 契约：新增 play-info、关联文件代理和 HLS 相对文件三个 OpenAPI path；响应只返回 `/api/asset/files/**` 受控地址。
  - 安全：每次读取先校验 SUCCEEDED、当前机构、素材与 fileInfo/派生关联；HLS 仅允许已登记任务目录和固定文件名，Content-Type
    白名单化，不生成 Content-Disposition。
  - Review：规格符合性“通过”；代码质量“通过”。无前端修改、无 migration、未进入 Task 6；Task 4 的 OnlyOffice 真机阻塞仍独立保留。

## Task 6：完成测试机验收与 M6 后端闭环

- 状态：进行中（2026-08-06；OnlyOffice 文档链路已解除阻塞，完整样本验收尚未完成）
- Owner：lims
- 输入：Task 1～5；测试机 MinIO、FFmpeg、OnlyOffice 和部署参数；OpenSpec 验收要求。
- 输出：部署文档、配置清单、真机冒烟记录、完整 `asset.openapi.json`、双轴 Review、QA/Retro、tasks.md 与进度表更新。
- 前后端影响：仅后端；前端以 OpenAPI 为准另开。
- API 依赖：校验 `asset.openapi.json` 与 `system-admin.openapi.json` 中本 Change 涉及的全部 asset/system-file operation
  和错误契约。
- 数据影响：无新增数据变更；若此前批准 migration，则验证迁移、回滚与既有数据兼容。
- 验收标准：
  - [ ] 测试机完成真实上传→PENDING→RUNNING→SUCCEEDED→MinIO 派生产物→play-info/Range 的纵向链路。
  - [ ] 视频、音频、中文文档、原生 PDF、Excel 和可重试失败样本均有可复核证据。
  - [ ] Admin 受影响零 DB、MySQL IT、MinIO IT 和真机冒烟全部通过；未运行项如实记录且不得关闭 Task。
  - [x] Controller、DTO/VO 与 OpenAPI 对齐，所有 `$ref` 可解析。
  - [ ] 规格符合性和代码质量 Review 结论均为“通过”，不存在越界前端修改或无关重构。
  - [x] QA、Retro、部署说明、tasks.md、进度表 COURSE-02/M6 状态和验收证据同步完成。
- 测试方式：执行受影响模块零 DB、`-Pintegration-test` MySQL/MinIO IT、OpenAPI 校验、`openspec validate --strict`、
  `git diff --check` 和测试机真机冒烟脚本。
- 当前验证（2026-08-04）：
  - Admin 零 DB 470/470；全量 MySQL/MinIO IT 116/116；真实 FFmpeg HLS/JPG/MP3 通过。
  - `asset.openapi.json` 与 `system-admin.openapi.json` JSON/内部 `$ref` 遍历通过；修复 asset 契约既存 `NotFoundError`、
    `ConflictError` 悬空引用。
  - `openspec validate --strict`、`git diff --check` 通过；无前端差异。
  - QA：`engineering/qa/2026-08-04-asset-transcode-backend.md`；Retro：
    `engineering/retro/2026-08-04-asset-transcode-backend.md`。
  - 未关闭原因：OnlyOffice 未部署且用户决定暂缓真机测试，中文文档、原生 PDF/Excel API 纵向样本和完整上传到 play-info
    链路未全部验收；前三条及双轴 Review“均通过”不得勾选。
  - 2026-08-06 进展：中文 DOCX/PPTX 的上传、异步转换、MinIO 派生、pageCount、play-info/Range 已完成真机链路；完整视频、音频、
    原生 PDF、Excel 和可重试失败样本仍须统一复核。随后按 TDD 修复 multipart 分片输入流不可重放及流式响应 ASYNC 二次鉴权问题：
    最终目标零 DB 8/8、Admin 零 DB 478/478、`AwsS3StorageGatewayIT` 4/4；本机 7 个 10MB 分片并发上传全部成功，PDF Range
    返回 206/正确签名且无 Sa-Token async dispatch 异常日志。独立双轴 Review 均“通过”；复杂 DOCX 视觉兼容性仍按 Task 4
    继续处理。
