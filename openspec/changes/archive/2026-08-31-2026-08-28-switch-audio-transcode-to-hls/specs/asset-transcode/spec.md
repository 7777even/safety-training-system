## MODIFIED Requirements

### Requirement: 音视频必须生成标准化派生文件和可信时长

系统 MUST 使用 FFmpeg/ffprobe 处理上传的 VIDEO 和 AUDIO。VIDEO MUST 生成 H.264/AAC 最高 720p 单码率 HLS 与封面；AUDIO MUST
生成
AAC 128kbps、目标 5 秒独立切片的音频 HLS（m3u8 与 ts），且 MUST NOT 生成封面。素材 MUST 回写可信 durationSeconds。系统 MUST
NOT
将空产物、缺少任一必需视频派生类型、或缺少音频 HLS 的结果提交为成功。新执行的 AUDIO 任务 MUST NOT 再将单文件 MP3（
`TRANSCODED`）登记为成功派生产物。

#### Scenario: 视频转码成功

- **WHEN** 调度器处理受支持且完整的视频源文件
- **THEN** MinIO 中存在可播放的 m3u8、ts 分片和封面文件
- **AND** 结果包含 HLS 与 THUMBNAIL 派生记录
- **AND** 派生记录、durationSeconds、Job 与 Asset 的 SUCCEEDED 状态在同一结果事务提交

#### Scenario: 音频转码成功

- **WHEN** 调度器处理受支持且完整的音频源文件
- **THEN** MinIO 中存在可播放的音频 m3u8 与至少一个 ts 分片
- **AND** 结果包含 HLS 派生记录，且不包含 THUMBNAIL
- **AND** 派生记录、durationSeconds、Job 与 Asset 的 SUCCEEDED 状态在同一结果事务提交
- **AND** 不得写入 `TRANSCODED` MP3 派生记录

#### Scenario: 视频 Provider 返回空产物

- **WHEN** VIDEO_TRANSCODE Provider 未抛异常但返回空 artifacts
- **THEN** 系统拒绝成功提交，将 Job 与 Asset 更新为 FAILED
- **AND** failureCode 为 TRANSCODE_ERROR，不产生派生记录

#### Scenario: 音频 Provider 返回空产物或不含 HLS

- **WHEN** AUDIO_TRANSCODE Provider 未抛异常但返回空 artifacts、或 artifacts 不含 HLS
- **THEN** 系统拒绝成功提交，将 Job 与 Asset 更新为 FAILED
- **AND** failureCode 为 TRANSCODE_ERROR，不产生派生记录

#### Scenario: 环境配置为 Stub Provider

- **WHEN** 转码任务由未执行真实转码的 Stub Provider 处理
- **THEN** 系统将 Job 与 Asset 更新为 FAILED，failureCode 为 PROVIDER_DISABLED
- **AND** failureReason 说明当前环境未启用真实转码服务且任务未执行，不得返回空结果或记录成功

#### Scenario: 视频 Provider 返回不完整产物

- **WHEN** VIDEO_TRANSCODE Provider 只返回 HLS 或只返回 THUMBNAIL
- **THEN** 系统拒绝成功提交并进入既有失败与对象清理流程
- **AND** 不保留半成品派生记录，不把素材开放给下载、编辑或课程制作

#### Scenario: 音视频源文件无效

- **WHEN** ffprobe 无法解析源文件或文件声明与内容不符
- **THEN** Job 与 Asset 进入 FAILED 且 failureCode 为 SOURCE_INVALID 或 UNSUPPORTED_CODEC
- **AND** 不产生可用派生记录

### Requirement: 播放和预览文件必须经过权限控制并支持 Range

系统 MUST 为可用素材提供 play-info，并通过受控文件代理读取私有 MinIO 对象。文件代理 MUST 校验素材归属和访问场景，并支持合法单区间
HTTP Range。SUCCEEDED 的 AUDIO 若存在 HLS 派生，play-info 的 format MUST 为 `hls`，url MUST 指向与视频相同的 HLS 受控代理。没有
HLS 的 SUCCEEDED 音频（含仅有遗留 `TRANSCODED`）play-info 的 format MUST 为 `original`。系统 MUST NOT 再返回 `format=mp3`
。系统
MUST NOT 向调用方暴露永久凭据或无需鉴权的永久对象地址。

#### Scenario: 读取视频分片

- **WHEN** 有权访问 SUCCEEDED 素材的调用方请求 play-info 和 HLS 分片
- **THEN** 系统返回可用格式、受控地址、封面和时长信息
- **AND** m3u8 与其相对 ts 分片均经过相同权限边界

#### Scenario: 读取音频 HLS 分片

- **WHEN** 有权访问已成功转码且具有 HLS 派生的 AUDIO 素材请求 play-info
- **THEN** 系统返回 format=`hls`、指向该任务 m3u8 的受控 HLS 地址，以及 durationSeconds
- **AND** 调用方随后请求的 m3u8 与相对 ts 分片均经过与视频相同的权限边界和 Range 语义
- **AND** 不得要求音频必须存在封面

#### Scenario: 遗留音频仅有 TRANSCODED 派生

- **WHEN** 有权访问 SUCCEEDED 的 AUDIO，且派生中只有 `TRANSCODED`、没有 HLS
- **THEN** 系统返回 format=`original`
- **AND** 不删除、不改写该遗留派生记录，也不把它当作可播格式

#### Scenario: 按字节范围读取文件

- **WHEN** 有权调用方发送合法单区间 Range 请求
- **THEN** 系统返回 HTTP 206、正确 Content-Range 和精确字节内容
- **AND** 非法、多区间或越界 Range 返回 HTTP 416

#### Scenario: 拒绝跨机构文件枚举

- **WHEN** 调用方枚举其他机构的 assetId、fileInfoId 或对象路径
- **THEN** 系统拒绝访问且不暴露资源是否存在、bucket 或对象键
