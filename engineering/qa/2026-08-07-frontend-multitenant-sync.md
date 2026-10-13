# 前端多租户契约同步 QA 记录

## 范围

覆盖 PC Web、移动端、shared 登录契约，租户/用户/角色管理清理，监管聚合只读视角，素材播放格式和使用说明文档。

## 验证结果

- `node --test tests/multitenant-contract.test.mjs`：5/5 通过。
- `pnpm --filter @safety-training/pc-web exec vue-tsc --noEmit --ignoreDeprecations 5.0`：通过。
- `pnpm --filter @safety-training/mobile-app exec vue-tsc --noEmit --ignoreDeprecations 5.0`：通过。
- `pnpm --filter @safety-training/pc-web build`：通过。
- `git diff --check`：通过。
- 全量 PC 测试：166 个用例中 145 个通过；21 个既有用例仍引用旧页面路径、旧分片阈值或旧组织字段，未将其冒充为本次同步通过项。

## 阻塞与处理

仓库默认 `pnpm typecheck` 使用的 `ignoreDeprecations` 值与当前 TypeScript 5.9 不兼容，因此使用同一编译器显式传入兼容值完成类型验证；未修改全局依赖或后端规则。
