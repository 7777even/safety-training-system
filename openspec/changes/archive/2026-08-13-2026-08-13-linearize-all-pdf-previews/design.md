# Design：所有 PDF 预览文件线性化

## 1. 总体设计

```text
原生 PDF ────────┐
                 ├→ 任务临时目录 → qpdf --linearize → qpdf 校验 → PREVIEW 派生文件
Office 源文件 → OnlyOffice PDF ┘                                      ↓
                                                play-info → 受控 Range 文件代理 → PDF.js
```

所有在线预览 PDF 必须经过同一线性化器。源文件只读且保持不变；成功后登记新的 `PREVIEW` 文件事实，失败时清理任务临时文件和未登记对象。

## 2. 新文件处理

### 2.1 原生 PDF

- 创建 `DOCUMENT/UPLOAD` 素材且源文件后缀为 `pdf` 时，素材保持 `UPLOADED` 并创建 `DOCUMENT_PREVIEW/PENDING` 任务。
- Provider 从私有存储下载源 PDF，不调用 OnlyOffice。
- 输入先由 PDFBox 验证并读取页数；qpdf 线性化后再次校验输出可读且页数一致。
- 成功时登记 `PREVIEW` 派生文件并将素材置为 `SUCCEEDED`；失败时素材进入 `FAILED`，可复用既有人工重试。

### 2.2 Office 文档

- docx/ppt/pptx 沿用 OnlyOffice 转换和安全回拉边界。
- OnlyOffice 返回 PDF 后，不直接上传；先写入任务临时目录并执行 qpdf 线性化。
- 只登记线性化后的 PDF，`pageCount` 取校验后的最终文件。

## 3. qpdf 执行边界

- 新增配置：
  - `sts.transcode.qpdf-path`，默认 `/usr/bin/qpdf`，必须是绝对路径；
  - `sts.transcode.pdf-linearize-timeout-seconds`，默认 120 秒，必须为正数。
- 使用 `ProcessBuilder` 参数列表直接执行，不经过 shell：
  - `qpdf --linearize <input> <output>`；
  - `qpdf --check <output>`；
  - `qpdf --check-linearization <output>`。
- 任一进程非零退出、超时、输出不存在、PDFBox 无法读取、页数变化均归类为 `TRANSCODE_ERROR` 或 `SOURCE_INVALID`，错误摘要必须脱敏、限长。
- 临时目录必须位于 `sts.transcode.work-dir` 的任务子目录；正常、异常和中断路径均删除，禁止删除工作根目录或其外部路径。
- 应用启用真实文档转码时必须在启动阶段验证 qpdf 可执行；测试使用显式 stub/测试替身，不依赖开发机恰好安装的软件。

## 4. 派生文件

- 输出对象键继续使用 `derivative/{assetId}/{jobId}/preview/document.pdf`。
- 新素材只在 PREVIEW 文件上传、文件记录、派生记录和任务终态全部成功后变为 `SUCCEEDED`。
- 源 PDF 保持不变，继续按已有文件生命周期管理。

## 5. 历史数据边界

- 本 Change 只影响发布后新创建的原生 PDF 和 Office 文档素材。
- 不提供历史扫描、回补任务、新旧 PREVIEW 替换或历史重试语义。
- 既有 PDF 素材及其 PREVIEW 保持原状。

## 6. API 与权限

- 不新增 URL、权限或错误码。
- `POST /api/asset/assets` 的原生 PDF 示例和描述改为异步处理：返回 assetId 后状态为 `UPLOADED`，完成后 play-info 才可访问。
- `GET /api/asset/assets/{assetId}/play-info` 和 `/api/asset/files/**` 保持不变，仍校验当前机构、素材/文件关联和
  `asset:asset:list`；学员侧继续由学习域校验本人报名。
- `AssetPlayInfoVO.format=pdf` 只指向当前有效的线性化 PREVIEW，不再为新原生 PDF回退源文件。

## 7. 部署与回滚

- qpdf 安装在执行文档任务的后端主机或容器，部署检查执行 `qpdf --version`。
- 发布顺序：安装 qpdf → 配置绝对路径 → 发布应用 → 新文件冒烟。
- 回滚时源文件保持不变，可回退应用版本；新素材若已进入异步状态，由运维在回退前等待任务完成或停止调度。

## 8. 风险与取舍

- 所有 PDF 均执行外部进程会增加 CPU、磁盘 I/O 和任务延迟；用户已确认不按大小跳过，依靠既有并发和超时配置限流。
- 线性化主要改善首屏与渐进加载，不等同于压缩，也不能保证弱网瞬时打开。
- 原生 PDF 从同步完成改为异步可用，是对处理状态语义的有意调整，必须同步 OpenAPI 和回归测试。
- 历史 PREVIEW 的原子替换若受现有数据唯一性约束限制，必须先验证真实 SQL；不得在零 DB 测试中假设。
