# Tasks

## Task 1：完成素材上传适配器【已完成】

- Owner：fxw11
- 输入：现有 system-admin OpenAPI 文件上传和 multipart 契约
- 输出：小文件/大文件策略选择、进度、恢复、取消、重试
- 前后端影响：仅前端；不修改后端和 OpenAPI
- API 依赖：SysFileInfoController、SysFileMultipartController
- 数据影响：无
- 验收标准：上传完成返回现有 `fileInfoId`；multipart complete 请求字段完全匹配现有契约
- 测试方式：Node 行为测试、PC lint/typecheck/build
- 当前进度：已完成 20 MiB 阈值策略、单文件上传、multipart init/parts/complete、SHA-256、进度回调、失败重试、AbortSignal 暂停/继续，以及调用现有 multipart DELETE 的取消清理。
- 已有证据：`frontend/pc-web/src/api/asset.ts`、`frontend/pc-web/src/components/file-upload/UnifiedFileUpload.vue`、`frontend/pc-web/src/components/file-upload/upload-queue.ts`、`frontend/pc-web/tests/asset-upload-adapter.test.mjs`；Node 行为测试、受影响文件 lint、PC build 已通过。

## Task 2：完成素材库上传后创建与状态展示【已完成】

- Owner：fxw11
- 输入：现有 asset OpenAPI
- 输出：创建素材、PENDING/RUNNING/SUCCEEDED/FAILED 展示、失败重试
- 前后端影响：仅前端；不修改后端和 OpenAPI
- API 依赖：素材创建、详情、转码任务、重试接口
- 数据影响：无
- 验收标准：上传完成不直接视为可学习；成功/失败状态清晰可见
- 测试方式：组件测试和 PC 构建
- 当前进度：已完成上传成功后写入 `fileInfoId`、上传完成后再创建素材、待处理/处理中/成功/失败状态展示、状态自动刷新、失败重试、转码任务查看，以及仅在 `SUCCEEDED` 状态允许预览。现有契约使用 `UPLOADED / PROCESSING / SUCCEEDED / FAILED`，页面分别表达为“待处理/处理中/处理成功/处理失败”。
- 已有证据：`frontend/pc-web/src/views/admin/business-center/resource-library/index.vue`、`frontend/pc-web/src/constants/asset.ts`、`frontend/pc-web/src/mock/asset.ts`、`frontend/pc-web/tests/task1-asset-management.test.mjs`；素材库相关 Node 测试通过，PC build 已通过。

## Task 3：完成统一素材预览【已完成】

- Owner：fxw11
- 输入：现有 asset play-info 与受控文件代理接口
- 输出：AssetViewer 和素材详情预览
- 前后端影响：仅前端；不修改后端和 OpenAPI
- API 依赖：asset play-info、文件代理、Range/HLS
- 数据影响：无
- 验收标准：视频、音频、PDF、图片、文本和不支持格式均有明确行为
- 测试方式：格式路由测试、PC build；真机验收另行执行
- 当前进度：已完成 `AssetViewer`、受控 `play-info` 请求、HLS.js/原生 HLS 降级、原生音频、PDF.js、图片、文本、安全下载兜底和不支持格式提示；DOC/DOCX/PPT/PPTX 依赖后端 PDF 派生结果，不在浏览器直接渲染 Office，Excel 无派生结果时进入下载兜底。
- 已有证据：`frontend/pc-web/src/components/asset-viewer/AssetViewer.vue`、`frontend/pc-web/src/views/admin/business-center/resource-library/index.vue`、`frontend/pc-web/src/mock/asset.ts`、`frontend/pc-web/tests/task1-asset-management.test.mjs`；格式路由测试、受影响文件 lint、PC build 已通过。真实后端素材样本和移动端真机属于环境联调，不改变本前端 Task 的完成结论。

## 进度汇总

| Task | 实现状态 | 验收状态 | 当前结论 |
| --- | --- | --- | --- |
| Task 1 上传适配器 | 已完成 | 自动化验证通过 | 已关闭 |
| Task 2 上传后创建与状态 | 已完成 | 自动化验证通过 | 已关闭 |
| Task 3 统一素材预览 | 已完成 PC 端 | 自动化验证通过 | 已关闭 |

因此，本 Change 当前整体状态为：**前端实现与自动化验收完成**。

## 验证记录

- `node --test frontend/pc-web/tests/asset-upload-adapter.test.mjs frontend/pc-web/tests/task1-asset-management.test.mjs frontend/pc-web/tests/course-workflow-optimization.test.mjs`：17/17 通过。
- 受影响文件 ESLint：通过。
- `pnpm --filter @safety-training/pc-web build`：通过；仅保留既有大体积 chunk 警告。
- `openspec validate 2026-08-06-implement-asset-library-frontend --type change`：通过。
- `git diff --check`：通过。
- `pnpm --filter @safety-training/pc-web typecheck`：存在既有 `src/mock/question.ts:134` 类型错误；本次素材库改动未引入新的 typecheck 错误，未修改无关题库代码。
