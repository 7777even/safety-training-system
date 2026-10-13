# Design：权限受限 PDF 的派生预览处理

## 1. 失败根因

现有链路如下：

```text
SOURCE InputStream
  → 临时 source.pdf
  → PDFBox 加载并重建 Page Tree
  → PDFBox 保存 normalized.pdf
  → qpdf --linearize
  → 完整性、线性化、页数和 Page Tree 校验
  → 上传 PREVIEW
```

PDFBox 3.0.5 可以使用空用户密码打开权限受限 PDF，但文档仍处于加密状态。重建 Page Tree 后直接调用 `PDDocument.save`
时，PDFBox 因文档携带 encryption dictionary 而拒绝保存。当前规范化器将该 `IllegalStateException` 转换成不带 cause
的统一异常，处理器也没有记录异常栈，因此任务只能看到“PDF 页面树规范化失败”。

## 2. 派生文件保护边界

采用以下处理顺序：

```text
成功加载 SOURCE
  → 判断 PDDocument.isEncrypted()
  → 仅在当前文档已成功打开时，对内存中的派生文档设置移除安全保护
  → 固化页面继承属性并重建 Page Tree
  → 保存 normalized.pdf
  → qpdf 线性化与既有校验
  → 通过受控对象键上传 PREVIEW
```

约束：

1. `source.pdf` 是任务临时副本，除读取外不修改；对象存储 SOURCE 不发生写入。
2. 移除保护只影响随后保存的 `normalized.pdf` 及最终 PREVIEW。
3. PREVIEW 仍使用既有私有对象键、素材权限、租户隔离和受控文件代理；不新增公开下载地址。
4. PDFBox 无法用空密码加载时，在进入规范化前失败；本 Change 不增加密码参数、密码库或重试猜测。
5. 非加密 PDF 保持现有执行路径和输出校验。

## 3. PDFBox 实现

`PdfPageTreeNormalizer.normalize` 在成功加载 `PDDocument` 后、保存前显式处理安全状态。仅当 `document.isEncrypted()` 为真时调用
PDFBox 提供的派生保护移除能力；不修改 SOURCE 文件，不重新设置未知 owner/user 密码。

处理后继续复用现有逻辑：

- 快照页面 COS 字典并保持顺序。
- 固化 `Resources`、`MediaBox`、`CropBox`、`Rotate` 等继承属性。
- 最大扇出 32 重建 `/Pages` 层级。
- 使用非压缩传统 xref 保存中间文件。
- qpdf 负责最终压缩和线性化。
- 最终再次验证页数、节点类型、`Parent`、`Count` 和扇出。

不得通过捕获保存异常后自动执行外部 `qpdf --decrypt` 重试；安全处理应在已知文档状态下显式完成，避免产生两条行为不一致的生成路径。

## 4. 失败与可观测性

任务对外失败原因继续使用稳定、无内部路径的中文消息。服务端诊断需要记录：

- 转码任务 ID、素材 ID和失败码。
- 原始异常类型及堆栈。
- 不记录 Token、密码、Authorization、带查询参数 URL 或对象存储凭证。

`TranscodeFailureException` 应保留底层 cause，`TranscodeJobProcessor` 在写入清洗后的任务失败原因前记录安全上下文和异常栈。媒体流输出期间的客户端断开属于独立
HTTP 生命周期，不作为 PDF 规范化失败依据。

## 5. TDD 与验证设计

先新增自动化测试并观察现有实现失败：

1. 使用 PDFBox 生成带空用户密码、禁止修改和文档组装的加密 PDF。
2. 调用真实 `PdfPageTreeNormalizer`；现有实现应在保存阶段得到“PDF 页面树规范化失败”。
3. 实现后断言输出可以无密码重开、页数和页面内容保持、输出不再加密、Page Tree 满足最大扇出。
4. 非空用户密码 PDF 继续无法由无密码链路读取，不生成输出文件。
5. 非加密 PDF 现有 65 页、1025 页、继承属性、书签和注释测试继续通过。
6. 失败异常保留 cause，处理器记录诊断但写入数据库的原因不包含敏感信息。

定向验证：

- 零 DB：规范化器、线性化器、文档预览 Provider 和任务处理器测试。
- 真实进程：指定 2517 页样本，验证 PDFBox 重开、页数一致、`qpdf --check`、`qpdf --check-linearization`。
- 本 Change 不涉及 Entity、Mapper、自定义 SQL、schema、索引或事务落库语义，不要求新增数据库 IT。

## 6. 文档同步与回滚

实现完成后更新既有 PDF 渐进预览架构说明、转码部署说明、QA/Retro 和进度表。回滚时恢复拒绝此类 PDF 的旧行为不会影响
SOURCE；已生成 PREVIEW 仍由既有私有对象访问控制保护，可按既有任务清理机制处理。
