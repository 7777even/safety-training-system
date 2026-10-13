# Proposal：音频转码改为 HLS 按片播放

## Why

当前 AUDIO 转码只产出一份 128kbps MP3（`derivative_type=TRANSCODED`）。play-info 返回 `format=mp3` 和整文件地址；管理端/学员端为带上
Bearer，会先把整份音频下载成 Blob 再播放。视频已用 5 秒 HLS 按片起播、拖动，PDF 已线性化并按 Range
加载，音频没有对等能力，长课时音频起播慢。需要把音频转码对齐视频的 HLS 切片，使成功后的 play-info 走同一套受控分片代理。

## What Changes

- AUDIO 真实转码产物从单文件 MP3 改为 **AAC 128kbps、5 秒独立切片的音频 HLS**（m3u8 + ts），`derivative_type=HLS`，对象前缀与视频相同：
  `derivative/{assetId}/{jobId}/hls/`。
- AUDIO 成功结果必须含 HLS 清单与至少一个 ts 分片；不得再将 `TRANSCODED` MP3 作为新任务成功产物。音频 **不** 生成封面。
- **BREAKING（契约语义）**：AUDIO 的 play-info `format` 不再返回 `mp3`。成功转码音频为 `hls`，url 指向既有 HLS 代理
  `.../files/{assetId}/hls/{jobId}/index.m3u8`。无 HLS 时返回 `original`。枚举删除 `mp3`。
- 结果提交护栏扩展到 `AUDIO_TRANSCODE`：缺 HLS 时 `FAILED/TRANSCODE_ERROR`，不落半成品派生。
- 同步 `docs/api/asset.openapi.json`、`docs/architecture/asset-transcode-design.md`、
  `docs/deployment/asset-transcode-backend.md`。
- 不自动重转或删除遗留 `TRANSCODED` 行；play-info 也不再把它当作可播格式。

## Capabilities

### New Capabilities

- （无）本 Change 不新增独立能力目录，只修正既有素材转码行为。

### Modified Capabilities

- `asset-transcode`：AUDIO 标准化产物从 128kbps MP3 改为 5 秒 AAC HLS；play-info 成功音频返回 `hls`；不兼容遗留
  TRANSCODED；成功提交必须具备 HLS。

## Impact

- 后端：`FfmpegCommandFactory` / `FfmpegProvider` 音频命令与产物、`TranscodeResultService` 成功护栏、
  `AssetMediaAccessService` play-info 主派生选择、相关零 DB 与 IT、FFmpeg 真机冒烟。
- API：`docs/api/asset.openapi.json` 的 play-info `format` 说明与 example；不新增路径、权限码或表结构。
- 数据：无 migration。新任务写 `HLS`；历史 `TRANSCODED` 行保留。
- 依赖：FFmpeg 已有 AAC/HLS（视频在用）；音频不再依赖 `libmp3lame` 作为成功产物编码器。
- 前后端影响：仅后端；前端以 OpenAPI 为准另开（播放器需把 AUDIO + `hls` 走现有 hls.js / 分片代理，不再整包下载 MP3）。

## 覆盖功能项

- 功能项 20：课程资源管理支持文档、音频、视频及图片的上传、下载、删除和在线预览；“上传资源后平台会对资源进行转码服务，转码中的资源不可下载、编辑及课程制作，转码成功后的资源方可进行下载编辑及课程制作”。本
  Change 让音频转码成功对应可按片播放的 HLS，与视频预览起播方式对齐。
- 功能项 22 知识点材料：章节可添加音频材料供学员学习。本 Change 只保证音频素材在转码成功后具备与视频同类的按片播放载体；不改章节编排。

## 非目标范围

- 不修改任何前端页面、Mock、联调或 UI；前端以 OpenAPI 为准另开。
- 不把音频做成视频那样的多码率 ABR，不生成音频封面，不把源文件切片，不提供单独「下载转码 MP3」接口（下载仍走原始上传文件）。
- 不引入 Service Worker、MSE 自管 Range、预签名 URL 或新的媒体鉴权方式。
- 不自动重转或删除历史 `SUCCEEDED` 且仅有 `TRANSCODED` 的音频；不操作生产数据。
- 不改变任务调度、抢占、超时、人工重试、Stub `PROVIDER_DISABLED`、权限模型或数据库结构。
- 不处理当前环境 Stub 抢占导致音频 `PROVIDER_DISABLED` 的运维配置问题（`TRANSCODE_PROVIDER` / 多实例），那是部署问题，不是本
  Change 范围。

## 人工确认点

1. 新 AUDIO 转码成功产物定为 AAC 128kbps、5 秒独立切片 HLS，不再产出 MP3 派生。
2. 成功音频 play-info 返回 `format=hls`；无 HLS（含仅有遗留 TRANSCODED）返回 `original`；契约枚举删除 `mp3`。
3. 音频成功不要求 THUMBNAIL；缺 HLS 不得 `SUCCEEDED`。
4. 不迁移、不删除遗留 MP3 派生行，但也不再通过 play-info 播放它们。
5. 实施 Owner 为 `lims`，交付后端 + OpenAPI；前端对接另开。
