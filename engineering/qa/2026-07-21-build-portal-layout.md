# QA：前台门户统一布局

## 范围

OpenSpec Change：`2026-07-21-build-portal-layout`。

本次仅覆盖 PC Web 的菜单驱动门户路由、统一布局和五个页面骨架；不覆盖尚未建设的课程、考试、培训及教学辅助业务功能。

## 验证结果

- `node --test frontend/pc-web/tests/clean-modern-style-tokens.test.mjs frontend/pc-web/tests/portal-layout-routing.test.mjs`：通过，8 项测试全部成功。
- `pnpm --filter @safety-training/pc-web run lint`：通过，零警告。
- `pnpm --filter @safety-training/pc-web run typecheck`：通过。
- `pnpm --filter @safety-training/pc-web run build`：通过。
- `git diff --check`：通过，无空白错误。
- 路由结构核对：所有 `/portal/*` 菜单统一收敛到 `/portal` 父路由，`PortalLayout` 在 `.portal-content` 使用 `RouterView` 渲染子页面。
- 菜单组件核对：`portal/home`、`portal/class`、`portal/exams`、`portal/training`、`portal/teaching-aids` 均存在可解析页面；四个非首页页面只包含中文占位提示，未新增 API 请求或模拟数据。
- 独立代码审查：未发现 P0/P1 问题。

## 已知限制

- 未在带真实登录态的浏览器会话中进行端到端菜单点击验证；本次以路由结构自动测试、类型检查和生产构建覆盖。
- 菜单配置中四个非首页入口的 `sortOrder` 均为 `1`，前台导航最终相对顺序依赖 profile 返回顺序；本次未修改后端菜单数据。
