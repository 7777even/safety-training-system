# Tasks：支持权限受限 PDF 的派生预览

> 当前状态：Task 1 已完成（2026-08-17）。

## Task 1：完成无打开密码权限受限 PDF 的安全预览能力

- 状态：已完成（2026-08-17）
- Owner：lims
- 输入：已确认 proposal/design；现有 `PdfPageTreeNormalizer`、`PdfLinearizer`、`DocPreviewProvider` 和
  `TranscodeJobProcessor`；约 63.86 MiB、2517 页真实权限受限 PDF 样本
- 输出：仅对派生 PREVIEW 移除加密限制的生成链、可诊断的失败日志、自动化和真实样本验收证据、同步的架构/部署/QA/Retro/进度记录
- 前后端影响：仅后端；前端以 OpenAPI 为准另开
- API 依赖：无新增或变更；继续复用既有素材 play-info 和受控文件 Range 接口
- 数据影响：不修改数据库和 SOURCE；只改变新生成或重试生成的派生 PREVIEW 文件保护状态
- 验收标准：
  - [x] 空用户密码、禁止修改/组装但可直接读取的 PDF 能成功生成 PREVIEW
  - [x] 仅 normalized/PREVIEW 移除加密，SOURCE 对象内容和保护状态保持不变
  - [x] 非空打开密码或 PDFBox 无法读取的 PDF 继续安全失败，不尝试破解或保存密码
  - [x] PREVIEW 仍只通过既有 Bearer 鉴权、素材权限和租户隔离文件代理访问，不产生公共对象 URL
  - [x] 输出页数、页序、页面内容和 Page Tree 结构保持正确，并通过 PDFBox 重开、`qpdf --check` 和
    `qpdf --check-linearization`
  - [x] 规范化异常保留底层 cause；服务端日志可定位任务和异常栈，任务失败原因不泄漏 Token、密码、内部 URL 或凭证
  - [x] 2517 页真实样本完整链通过，最终页数仍为 2517；现有非加密 PDF 回归通过
  - [x] 不新增生产依赖，不修改 API Contract、系统权限、数据库、对象存储访问策略或 Range 语义
- 测试方式：按 TDD 先新增权限受限 PDF 失败测试并观察 Red；实现最小修复后运行相关零 DB 测试和真实 PDFBox/qpdf
  样本测试；完成规格符合性与代码质量双轴 Review；本 Task 无持久化变更，不新增数据库 IT
