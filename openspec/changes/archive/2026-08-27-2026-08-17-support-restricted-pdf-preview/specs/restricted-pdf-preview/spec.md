# 权限受限 PDF 派生预览

## ADDED Requirements

### Requirement: 可直接读取的权限受限 PDF 必须能够生成受控 PREVIEW

当 PDF 无需用户输入密码即可由系统读取，但携带禁止修改或文档组装的加密权限时，系统 MUST 允许其进入既有 PREVIEW 生成链。系统
MUST 只对派生 PREVIEW 移除 PDF 内部加密限制，MUST NOT 修改或覆盖 SOURCE。

#### Scenario：上传空用户密码的权限受限 PDF

- **WHEN** PDFBox 可以无密码读取上传 PDF，但文档带加密字典并禁止修改或组装
- **THEN** 系统仅在派生文档保存前显式移除其安全保护
- **AND** SOURCE 的对象内容和保护状态保持不变
- **AND** 最终 PREVIEW 页数、页序和页面内容与 SOURCE 一致
- **AND** 最终 PREVIEW 通过 Page Tree、PDF 完整性和线性化校验

#### Scenario：PDF 需要非空打开密码

- **WHEN** 系统无法使用空密码读取 PDF
- **THEN** PREVIEW 任务以源文件不可读失败
- **AND** 系统不得猜测、请求持久化或记录文档密码
- **AND** 不得上传或登记半成品 PREVIEW

### Requirement: 移除内部 PDF 保护不得绕过系统访问控制

移除派生 PREVIEW 的 PDF 内部加密后，系统 MUST 继续使用既有私有对象存储、租户隔离、素材权限和 Bearer 鉴权文件代理提供访问。系统
MUST NOT 因派生文件未加密而生成公共对象地址或匿名访问路径。

#### Scenario：访问已移除内部保护的 PREVIEW

- **WHEN** 客户端请求权限受限 SOURCE 对应的 PREVIEW
- **THEN** 请求仍经过既有素材权限和租户边界校验
- **AND** 合法请求继续支持 HTTP Range
- **AND** 未授权请求不得获得 PREVIEW 内容或对象存储地址

### Requirement: PDF 规范化失败必须保留安全诊断信息

PDF PREVIEW 规范化失败时，系统 MUST 为服务端诊断保留底层异常类型和堆栈，同时 MUST 对持久化任务失败原因进行清洗。诊断信息
MUST NOT 记录 Token、密码、Authorization、对象存储凭证或带敏感查询参数的 URL。

#### Scenario：PDFBox 保存派生文件失败

- **WHEN** PDFBox 在加载、页面树处理或保存阶段抛出异常
- **THEN** 任务失败原因使用稳定、安全的业务描述
- **AND** 服务端日志包含任务标识、失败码和原始异常栈
- **AND** 清理本次生成的全部中间文件
- **AND** SOURCE 保持不变
