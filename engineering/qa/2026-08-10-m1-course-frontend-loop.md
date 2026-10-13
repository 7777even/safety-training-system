# M1 课程闭环前端联调 QA 记录

## 本轮范围

- 创建 M1 独立 OpenSpec，明确课程管理、学员学习和学习事实回查范围。
- 修复移动端课程列表真实接口请求参数位置。
- 复核阶段 A 删除能力的前端和菜单残留。

## 已执行验证

| 验证项          | 命令                                                                                                                   | 结果     |
|--------------|----------------------------------------------------------------------------------------------------------------------|--------|
| 移动端课程定向测试    | `node --test frontend/mobile-app/tests/learner-course-learning.test.mjs`                                             | 3/3 通过 |
| 移动端类型检查      | `pnpm --filter @safety-training/mobile-app exec vue-tsc --noEmit --ignoreDeprecations 5.0`                           | 通过     |
| 阶段 A 综合定向测试  | `node --test frontend/pc-web/tests/system-admin-closure.test.mjs frontend/pc-web/tests/latest-openapi-sync.test.mjs` | 6/6 通过 |
| 阶段 A PC 类型检查 | `pnpm --filter @safety-training/pc-web exec vue-tsc --noEmit --ignoreDeprecations 5.0`                               | 通过     |
| 差异检查         | `git diff --check`                                                                                                   | 通过     |

## 未完成验收

- 本机后端未监听 8080，尚未执行管理员发布课程、学员报名/学习、管理员回查事实的真实端到端验收。
- PC Web 课程生产路径和移动端学习路径仍需在可访问后端环境逐接口验收后，才能关闭 M1 Task。
- 默认移动端 `typecheck` 脚本受现有 `tsconfig` 的 `ignoreDeprecations` 配置阻断；使用兼容命令验证通过。
