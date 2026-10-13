# Spec Delta：工程治理基线

## 新增规范

- Monorepo 按 frontend、backend、database、docs、engineering、openspec 分层。
- 所有业务变更遵循 OpenSpec → workHarness → codeHarness → testHarness → Retro。
- 前后端遵循 API Contract First 与 Mock First。
- 权限由后端真实校验，前端只负责展示控制。
- 数据库变更必须通过 `database/migration/`。
- Git 使用 main、develop、feature/*；一个业务功能一个 feature 分支。

