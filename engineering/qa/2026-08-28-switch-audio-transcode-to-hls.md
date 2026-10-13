# QA：音频转码改为 HLS 按片播放

日期：2026-08-28  
OpenSpec：`openspec/changes/2026-08-28-switch-audio-transcode-to-hls/`  
Owner：lims

## 验收范围

- 新 `AUDIO_TRANSCODE` 成功产物为 AAC 128kbps、5 秒独立切片 HLS（m3u8 + ts），`derivative_type=HLS`，对象前缀与视频相同。
- 音频不生成封面，不再登记 `TRANSCODED` MP3。
- 空产物或不含 HLS 时 `FAILED/TRANSCODE_ERROR`，不落半成品派生。
- 新成功音频 play-info 返回 `format=hls` 与既有 HLS 代理地址；无 HLS（含仅有遗留 `TRANSCODED`）返回 `original`，契约删除
  `mp3`。
- 无 migration；不重转、不删除历史 MP3 派生。仅后端；前端以 OpenAPI 为准另开。

## TDD 证据

1. Red：`FfmpegCommandFactoryTest` 要求 AAC HLS 参数时旧命令仍是 `libmp3lame`；`TranscodeResultServiceTest` 音频空产物/缺
   HLS 仍会落库；`AssetMediaAccessServicePlayInfoTest` 有 HLS 的音频仍返回 `original` 或 `mp3`。
2. Green：音频命令改为 AAC HLS；Provider 上传 `hls/` 清单与 ts；结果护栏要求 AUDIO 必须含 HLS；play-info 只认 HLS，仅有遗留
   TRANSCODED 返回 `original`。
3. 真机：本机 `/usr/bin/ffmpeg` 可用，`FfmpegRealProcessTest.realFfmpegProducesAudioHlsManifestAndSegment` 生成 m3u8 与
   ts，未跳过。

## 自动化结果

| 验证                                                                                                           | 结果                          |
|--------------------------------------------------------------------------------------------------------------|-----------------------------|
| `FfmpegCommandFactoryTest`、`FfmpegProviderAudioHlsTest`、`TranscodeResultServiceTest`、`FfmpegRealProcessTest` | 15 通过，0 失败，0 跳过             |
| `AssetMediaAccessServicePlayInfoTest`                                                                        | 14 通过，0 失败                  |
| `mvn -pl sts-service-admin -am test`                                                                         | 767 通过，0 失败，1 个既有 qpdf 条件跳过 |
| `TranscodeResultServiceIT`、`TranscodeTenantContextIT`（`-Pintegration-test`）                                  | 7 通过，0 失败                   |
| `jq empty docs/api/asset.openapi.json`                                                                       | 通过                          |
| `openspec validate 2026-08-28-switch-audio-transcode-to-hls --strict --type change`                          | 通过                          |
| `git diff --check`                                                                                           | 通过                          |

## Review

- 规格符合性：通过。新音频成功只写 HLS；缺 HLS 不得 SUCCEEDED；play-info 音频只认 HLS，遗留 TRANSCODED 返回 `original`
  ；OpenAPI 删除 `mp3` 枚举并补音频成功 example。
- 代码质量：通过。未改调度、权限、表结构或前端；对象前缀复用视频 `hls/`；成功护栏在落库前失败。

## 未执行项

- 未自动重转或删除历史 `SUCCEEDED` 且仅有 `TRANSCODED` 的音频。
- 前端已按用户当场授权对接：PC/移动端成功音频与视频共用 HLS 播放器，不再保留 MP3 整包播放。未做浏览器实机点播。
- 未跑全量 MySQL IT（仅受影响 `TranscodeResultServiceIT`、`TranscodeTenantContextIT`）。
