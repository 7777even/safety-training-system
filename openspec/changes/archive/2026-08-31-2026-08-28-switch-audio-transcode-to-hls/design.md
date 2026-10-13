# Design：音频转码改为 HLS 按片播放

## Context

见 `proposal.md` 的 Why。当前约束：

- Owner 为 `lims`，只改后端、`docs/api/`、架构/部署文档和工程记录；前端以 OpenAPI 为准另开。
- VIDEO 已产出 5 秒独立切片 HLS，文件代理 `GET /api/asset/files/{assetId}/hls/{jobId}/{name}` 已支持 m3u8/ts 与 Range。
- AUDIO 走同一 `FfmpegProvider`，但命令是 `-c:a libmp3lame` 单文件；play-info 按 `TRANSCODED` → `mp3`。
- `TranscodeResultService` 只对 `VIDEO_TRANSCODE` 强制 HLS+THUMBNAIL；AUDIO 空产物仍可能被当成成功（除 stub 直接失败外）。
- 派生唯一键为 `(asset_id, derivative_type, deleted)`。AUDIO 与 VIDEO 是不同素材行，二者都可以拥有 `HLS`。
- 私有对象必须带 Bearer；原生 `<audio src>` 加不上该头，这是前端整包下载的原因。本设计用与视频相同的 HLS 代理解决，不新做鉴权通道。

## Goals / Non-Goals

**Goals:**

- 新 AUDIO 任务与视频共用切片时长、独立切片标志、对象前缀和 HLS 代理。
- 成功提交边界拒绝没有 HLS 的音频结果。
- play-info 音频只认 HLS；无 HLS 时返回 `original`，不回退 `mp3`。
- OpenAPI 与转码设计/部署文档与上述语义一致。

**Non-Goals:**

- 不为音频生成封面、多码率或 MP3 并行产物。
- 不改调度、权限、表结构，不做历史数据重转。
- 不实现前端播放器、Mock 或 Service Worker。

## Decisions

### D1 音频切片用 AAC HLS，不用 MP3 容器切 HLS

- **选择**：`-vn -c:a aac -b:a 128k`，HLS muxer 参数与视频音轨对齐：
  `-hls_time 5 -hls_flags independent_segments -hls_playlist_type vod`，写出 `index.m3u8` + `segment-%05d.ts`。
- **理由**：浏览器和现有 hls.js 对 AAC-in-TS 支持成熟；视频已经依赖 AAC/HLS。MP3-in-HLS 兼容性差。
- **备选**：单文件 MP3 + 自管 Range / 预签名 URL。否决：要新鉴权胶水，且无法复用已有分片代理。
- **备选**：音频也强制关键帧表达式。否决：无视频轨，独立切片由 AAC 访问单元边界承担即可。

### D2 复用视频对象前缀与 HLS 代理，不新增 URL

- **选择**：音频产物键为 `derivative/{assetId}/{jobId}/hls/`（可带租户前缀），play-info url 形如
  `/api/asset/files/{assetId}/hls/{jobId}/index.m3u8`。
- **理由**：`AssetMediaAccessService.hls` 已按对象键解析 jobId 并校验 `derivative_type=HLS`，音频无需新接口。
- **备选**：仍写 `transcoded/audio.mp3` 另开音频分片 API。否决：重复代理与权限逻辑。

### D3 音频成功只要 HLS，不要 THUMBNAIL，也不再写 TRANSCODED

- **选择**：`AUDIO_TRANSCODE` 护栏：artifacts 必须含 `HLS`；缺则 `TRANSCODE_ERROR` 并走既有 cleanup。不要求封面。Provider
  不再上传 MP3。
- **理由**：课程音频没有封面需求；再产 MP3 会双写存储且 play-info 语义分叉。下载继续用源文件。
- **备选**：同时保留 MP3 供下载。否决：源文件下载已存在，本 Change 不新增转码下载格式。

### D4 play-info 只认 HLS，切断 mp3

- **选择**：AUDIO 主派生只找 `HLS` → `format=hls`；没有 HLS（含仅有遗留 `TRANSCODED`）→ `original`。OpenAPI `format` 枚举删除
  `mp3`。
- **理由**：人工确认不再兼容历史 MP3。旧 SUCCEEDED 音频若未重转，play-info 表现为派生缺失，与视频缺 HLS 同一信号。
- **备选**：保留 `mp3` 只读兼容。否决：用户明确不需要。

### D5 不改表、不改 job_type

- **选择**：继续 `AUDIO_TRANSCODE`；派生类型用已有 `HLS`。无 migration。
- **理由**：表注释已含 HLS；唯一键按素材隔离。改 job_type 会牵动任务列表与重试，无收益。

### D6 ts 的 Content-Type

- **选择**：沿用现有非 m3u8 分片 `video/mp2t`。音频 HLS 仍是 MPEG-TS。
- **理由**：hls.js 与 Safari 按 TS 容器识别，不按“是否有画面”。

## Risks / Trade-offs

- [Risk] 前端仍按 `mp3` 整包下载，新音频返回 `hls` 后若未改播放器会预览失败。→ Mitigation：OpenAPI 写明 AUDIO 成功为 `hls`
  ；本 Change 不改前端，确认点已声明前端另开。上线顺序：先合后端契约，前端改完再把 ffmpeg 音频任务放进共享环境，或接受短暂不可预览。
- [Risk] 共享库上 stub 实例仍可能抢到音频任务并 `PROVIDER_DISABLED`。→ Mitigation：非本 Change；部署只保留
  `TRANSCODE_PROVIDER=ffmpeg` 的一台调度。
- [Risk] 切片使对象数增加（约每 5 秒一片）。→ Mitigation：与视频同一策略；音频片约 80KB，可接受。
- [Risk] 遗留仅有 TRANSCODED 的 SUCCEEDED 音频 play-info 变为 `original`，前端无法再按 mp3 播放。→
  Mitigation：不自动重转；需要可播时走失败重试或重新上传。前端以 OpenAPI 另开，不再依赖 `mp3`。
- [Risk] 真机 FFmpeg 缺 AAC。→ Mitigation：视频已要求 AAC；冒烟覆盖音频 HLS 生成 m3u8+ts。

## Migration Plan

1. 实现与测试通过后部署 Admin。新上传/失败重试的 AUDIO 走 HLS。
2. 已 SUCCEEDED 且仅有 TRANSCODED 的音频 play-info 返回 `original`，不批量重转、不删派生行。
3. 回滚：恢复音频 Provider 为 MP3 命令即可；已写成 HLS 的素材 play-info 仍可播，回滚后新任务重新变 MP3。不写 down migration。
4. 若需把历史 MP3 也改成 HLS：仅 FAILED 可走现有重试；SUCCEEDED 重转需另开 Change，本 Change 不做。

## Open Questions

无。切片参数、产物类型和 play-info 语义已确认；历史 MP3 不兼容。
