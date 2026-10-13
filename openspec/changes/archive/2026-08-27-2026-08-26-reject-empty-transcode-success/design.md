# Design：拒绝素材转码空产物成功

## 1. 设计原则

- Owner 为 `lims`，仅实施后端及相关 OpenSpec、QA、Retro 和进度记录；前端以 OpenAPI 为准另开。
- `SUCCEEDED` 表示下游已经能够消费完整派生结果，不能只表示 Provider 没有抛异常。
- 结果完整性在统一结果提交边界校验，避免某个 Provider 或后续 Provider 漏检后写入伪成功。
- 复用既有任务失败状态、失败码、清理和人工重试链路，不新增接口或数据结构。

## 2. 成功结果护栏

`TranscodeResultService.succeed` 在写入任何文件信息、派生记录和终态之前校验 `TranscodeOutput`：

1. `output`、`artifacts` 不得为空。
2. `VIDEO_TRANSCODE` 必须同时包含且只按现有语义识别 `HLS` 与 `THUMBNAIL` 必需类型。
3. 校验失败抛出 `TranscodeFailureException("TRANSCODE_ERROR", ...)`，错误原因只说明缺失的派生类型，不包含对象路径或凭据。
4. `TranscodeJobProcessor` 复用现有异常处理：清理 Provider 已上传对象，将 Job 与 Asset 更新为 `FAILED`，保存脱敏失败原因。
5. 完整视频结果继续按原事务写入两条派生记录并提交 Job、Asset 的 `SUCCEEDED`。

校验必须先于派生文件落库，确保不完整输出不会留下半成品数据库记录。若 Provider 已经上传部分对象，现有 `cleanup(output)`
负责尽力删除本任务上传对象。

## 3. Stub 行为

保留 stub 供显式测试配置使用，但 stub 执行任务时直接抛出 `PROVIDER_DISABLED`，原因固定为“当前环境未启用真实转码服务，任务未执行”，
不得返回空结果。调度器记录包含 failureCode 的异常日志并将 Job、Asset 更新为 `FAILED`。统一结果护栏继续作为独立防线，拒绝其他
Provider 返回的空结果或不完整视频结果，并记录 `TRANSCODE_ERROR`。

## 4. API、权限与数据影响

- API：不新增或修改路径、请求、响应字段；在既有开放字符串 `failureCode` 文档中增加 `PROVIDER_DISABLED` 语义，任务查询继续通过
  `failureCode/failureReason` 返回原因。
- 权限：沿用素材任务查询与人工重试权限，不改变权限语义。
- 数据：不新增表、字段、索引、初始化数据或 migration；只纠正新执行任务的终态判定。
- 前后端影响：仅后端；前端以 OpenAPI 为准另开。

## 5. TDD 与验证

Red 阶段先修改/新增零 DB 测试并确认失败：

- `VIDEO_TRANSCODE + TranscodeOutput.empty()` 不得调用成功提交，最终进入 `FAILED/TRANSCODE_ERROR`。
- 只有 HLS 或只有 THUMBNAIL 的视频输出不得落库或提交成功。
- 同时具有 HLS、THUMBNAIL 的视频输出保持成功。

Green 阶段以最小结果校验实现通过测试；Refactor 只提取本次必要的校验方法，不重构 Provider、调度器或存储代码。完成后运行 Admin
受影响零 DB
测试；本次不涉及 Entity、Mapper SQL、schema、索引或真实持久化事务变化，不要求新增 MySQL IT。

## 6. 风险与取舍

- 修复部署后，误配 stub 的环境会从“立即成功”变为明确失败，这是预期的故障暴露，不提供静默兼容。
- 本 Change 阻止新增伪成功，不自动修复历史 `SUCCEEDED + 0 派生文件` 数据，避免未经授权修改线上业务数据。
- 本 Change 不推断大文件空输出的底层触发原因；失败状态与日志形成后，再依据 `failureReason` 和运行日志继续诊断。
