# Tasks：音频转码改为 HLS 按片播放

> 状态：已完成
>
> Owner：lims
>
> 执行约束：确认后一次只执行一个 Task；行为修改采用 Red → Green → Refactor，Review 通过后再更新状态与进度记录。单次最多连续完成三个
> Task。前后端影响一律为「仅后端；前端以 OpenAPI 为准另开」。

## 1. 完成音频 HLS 转码与成功产物护栏

- 状态：已完成
- Owner：lims
- 输入：`FfmpegCommandFactory` / `FfmpegProvider` 现有 MP3 音频路径、`TranscodeResultService` 视频护栏、
  `FfmpegRealProcessTest`、`TranscodeResultServiceIT`、`TranscodeTenantContextIT`，以及本 Change 的 specs/design。
- 输出：AUDIO 产出 AAC 128kbps 5 秒独立切片 HLS；成功提交必须含 HLS 且不写 TRANSCODED/THUMBNAIL；相关零 DB、IT 与真机冒烟。
- 前后端影响：仅后端；前端以 OpenAPI 为准另开。
- API 依赖：本 Task 不改 OpenAPI 路径；产物类型变为 HLS，供 Task 2 的 play-info 消费。
- 数据影响：无 migration。新成功任务写入 `sts_asset_derivative.derivative_type=HLS`；不处理历史 TRANSCODED 行。
- 测试方式：先改/新增能表达预期的零 DB 测试并观察失败；最小实现转绿；涉及结果落库的 IT 一并更新并运行；本机有 ffmpeg
  时跑音频真机测试。

- [x] 1.1 将音频 FFmpeg 命令从 `libmp3lame` MP3 改为 AAC HLS（`-vn`、`-c:a aac`、`-b:a 128k`、`-hls_time 5`、
  `independent_segments`、`vod`），并更新 `FfmpegCommandFactoryTest`，使命令断言覆盖上述参数且不再要求 `libmp3lame`
- [x] 1.2 让 `FfmpegProvider` 的 AUDIO 路径上传 `hls/index.m3u8` 与 ts 分片，登记 `HLS` 派生（对象前缀与视频相同），不再产出
  `audio.mp3` / `TRANSCODED`；用对象键与产物类型测试验证
- [x] 1.3 扩展 `TranscodeResultService`：`AUDIO_TRANSCODE` 空产物或不含 HLS 时抛出 `TRANSCODE_ERROR` 且不落库；含 HLS、不含
  THUMBNAIL 时可成功。更新 `TranscodeResultServiceTest`，先 Red 后 Green
- [x] 1.4 更新把 `AUDIO_TRANSCODE` 成功写成 `TRANSCODED` 的 IT（含 `TranscodeResultServiceIT`、`TranscodeTenantContextIT`
  ）为 HLS 产物，运行 `mvn -pl sts-service-admin -am test` 与 `-Pintegration-test` 中受影响 IT，确认音频成功落 HLS、缺 HLS
  不成功
- [x] 1.5 更新 `FfmpegRealProcessTest`：短音频样本生成 m3u8 与至少一个 ts，不再断言 MP3 文件；本机无 ffmpeg 时如实报告跳过，不得用零
  DB 代替

## 2. 完成音频按片播放契约并切断遗留 MP3

- 状态：已完成
- Owner：lims
- 输入：Task 1 的 HLS 派生、`AssetMediaAccessService` play-info、`docs/api/asset.openapi.json`、转码架构与部署文档。
- 输出：新音频 play-info 返回 `hls` 与既有 HLS 代理地址；无 HLS（含仅有遗留 TRANSCODED）返回 `original`；OpenAPI 删除 `mp3`
  枚举。
- 前后端影响：仅后端；前端以 OpenAPI 为准另开。
- API 依赖：更新 `GET /api/asset/assets/{assetId}/play-info` 的 `format` 说明与 example：`hls` 适用于已转码视频和已转码音频；删除
  `mp3`。不新增路径或权限。
- 数据影响：无。不删除遗留 TRANSCODED 行，但 play-info 不再使用它们。
- 测试方式：零 DB 覆盖 HLS 与遗留 TRANSCODED→original；契约四条铁律自检；`git diff --check`；完成后写 QA/Retro 并回填进度表。

- [x] 2.1 修改 play-info：AUDIO 有 HLS 时 `format=hls` 且 url 为 `/api/asset/files/{assetId}/hls/{jobId}/index.m3u8`
  （含租户前缀对象键）；更新 `AssetMediaAccessServicePlayInfoTest` 并先观察旧「TRANSCODED→mp3」用例对有 HLS 场景失败
- [x] 2.2 仅有 TRANSCODED、无 HLS 的 SUCCEEDED 音频返回 `format=original`；缺 HLS 与缺两种派生同一信号。用零 DB 测试锁住，不再返回
  `mp3`
- [x] 2.3 按 OpenAPI 编写规范核对 `docs/api/asset.openapi.json`：`format` 中文说明 `hls` 含音视频；删除 `mp3` 枚举；补齐音频成功
  example
- [x] 2.4 核对 `docs/architecture/asset-transcode-design.md`、`docs/deployment/asset-transcode-backend.md`、
  `docs/deployment/customer-environment-survey.md` 已写入的音频 HLS 口径与实现一致（AUDIO → 5 秒 AAC HLS，无封面）
- [x] 2.5 双轴 Review 通过后更新本 `tasks.md` 状态、`engineering/qa/`、`engineering/retro/` 与进度表对应行；运行
  `npx @fission-ai/openspec validate --strict --change "2026-08-28-switch-audio-transcode-to-hls"` 与 `git diff --check`

## 3. 完成前端音频 HLS 播放对接

- 状态：已完成
- Owner：当场授权前端
- 输入：已确认 play-info 音频成功为 `hls`，契约删除 `mp3`。
- 输出：PC / 移动端播放器按 `format=hls` 走现有 HLS 分片代理；不再保留 MP3 整包播放路径。
- 前后端影响：前端对接已确认 OpenAPI；不改后端。
- API 依赖：`GET /api/asset/assets/{assetId}/play-info`，`format=hls`。
- 数据影响：无。不兼容历史上仅有 TRANSCODED 的音频预览。
- 测试方式：`pnpm --filter @safety-training/pc-web typecheck`、`pnpm --filter @safety-training/mobile-app typecheck`。

- [x] 3.1 PC `AssetViewer` 与学员学习页：音视频成功均走 hls.js；类型与 Mock 删除 `mp3`
- [x] 3.2 移动端 `AssetPreview`：AUDIO 成功走与视频相同的 HLS 播放器；删除 InnerAudio 整包路径
