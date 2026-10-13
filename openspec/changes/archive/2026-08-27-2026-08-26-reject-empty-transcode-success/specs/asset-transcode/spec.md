## MODIFIED Requirements

### Requirement: 音视频必须生成标准化派生文件和可信时长

系统 MUST 使用 FFmpeg/ffprobe 处理上传的 VIDEO 和 AUDIO。VIDEO MUST 生成 H.264/AAC 720p 单码率 HLS 与封面；AUDIO MUST 生成
128kbps MP3；素材 MUST 回写可信 durationSeconds。系统 MUST NOT 将空产物或缺少任一必需视频派生类型的结果提交为成功。

#### Scenario: 视频转码成功

- **WHEN** 调度器处理受支持且完整的视频源文件
- **THEN** MinIO 中存在可播放的 m3u8、ts 分片和封面文件
- **AND** 结果包含 HLS 与 THUMBNAIL 派生记录
- **AND** 派生记录、durationSeconds、Job 与 Asset 的 SUCCEEDED 状态在同一结果事务提交

#### Scenario: 视频 Provider 返回空产物

- **WHEN** VIDEO_TRANSCODE Provider 未抛异常但返回空 artifacts
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
