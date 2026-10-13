# 后台模块顶栏导航实施计划

**目标：** 以 profile `menus` 为唯一数据源，将后台导航改为顶层模块顶栏和当前模块按需左侧导航。

**范围：** 只修改动态路由解析、后台布局和既有 OpenSpec Change；不修改 API、后端菜单数据、数据库或业务页面。

## 文件职责

- `frontend/pc-web/src/constants/admin-navigation.ts`：定义模块导航项与其默认入口路径。
- `frontend/pc-web/src/router/admin-dynamic-routes.ts`：解析顶层 `D` 模块、`M/C` 后代、模块根重定向和当前模块定位。
- `frontend/pc-web/src/layouts/AdminLayout.vue`：展示模块顶栏、条件侧栏和当前页面内容。
- `openspec/changes/2026-07-17-foundation-auth-organization-permission/{design.md,spec-delta.md,tasks.md}`：记录并验收此调整。

## 实施与验收

- [x] 顶层 `/admin` 且 `menuType: D` 的已解析菜单生成模块项；模块 `landingPath` 为首个叶子页面，独立页面则为自身路径。
- [x] 当前模块采用完整路径边界匹配，并按路径长度降序选择，防止祖先模块覆盖子模块。
- [x] 有已解析后代的模块根路由注册空路径重定向到首个叶子，确保刷新、直访和顶栏点击行为一致。
- [x] `AdminLayout` 顶栏仅显示模块；仅当前模块有 `M/C` 后代时渲染左侧菜单；按钮 `F` 不参与路由和导航。
- [x] 运行 `pnpm --filter @safety-training/pc-web typecheck`。
- [x] 运行 `pnpm --filter @safety-training/pc-web lint`。
- [x] 运行 `pnpm build:pc`。
- [x] 静态扫描确认路由与布局中不存在“首页概览、业务中心、系统管理”模块名称分支。
- [x] 独立代码审查已完成；发现的模块祖先路径匹配和模块根空白问题均已修复并复审通过。
