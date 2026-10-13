# 设计

## API 边界

严格使用现有 `docs/api/system-admin.openapi.json` 与 `docs/api/asset.openapi.json`。小文件调用文件上传接口，大文件调用 multipart init、parts、complete；上传得到 `fileInfoId` 后再调用素材创建接口。

现有 multipart 恢复响应只提供 `uploadedParts`，不提供历史 ETag。因此前端恢复时对已存在分片重新上传，以重新取得 ETag 后再 complete，不扩展接口契约。

## 预览策略

- 图片：原生 `<img>`。
- 视频：HLS.js，浏览器原生 HLS 作为降级。
- 音频：原生 `<audio>`。
- PDF：PDF.js。
- DOC/DOCX/PPT/PPTX：只消费后端已生成的 PDF 派生文件。
- XLS/XLSX：有后端预览结果时展示，否则提示下载。
- TXT/Markdown：安全文本展示。
- 其他格式：明确提示不支持在线预览。

## 前端结构

- `asset.ts`：上传策略和素材接口适配。
- `AssetViewer`：统一格式路由和权限请求。
- 素材库页面：上传、创建素材、状态轮询、失败重试和预览。
- 共享类型：不与课程学习页面耦合。
