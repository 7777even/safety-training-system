# 归档记录

- 归档日期：2026-08-27
- 归档原因：进度表 COURSE-02 / M6 已关闭；本 Change Task 4、Task 6 仍留有真机样本验收未勾选项，不能按 tasks.md
  全绿归档。已实现要求已同步主规格。
- 规格同步：已写入 `openspec/specs/asset-transcode/spec.md`，并合并后续 `reject-empty-transcode-success` 的空产物护栏。

## 任务状态

| Task                | 状态                                                                     |
|---------------------|------------------------------------------------------------------------|
| Task 1 MinIO 与可恢复上传 | 已完成                                                                    |
| Task 2 异步调度与失败恢复    | 已完成                                                                    |
| Task 3 音视频真实转码      | 已完成                                                                    |
| Task 4 文档预览转换       | 进行中：OnlyOffice 链路已通，复杂 WPS/VML DOCX 视觉验收未关闭；已拒绝旧 .doc 与含 VML 文本框的 docx |
| Task 5 受控播放与 Range  | 已完成                                                                    |
| Task 6 测试机验收与 M6 闭环 | 进行中：完整视频/音频/原生 PDF/Excel/失败样本统一复核未全部勾选                                 |

## 后续（不阻塞当前版本）

1. 用重新另存的标准 DOCX 做 OnlyOffice 全页视觉复核。
2. 补齐更多真实样本的上传 → 转码 → play-info/Range 复核。
3. 以上若实施，另开 Change，不要改本归档目录。
