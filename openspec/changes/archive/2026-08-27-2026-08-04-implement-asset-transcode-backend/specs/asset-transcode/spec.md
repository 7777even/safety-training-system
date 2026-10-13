# 素材真实转码与媒体派生

## ADDED Requirements

### Requirement: 部署环境文件必须由私有 MinIO 持久化

系统 MUST 在 `storage.type=minio` 时把新上传源文件和派生文件保存到配置的私有 MinIO bucket，并以服务端生成的对象键隔离源文件和每个任务的派生文件。系统
MUST NOT 向调用方暴露永久凭据或无需鉴权的永久对象地址。

#### Scenario: 上传文件到 MinIO

- **WHEN** 有上传权限的用户完成合法文件上传
- **THEN** 系统把完整对象保存到 MinIO 并登记唯一 fileInfoId
- **AND** 数据库记录的大小、checksum 和对象实际内容一致

#### Scenario: MinIO 配置或 bucket 不可用

- **WHEN** 应用以 MinIO 模式启动但必要配置缺失、bucket 不存在或不可访问
- **THEN** 系统快速失败并给出不包含凭据的诊断信息
- **AND** 不静默回退到应用机本地目录

### Requirement: 大文件上传必须支持安全的断点续传

系统 MUST 提供 multipart 初始化、分片、完成和取消能力，并 MUST 将上传会话绑定到调用人、文件声明、checksum、大小和有效期。完成前的对象
MUST NOT 成为可引用文件。

#### Scenario: 中断后恢复上传

- **WHEN** 同一调用人在有效期内以相同文件声明恢复未完成上传
- **THEN** 系统返回该会话已成功上传的分片
- **AND** 调用方只需补传缺失分片

#### Scenario: 拒绝冒用上传会话

- **WHEN** 其他用户、其他机构或不一致文件声明尝试使用已有 uploadId
- **THEN** 系统拒绝访问或完成该上传
- **AND** 不泄露已上传分片或对象信息

### Requirement: 转码任务必须异步、可抢占且可恢复

系统 MUST 在素材创建事务之外异步处理转码任务。每个 PENDING 任务 MUST 通过数据库条件更新由单一执行实例抢占；超时 RUNNING 任务
MUST 被回收为明确失败状态。

#### Scenario: 多个实例竞争同一任务

- **WHEN** 两个调度实例同时扫描到同一 PENDING 任务
- **THEN** 只有一个实例把任务条件更新为 RUNNING 并执行 Provider
- **AND** 另一个实例不得下载源文件或生成派生文件

#### Scenario: 运行任务超时

- **WHEN** RUNNING 任务超过配置的 30 分钟阈值
- **THEN** 巡检将任务和素材置为 FAILED
- **AND** failureCode 为 TIMEOUT，任务只可通过人工操作重试

### Requirement: 音视频必须生成标准化派生文件和可信时长

系统 MUST 使用 FFmpeg/ffprobe 处理上传的 VIDEO 和 AUDIO。VIDEO MUST 生成 H.264/AAC 720p 单码率 HLS 与封面；AUDIO MUST 生成
128kbps MP3；素材 MUST 回写可信 durationSeconds。

#### Scenario: 视频转码成功

- **WHEN** 调度器处理受支持且完整的视频源文件
- **THEN** MinIO 中存在可播放的 m3u8、ts 分片和封面文件
- **AND** 派生记录、durationSeconds、Job 与 Asset 的 SUCCEEDED 状态在同一结果事务提交

#### Scenario: 音视频源文件无效

- **WHEN** ffprobe 无法解析源文件或文件声明与内容不符
- **THEN** Job 与 Asset 进入 FAILED 且 failureCode 为 SOURCE_INVALID 或 UNSUPPORTED_CODEC
- **AND** 不产生可用派生记录

### Requirement: 文档必须按格式形成稳定预览事实

系统 MUST 将 doc/docx/ppt/pptx 通过启用 JWT 的 OnlyOffice 转换为 PDF 并回写 pageCount。原生 PDF MUST
直接解析页数且不创建任务；xls/xlsx MUST 保持原文件预览语义且不创建转码任务。

#### Scenario: 中文 Office 文档转换成功

- **WHEN** 调度器处理合法的中文 docx 或 pptx
- **THEN** 系统生成可打开且中文字体正常的 PDF 派生文件
- **AND** pageCount 与 PDF 实际页数一致

#### Scenario: OnlyOffice 回拉源文件

- **WHEN** OnlyOffice 使用内部源文件地址读取待转换文件
- **THEN** 系统只接受绑定 jobId/fileInfoId 且未过期的一次性令牌
- **AND** 令牌不能读取其他文件或在任务外重复使用

### Requirement: 转码失败必须分类且支持受控人工重试

系统 MUST 使用稳定 failureCode 表示源文件、编码、引擎、超时和存储故障。系统 MUST NOT 自动重试；只有失败且类型可转码的素材可由有权限用户人工重试。

#### Scenario: 人工重试可恢复故障

- **WHEN** 有权限用户重试一个可重试的 FAILED 任务
- **THEN** 系统清理当前失败信息、增加 retryCount 并将任务重新置为 PENDING
- **AND** 不覆盖或删除任何旧成功派生文件

#### Scenario: 失败信息脱敏

- **WHEN** Provider 或存储抛出包含内部 URL、令牌或凭据的异常
- **THEN** failureReason 只保存限长脱敏摘要
- **AND** API 与日志不返回密钥或完整查询令牌

### Requirement: 播放和预览文件必须经过权限控制并支持 Range

系统 MUST 为可用素材提供 play-info，并通过受控文件代理读取私有 MinIO 对象。文件代理 MUST 校验素材归属和访问场景，并支持合法单区间
HTTP Range。

#### Scenario: 读取视频分片

- **WHEN** 有权访问 SUCCEEDED 素材的调用方请求 play-info 和 HLS 分片
- **THEN** 系统返回可用格式、受控地址、封面和时长信息
- **AND** m3u8 与其相对 ts 分片均经过相同权限边界

#### Scenario: 按字节范围读取文件

- **WHEN** 有权调用方发送合法单区间 Range 请求
- **THEN** 系统返回 HTTP 206、正确 Content-Range 和精确字节内容
- **AND** 非法、多区间或越界 Range 返回 HTTP 416

#### Scenario: 拒绝跨机构文件枚举

- **WHEN** 调用方枚举其他机构的 assetId、fileInfoId 或对象路径
- **THEN** 系统拒绝访问且不暴露资源是否存在、bucket 或对象键

