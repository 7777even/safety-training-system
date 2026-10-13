# Tasks：修复转码链路的租户上下文缺失

> 状态：**已完成（2026-08-07）**，Task 1～3 全部交付。
>
> Task 1 的实现路线经人工确认为**绑定租户上下文**（design §1 方案 B），
> 而非仅在写派生处传字段——后者盖不住 provider 层 4 处受隔离表的读取。
> Task 3 的 format 枚举经人工确认按 design doc §6.3 口径收敛为
> `hls / mp3 / pdf / excel / image / original`。
>
> Owner：lims（仅后端；前端以 `docs/api/asset.openapi.json` 为准另开）
>
> 依赖顺序：Task 1 → Task 2 → Task 3。Task 1 未完成前 Task 2 的用例无从验证。
>
> 缺陷来源：`2026-08-06-rebuild-multi-tenant-isolation` Task 2 加 `NOT NULL organization_id` 后，
> 转码调度线程无租户上下文导致写派生文件失败。该 Change 的 Task 2 验收标准
> 「异步任务、MQ 消费显式设置上下文后才执行业务」已据此改回未完成，由本 Change 承接闭环。

---

## Task 1：转码执行链路显式声明租户

- 状态：已完成（2026-08-07）
- Owner：lims
- 输入：`TranscodeScheduler`、`TranscodeJobProcessor`、`TranscodeResultService` 现状；design §1、§2
- 输出：`TranscodeJobProcessor.process` 在拿到 job 后用 `TenantContext.runWithTenant` 包住整个处理过程
- 前后端影响：仅后端，无接口变化
- API 依赖：无
- 数据影响：不改表结构
- 验收标准：
  - [x] 转码成功后 `us_sys_file_info` 与 `sts_asset_derivative` 均落库，且 `organization_id` 等于任务租户
  - [x] 素材终态为 `SUCCEEDED`，不再因写派生失败被置为 `FAILED`
  - [x] `claimPending` / `selectById` 保持在租户上下文之外，调度器仍可跨租户抢占待处理任务
  - [x] `markProcessing` / `finishTranscode` / `fail` 的显式 `organizationId` 形参保留（design §2）
  - [x] 处理结束后上下文恢复，线程池复用不残留上一任务的租户
- 测试方式：零 DB 覆盖 `process` 的上下文绑定与恢复；MySQL IT 见 Task 2
- 实施补记：
  - `process(jobId)` 拆成两段：`claimPending` / `selectById` 留在上下文外（调度器需跨租户抢占，
    且拿到 job 前不知道租户是谁），随后 `TenantContext.runWithTenant(job.getOrganizationId(), ...)`
    包住 `processInTenant(job)`，覆盖 `markProcessing` → provider 执行 → `succeed`/`fail` 全程
  - **顺带修正了一个测试夹具缺陷**：`TranscodeJobProcessorTest` 构造的 job 从未设 `organizationId`，
    绑定上下文后触发「租户 ID 不能为空」。该列在库中是 NOT NULL，夹具本就不该省略，已补上。
    未为此加防御分支——为库约束保证不可能出现的状态写代码，等于加一段测不到的逻辑

---

## Task 2：用不依赖上下文绑定的测试固化约束

- 状态：已完成（2026-08-07）
- Owner：lims
- 输入：Task 1；`AbstractMysqlIntegrationTest` 在 `@BeforeEach` 绑定 DEMO 租户这一事实
- 输出：模拟调度线程形态（无上下文）的 IT；`TranscodeResultServiceIT` 补同类用例
- 前后端影响：仅后端
- API 依赖：无
- 数据影响：无
- 验收标准：
  - [x] 新增 IT 在断言前显式 `TenantContext.clear()`，模拟调度线程真实形态
  - [x] 该 IT 在修复前必须失败——**已实测**：修复前 4 例中 2 例红（派生文件计数与跨租户可见性）
  - [x] 两个租户各自的转码任务互不串扰，派生文件跨租户不可见
  - [x] `TranscodeResultServiceIT` 补一条无上下文用例，不再只在有上下文时为真
- 测试方式：MySQL IT（Testcontainers）；实施时先还原旧实现跑一次确认用例会红
- 实施补记：
  - 新增 `TranscodeTenantContextIT`（4 例）：无上下文转码成功并正确归属、派生文件跨租户不可见、
    处理后不残留上下文、任务与素材租户不一致时按失败处理
  - 测试内自备 `RecordingProvider`：像 `FfmpegProvider` 一样先 `assetMapper.selectById` 读素材
    再产出派生，并记录执行期间观察到的租户，用以断言绑定确实生效。
    生产桩 `StubTranscodeProvider` 返回空产物且不读库，直接用它测不出任何东西
  - 第 4 例是绑上下文相对"只传字段"方案的增量价值所在：跨租户读素材现在会失败，
    而不是静默按别家素材转码
- 备注：本 Change 的核心风险不是改不动，而是**改完仍然测不出来**。
  原缺陷正是被基类的租户绑定掩盖了整整一个改造周期

---

## Task 3：`play-info` 的 format 收敛到契约枚举

- 状态：已完成（2026-08-07）
- Owner：lims
- 输入：`AssetMediaAccessService.playInfo` 现状；`docs/api/asset.openapi.json` 的
  `AssetPlayInfoVO.format` 枚举 `hls/mp3/pdf/xls/xlsx/original`；design §4
- 输出：无派生文件时按 design §4 的规则取值，不再返回任意源文件后缀
- 前后端影响：仅后端；`format` 取值口径变化，前端以 OpenAPI 为准另开
- API 依赖：`GET /api/asset/assets/{assetId}/play-info` 响应字段取值收敛，字段与结构不变
- 数据影响：无
- 验收标准：
  - [x] 有派生文件时：HLS→`hls`、TRANSCODED→`mp3`、PREVIEW→`pdf`
  - [x] 无派生文件时按 asset_type 与源后缀判定，不再回显任意后缀：
    `IMAGE`→`image`；`DOCUMENT` 且后缀 pdf→`pdf`、xls/xlsx→`excel`；其余→`original`
  - [x] MUST NOT 返回 `mp4` 等任意后缀；视频缺派生返回 `original`，成为可观测异常信号
  - [x] 契约中 `format` 的 description 说明各取值含义与触发条件，并写明 `original` 的兜底语义
  - [x] `asset.openapi.json` 满足四条铁律
- 测试方式：零 DB 覆盖各分支取值；MySQL IT 覆盖有/无派生文件两种真实数据形态
- 实施补记：
  - **枚举按设计文档口径调整**：`xls`/`xlsx` 合并为 `excel`（design doc §6.3 本就写的 `excel`，
    契约此前写成两个后缀，二者不一致），并新增 `image`。
    最终枚举 `hls / mp3 / pdf / excel / image / original`，语义统一为
    「返回的 url 该用什么组件渲染」，与 §6.3 预览组件表一一对应
  - 保留 `image` 而不并入 `original` 的理由：`original` 若同时承载「正常图片」与「转码丢了的视频」，
    异常信号会被正常数据稀释，等于回到 `mp4` 的老问题。
    收敛后视频/音频/Word 出现 `original` 一定是故障，可直接用于监控
  - **顺带修掉一个坏地址**：外链（EXTERNAL）与富文本（CREATED）素材 `fileInfoId` 为 null，
    原实现会拼出 `/api/asset/files/{id}/null`。这类素材按 §6.3 走 `rich_content` /
    `external_url` 渲染，不经本接口，现返回 404（6011）
  - 三边同步：`AssetMediaAccessService`、`docs/api/asset.openapi.json`、
    `docs/architecture/asset-transcode-design.md` §6.3 与 §7 接口表
- 验收证据：
  - 零 DB `AssetMediaAccessServicePlayInfoTest`（9 例）——`playInfo` 此前没有任何 Service 层测试；
    先写用例并实测 9 例中 5 例红，再实现
  - 带 DB `AssetPlayInfoFormatIT`（6 例）覆盖有派生/无派生两种真实数据形态
  - 全量：零 DB 542 通过、带 DB 195 通过，均 0 失败
- 备注：视频返回 `original` 本身就是「转码未成功」的可观测信号，
  比返回 `mp4` 假装可直接播放更有利于发现问题
