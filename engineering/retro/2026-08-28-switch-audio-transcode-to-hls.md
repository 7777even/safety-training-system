# Retro：音频转码改为 HLS 按片播放

日期：2026-08-28

## 做得好的地方

- 音频切片参数与视频音轨对齐（AAC 128k、5 秒、independent_segments、vod），直接复用已有 HLS 对象前缀和文件代理。
- 先用失败测试锁住「缺 HLS 不得成功」和「有 HLS 的音频不得再走 mp3」，再改 Provider 与 play-info，避免半成品落库。
- 不自动重转遗留 TRANSCODED 行；play-info 也不再把它当可播格式。

## 问题与原因

- 一期把 AUDIO 做成单文件 MP3，play-info 只能给整文件地址；私有对象必须带 Bearer，前端只能先整包下载再播。
- `TranscodeResultService` 原先只对 VIDEO 强制 HLS+封面，AUDIO 空产物或不含 HLS 仍可能被当成成功。
- 提案阶段已把命令测试写成 AAC HLS，但实现仍是 `libmp3lame`，且路径断言写成 Windows 反斜杠，Linux 上会误判。

## 改进

- 音视频成功不变量都在统一结果提交边界校验必需派生产物；AUDIO 只要 HLS，不要封面。
- 命令与对象路径测试用 `Path.resolve`，不要写死某一种分隔符。
- 新音频 play-info 必须走与视频相同的 HLS 代理；不要为遗留 MP3 再留一条 `mp3` 播放路径。
