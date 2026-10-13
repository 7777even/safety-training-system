# Design：素材真实转码与媒体派生后端闭环

## 1. 总体约束

- Owner 为 `lims`，仅实施后端、OpenAPI、部署文档和工程记录；前端以 OpenAPI 为准另开。
- `docs/api/system-admin.openapi.json`（文件上传）和 `docs/api/asset.openapi.json`（素材处理）是对外 API 唯一机器可读契约；每个新增或变更
  operation 必须满足 Controller tag、中文字段说明、参数/请求/响应 example、错误响应和 `x-permission`。
- MinIO access key、secret key、OnlyOffice JWT、内部源文件令牌签名密钥只通过环境变量或受控配置注入，不记录在日志、数据库
  failure_reason、OpenAPI example 或仓库文件中。
- 外部进程与网络转换必须在数据库事务之外执行；任务抢占和结果落库使用短事务。
- 源文件、派生文件和临时目录按任务隔离；任何用户可控名称不得直接拼接本地路径或对象路径。

## 2. 组件与数据流

```text
文件上传 API
  → StorageService
  → LocalStorageAdapter（开发）/ MinioStorageAdapter（测试与部署）
  → us_sys_file_info
  → 创建素材与 PENDING Job
  → TranscodeScheduler 条件抢占 RUNNING
  → FfmpegProvider / DocPreviewProvider
  → 派生文件上传 MinIO 并登记 us_sys_file_info
  → 单事务写 sts_asset_derivative、素材元数据、Job/Asset 终态
  → play-info / Range 文件代理供调用方消费
```

`TranscodeProvider` 只负责一次任务的外部处理并返回结构化结果，不直接提交业务终态。调度器负责抢占、超时、异常映射和结果编排；结果服务负责派生文件、素材与任务状态的原子提交。

## 3. MinIO 存储

### 3.1 配置

配置前缀沿用 `storage`：

```yaml
storage:
  type: minio
  minio:
    endpoint: ${MINIO_ENDPOINT}
    access-key: ${MINIO_ACCESS_KEY}
    secret-key: ${MINIO_SECRET_KEY}
    bucket: ${MINIO_BUCKET:sts-private}
    auto-create-bucket: false
```

- `auto-create-bucket=false` 为部署默认值，避免应用账号拥有不必要的建桶权限；测试 profile 可开启。
- 启动时验证 endpoint、bucket 可达和必要读写能力；配置缺失或 bucket 不存在时快速失败。
- 对象键由后端生成，格式为 `source/{yyyy/MM}/{uuid}.{ext}` 或
  `derivative/{assetId}/{jobId}/{type}/{generated-name}`，不使用原文件名作为路径。

### 3.2 单文件与分片上传

- 保留 `POST /system/files` 兼容小文件上传，底层随 `storage.type` 切换。
- 新增 multipart 初始化、分片、完成、取消接口，分片大小建议 10MB；后端校验登录、权限、文件声明、大小、分片编号和完成清单。
- 断点续传使用 checksum、文件大小和调用人隔离未完成会话；不得仅凭 checksum 跨机构或跨用户返回既有文件。
- complete 后对最终大小和 checksum 对账，再登记 `us_sys_file_info`；失败时不得产生可引用文件记录。
- 定时中止超过 24 小时的未完成 multipart 会话。会话写入新增的 `sts_file_multipart_upload`，保存调用人、机构、provider
  uploadId、bucket、对象键、文件声明、状态和到期时间；具体字段通过 Task 1 migration 落地。
- 统一使用 AWS SDK v2 `S3Client` 调用 MinIO S3 兼容接口，覆盖普通对象与公开 multipart 原语；启用 path-style access。

## 4. 任务状态与并发

### 4.1 状态机

```text
PENDING --条件抢占--> RUNNING --成功提交--> SUCCEEDED
                         |--分类失败--> FAILED
RUNNING --超过阈值巡检--> FAILED(TIMEOUT)
FAILED --人工重试--> PENDING
```

- 创建上传类 VIDEO/AUDIO/可转换 DOCUMENT 后，素材保持 `UPLOADED`，任务保持 `PENDING`；不得在创建请求事务内执行转码。
- 调度器以条件更新 `job_status=PENDING` 抢占任务，更新行数为 1 的实例才能执行。
- 执行池默认并发 2；每轮领取数不得突破可用执行槽位。
- 应用重启后，超过 30 分钟的 RUNNING 任务由巡检置为 `FAILED/TIMEOUT`，不自动重放。
- 人工重试清空本次失败字段并增加 `retry_count`；历史任务 ID 保持不变，已有成功派生文件不得被失败重试覆盖。

### 4.2 失败分类

| failureCode         | 含义                     | 重试建议     |
|---------------------|------------------------|----------|
| `SOURCE_INVALID`    | 文件损坏、密码保护或声明与内容不符      | 重新上传     |
| `UNSUPPORTED_CODEC` | FFmpeg 不支持的编码          | 转换源文件后重传 |
| `TRANSCODE_ERROR`   | FFmpeg/OnlyOffice 执行失败 | 可人工重试    |
| `TIMEOUT`           | 执行或巡检超过阈值              | 可人工重试    |
| `STORAGE_ERROR`     | MinIO 下载或上传失败          | 可人工重试    |

`failureReason` 只保存经过长度限制和敏感信息清洗的摘要；命令参数中的令牌、密钥、内部 URL 查询串和完整外部响应不得落库。

## 5. Provider

### 5.1 FFmpeg

- 使用显式配置的绝对路径调用 `ffprobe` 和 `ffmpeg`，不经过 shell。
- VIDEO：提取 duration；输出 H.264/AAC、最高 720p、10 秒切片的单码率 HLS；生成 480p 封面。
- AUDIO：提取 duration；输出 128kbps MP3。
- 子进程必须消费 stdout/stderr、防止缓冲区阻塞，并按配置超时强制终止进程树。
- 临时目录为 `work-dir/{jobId}-{random}`；完成或失败后尽力清理，不跟随符号链接，不删除配置根目录。

### 5.2 OnlyOffice

- doc/docx/ppt/pptx 创建 `DOCUMENT_PREVIEW`；使用 JWT 签名调用转换 API生成 PDF。
- OnlyOffice 通过一次性、短期、绑定 jobId/fileInfoId 的内部地址回拉源文件；令牌用后失效或到期失效。
- 后端下载 OnlyOffice 返回的 PDF，上传 MinIO 后用 PDFBox 解析页数。
- 原生 PDF 不建 Job，上传完成后解析页数并作为原文件预览；xls/xlsx 不建 Job、不计算页数，格式标识为 `excel`。
- OnlyOffice 错误响应和下载 URL 需进行协议、主机和大小限制，防止 SSRF 与无界下载。

### 5.3 Word 上传兼容性预检（2026-08-06 已人工确认）

- 仅约束素材业务目录 `folder=asset`，不改变其他模块通用附件的上传规则。
- `.doc` 旧二进制格式直接拒绝，提示使用 WPS/Word 另存为 `.docx` 或上传 PDF。
- `.docx` 必须是可安全解析的 ZIP/OOXML 包；只读取受限大小的 `word/document.xml` 等必要条目，禁止路径穿越、外部实体和无界解压。
- 若主文档、页眉页脚或 `.vml` 等 OOXML XML/VML part 含旧式 VML 文本框（`v:textbox`/对应文本框内容），则判为
  OnlyOffice 高风险版式并拒绝。不得仅依据 `docProps/app.xml` 中的 WPS/Word 生成器名称拒绝文件。
- 单文件上传在写入 MinIO 前预检；multipart 在合并并校验 checksum 后、登记 `fileInfoId` 前预检，拒绝时删除已合并对象并终止会话。
- 失败使用文件域错误码 `5006`，HTTP 400，文案说明“文档包含不兼容的旧式浮动文本框，请在 WPS/Word 中整理后另存为标准
  DOCX，或上传 PDF”；不得创建可供素材引用的文件记录。
- 实现时同步 `docs/api/system-admin.openapi.json` 的单文件上传、multipart complete 错误契约；不新增数据库字段或 migration。

## 6. 结果提交与补偿

- Provider 先把全部产物上传到任务隔离的对象前缀，再进入结果提交事务。
- 成功事务内 upsert `sts_asset_derivative`、回写素材 duration/pageCount、设置素材与 Job 为 `SUCCEEDED`。
- 数据库提交失败时，记录待清理对象并尽力删除本任务新上传但未登记的产物；不得删除旧成功产物。
- 失败时只更新 Job 与素材为 `FAILED`，不创建半成品派生记录。
- 相同 jobId 的成功提交必须幂等；重复回调或重复执行不能产生多个当前派生文件。

## 7. API Contract

### 7.1 上传

- `POST /api/system/files/multipart/init`
- `POST /api/system/files/multipart/{uploadId}/parts/{partNo}`
- `POST /api/system/files/multipart/{uploadId}/complete`
- `DELETE /api/system/files/multipart/{uploadId}`

接口返回后端 uploadId、建议分片大小、已上传分片和最终 `fileInfoId`。具体请求/响应 schema 在 Task 1 编写 OpenAPI 时冻结；如果不能在无
migration 前提下安全持久化恢复信息，应暂停确认。

### 7.2 素材与任务

- `POST /api/asset/assets`：上传类音视频/文档改为异步处理语义。
- `GET /api/asset/transcode-jobs`：新增 `jobStatus` 条件。
- `POST /api/asset/assets/{assetId}/retry-transcode`：允许可转换 DOCUMENT 的失败任务重试。
- `GET /api/asset/assets/{assetId}/play-info`：返回格式、素材元数据、主访问地址与封面地址。
- `GET /api/asset/files/{fileInfoId}` 及 HLS 相对对象访问：鉴权后代理私有 MinIO 对象，支持 Range。

`play-info` 管理端权限沿用 `asset:asset:list`。学员侧访问必须由课程学习模块建立报名/可见性校验后再开放；本 Change
不自行扩大该权限边界。

### 7.3 Range 语义

- 无 Range 请求返回 `200` 和完整流；合法单区间 Range 返回 `206`、`Content-Range`、`Accept-Ranges: bytes` 和正确长度。
- 不支持多区间请求；非法或越界 Range 返回 `416`。
- Content-Type、Content-Length、ETag/Last-Modified 以安全可控方式透传；Content-Disposition 不允许 CRLF 注入。

## 8. 权限与安全

- multipart 使用 `sys:file:upload`；任务列表使用 `asset:job:list`；重试使用 `asset:asset:edit`；管理端 play-info 使用
  `asset:asset:list`。
- 文件代理不能仅凭 fileInfoId 放行，必须校验素材归属、当前机构和调用场景；未授权统一不暴露对象键、bucket 或预签名地址。
- 内部源文件端点不使用 Sa-Token，但必须校验一次性签名令牌、绑定任务和有效期，并限制 OnlyOffice 可访问的文件。
- 日志只记录 jobId、assetId、failureCode、耗时和已脱敏对象键，不记录凭据或完整令牌。

## 9. 数据影响

现有 `sts_asset_asset`、`sts_asset_derivative`、`sts_asset_transcode_job` 字段足以支撑转码终态和派生文件。Task 1
新增 `sts_file_multipart_upload` 会话表，以数据库事实绑定 MinIO uploadId 与调用人、机构、文件声明、状态和 24h 到期时间；分片明细仍由
MinIO `ListParts` 返回，不在数据库重复保存。

任何字段、索引或约束变化都必须同步 `backend/sql/init/` 最新完整模块、`backend/sql/migration/` Admin 增量、init schema
version、
Entity、OpenSpec design 和 OpenAPI。

## 10. 配置与部署

部署文档必须覆盖：

- MinIO 私有 bucket、应用最小权限账号、endpoint 与健康检查；
- FFmpeg/ffprobe 版本、可执行权限、CPU/内存和至少 6GB 临时空间；
- OnlyOffice 9.2、JWT、中文字体注入、容器到后端内部源文件地址的网络互通；
- Nginx/网关的请求体、超时、Range 和 HLS MIME 配置；
- 所有新增环境变量、启动前检查、备份范围、故障排查和回滚到 `storage.type=local` 的限制。

配置切换只影响新上传文件；已登记在 local 的历史对象不能仅靠切换配置自动迁移，历史文件迁移不属于本 Change。

## 11. TDD 与验证

每个新增或修改行为必须先观察正确层级的自动化测试失败，再以最小实现通过：

- 零 DB：状态机、条件抢占编排、失败分类、超时、重试、Provider 命令构造与超时、OnlyOffice 请求/JWT、play-info、权限和 Range 解析。
- MySQL IT：真实 Mapper 抢占、并发唯一性、派生 upsert、结果原子性、逻辑删除与分页/状态筛选。
- MinIO IT：真实容器或测试机 MinIO 的上传、读取、Range、删除、multipart 中断续传、complete checksum 和过期 abort。
- 真机冒烟：FFmpeg 视频/音频样本、OnlyOffice 中文 docx/pptx 样本、原生 PDF 页数、MinIO 产物与 API 播放/预览链。
- Word 预检：零 DB 覆盖标准 DOCX、旧 `.doc`、VML 文本框、损坏/ZIP bomb 风险包；MinIO IT 覆盖 multipart 拒绝后的对象与会话补偿。
- 契约：OpenAPI JSON、所有 `$ref`、Controller-operation、Schema-DTO/VO 对齐。

Docker、MinIO、FFmpeg 或 OnlyOffice 不可用时必须如实记录未执行项，不得以 stub 或零 DB 测试代替真实验证。
