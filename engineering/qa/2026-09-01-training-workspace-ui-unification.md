# M3 培训工作台 UI 统一 QA

- 日期：2026-09-02
- 范围：培训标准、模板、评价表单、培训计划四个 PC 工作台的详情页骨架与步骤组件拆分。

## 验收结果

- 结构回归：先新增并观察模板、评价表单和计划工作台仍向步骤组件传递子模板的失败测试；迁移后 `node --test tests/training-workspace-ui.test.mjs tests/training-task1-pages.test.mjs tests/training-template-pages.test.mjs`，18/18 通过。
- 类型检查：`pnpm --filter @safety-training/pc-web exec vue-tsc --noEmit --pretty false` 通过。
- 受影响源码 ESLint：四个培训目录及其私有步骤组件定向检查通过；全培训模块检索 `<slot` 与 `$slots` 无匹配。
- 生产构建：`pnpm --filter @safety-training/pc-web build -- --outDir .task6-build` 通过。
- 差异检查：`git diff --check` 通过。
- UI 标准返工复验：四个 workspace 各自仅保留一个 `admin-detail-page__surface`；14 个步骤组件均使用 `admin-detail-page__card`，培训模块检索无自定义 `<slot>` / `$slots`，且无组件嵌套 surface。

## 已知限制

- `pnpm exec openspec validate 2026-09-01-sync-m3-training-frontend --strict` 通过；补齐了与既有 `spec-delta.md` 一致的机器可解析 Delta 文件。
- 独立构建目录 `frontend/pc-web/.task6-build/` 为验证产物，未触碰默认 `dist`；按仓库删除规则未自动递归清理。
