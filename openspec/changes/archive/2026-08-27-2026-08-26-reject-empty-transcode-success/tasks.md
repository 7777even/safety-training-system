# Tasks：拒绝素材转码空产物成功

> 状态：已人工确认（2026-08-26）；Task 1 已完成。
>
> Owner：lims
>
> 执行约束：确认后一次只执行一个 Task；行为修改采用 Red → Green → Refactor，Review 通过后再更新状态与进度记录。

## Task 1：保证视频转码成功对应完整派生产物

- 状态：已完成（2026-08-26）
- Owner：lims
- 输入：现有 `TranscodeJobProcessor`、`TranscodeResultService`、FFmpeg/stub Provider、任务失败与人工重试链路，以及线上
  `SUCCEEDED + 0 派生文件` 复现证据。
- 输出：拒绝空产物和不完整视频产物的统一结果护栏、修正后的零 DB 自动化测试、QA/Retro 与进度记录。
- 前后端影响：仅后端；前端以 OpenAPI 为准另开。
- API 依赖：沿用既有素材转码任务查询与人工重试 API；不修改 `docs/api/asset.openapi.json`。
- 数据影响：无数据库结构或 migration 变化；不处理历史伪成功数据，仅纠正新执行任务的状态提交。
- 验收标准：
  - [x] `VIDEO_TRANSCODE` 返回空 artifacts 时，Job 与 Asset 进入 `FAILED`，`failureCode=TRANSCODE_ERROR`，不得写入派生记录。
  - [x] Stub Provider 不再返回空成功结果；被误用于任务执行时记录 `FAILED/PROVIDER_DISABLED`，原因明确说明当前环境未启用真实转码服务。
  - [x] 视频结果缺少 HLS 或 THUMBNAIL 任一必需类型时不得提交成功，已上传的本任务对象进入既有清理流程。
  - [x] 同时具备 HLS 与 THUMBNAIL 的视频结果仍原子登记派生记录并提交 `SUCCEEDED`。
  - [x] 修正既有“视频空结果视为成功”的错误测试，并覆盖空结果、不完整结果、完整结果与失败原因脱敏。
  - [x] 除同步 `PROVIDER_DISABLED` 失败码说明外，不修改 API 结构、权限、数据库、前端或无关转码逻辑。
- 测试方式：先修改/新增 Admin 零 DB 测试并观察预期失败；最小实现转绿；运行受影响的转码目标测试和 Admin 零 DB 测试；执行
  `openspec validate --strict`、`git diff --check`，完成规格符合性与代码质量双轴 Review。

### 完成记录

- Red 1：新增结果提交护栏测试后，空结果、缺 HLS、缺 THUMBNAIL 三个用例均继续进入旧成功提交并按预期失败。
- Green 1：`TranscodeResultService` 在任何文件或派生记录落库前校验视频必须同时具有 HLS 与 THUMBNAIL；缺失时抛出
  `TRANSCODE_ERROR`，由既有 Processor 输出错误日志、清理任务产物并记录 Job/Asset 失败。
- Red 2：使用真实 `StubTranscodeProvider` 的编排测试证明 stub 仍返回空结果并调用成功提交。
- Red 3：将 stub 预期收紧为专用 `PROVIDER_DISABLED` 后，测试捕获到旧实现仍返回通用 `TRANSCODE_ERROR`。
- Green 2：stub 改为直接抛出 `PROVIDER_DISABLED/当前环境未启用真实转码服务，任务未执行`，不再产生假返回；目标测试
  11/11 通过，并同步 `asset.openapi.json` 失败码说明。
- 回归：`mvn -pl sts-service-admin -am test` 共 691 个测试，0 失败、0 错误、1 个既有 qpdf 条件跳过；最终目标测试
  11/11 通过。
- 未运行：未修改 Entity、Mapper、自定义 SQL、schema、索引、逻辑删除、分页、乐观锁或真实持久化事务，按 Admin 分层规则未运行
  MySQL IT；未操作线上历史伪成功数据。
- 双轴 Review：规格符合性“通过”；代码质量“通过”。除新增 `PROVIDER_DISABLED` 契约说明外，无 API 结构、权限、数据库、生产依赖、
  前端或无关重构变化；原有超时竞态测试保持覆盖。
- QA：`engineering/qa/2026-08-26-reject-empty-transcode-success.md`；Retro：
  `engineering/retro/2026-08-26-reject-empty-transcode-success.md`。
