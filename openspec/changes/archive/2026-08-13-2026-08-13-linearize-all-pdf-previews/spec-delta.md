# Spec Delta：所有 PDF 预览文件线性化

## 修改要求

### Requirement：文档必须按格式形成稳定且可渐进读取的预览事实

系统 MUST 将 doc/docx/ppt/pptx 通过启用 JWT 的 OnlyOffice 转换为 PDF，并 MUST 对转换结果执行 qpdf 线性化。系统 MUST 对原生上传
PDF 执行同一线性化流程。该要求适用于所有文件大小，MUST NOT 按阈值跳过。xls/xlsx MUST 保持原文件预览语义且不创建文档预览任务。

只有最终线性化 PDF 完成完整性、线性化状态和页数校验后，系统才可登记当前 `PREVIEW`、回写 pageCount 并将素材置为 `SUCCEEDED`
。源文件 MUST 保持不变。

#### Scenario：线性化原生 PDF

- **WHEN** 用户创建源文件为合法 PDF 的 DOCUMENT 素材
- **THEN** 系统创建异步 `DOCUMENT_PREVIEW` 任务并保持素材不可预览
- **AND** 任务对源文件执行 qpdf 线性化，不因文件较小而跳过
- **AND** 成功后 play-info 指向线性化 PREVIEW，而不是源 PDF

#### Scenario：线性化 Office 转换 PDF

- **WHEN** OnlyOffice 成功返回 docx 或 pptx 的 PDF 转换结果
- **THEN** 系统在登记和上传 PREVIEW 前执行 qpdf 线性化
- **AND** 最终 PDF 可读取、线性化校验通过且 pageCount 与转换结果一致

#### Scenario：拒绝不可用的线性化结果

- **WHEN** qpdf 不可执行、超时、返回非零状态，或最终 PDF/页数校验失败
- **THEN** 新素材和任务进入明确失败状态且不登记可用 PREVIEW
- **AND** 系统清理任务临时文件及未登记对象，错误信息不暴露内部路径或凭据

#### Scenario：渐进读取线性化 PDF

- **WHEN** 有权调用方通过 PDF.js 打开当前 PDF PREVIEW
- **THEN** play-info 返回既有受控文件地址
- **AND** 文件代理继续支持合法单区间 Range，并返回 HTTP 206、正确 Content-Range 和精确字节内容

### Requirement：历史 PDF 保持既有预览事实

系统 MUST NOT 因本 Change 扫描、重新排队或替换历史 PDF 素材及其 PREVIEW。

#### Scenario：发布后存在历史 PDF

- **WHEN** 系统发布时已经存在 PDF 素材或 Office PDF PREVIEW
- **THEN** 系统不为其自动创建线性化任务
- **AND** 既有素材状态和预览事实保持不变
