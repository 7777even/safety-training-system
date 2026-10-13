# QA：PC 菜单动态路由与按钮权限展示

## 范围

OpenSpec Change：`2026-07-17-foundation-auth-organization-permission` 的 Task 9。

## 验证结果

- PC Web 只保留 `/login` 静态路由；门户、系统管理和系统管理子页面均由 `GET /api/system/users/profile` 的 `menus` 动态注册。
- 当前接口误返回首页路径 `/dashboard` 时，动态路由注册为实际门户 `/portal`；接口修正为 `/portal` 后走同一目录约定，无需改动前端。
- 每个顶层 `/admin` 菜单均使用 `AdminLayout`，并作为后台左侧一级菜单显示；直接访问 `/admin/overview` 时，首页概览页面仍渲染在后台框架内。
- 后台子页面兼容 `component: "system/..."` 与 `component: "admin/system/..."` 两种格式，均按 `src/views/admin/system/...` 目录约定解析；组件不存在时根据菜单 path 尝试目录约定。课程分类菜单使用与 `admin/class/classify/index` 对齐的页面入口。
- 门户顶部导航由 profile 的非 `/admin` 顶层菜单生成，学习、考试和证书入口只有在接口返回对应菜单后才展示。
- 应用入口已加载 Element Plus 全局样式，`ElMessage` 使用标准浮层定位和视觉样式。
- 动态路由 `meta` 从已有菜单字段生成 `title`、`requiresAuth`、`menuId`、`menuType` 和 `icon`；无需变更 profile API Contract。
- 登录、会话恢复、用户切换和退出登录都会同步或移除动态路由；登录默认进入菜单顺序中第一个可解析页面。
- 用户、角色、菜单、组织和字典页面的操作按钮仅递归读取可见 `F` 菜单节点的 `perms`，不再依赖 profile 顶层 `perms`。
- `pnpm --filter @safety-training/pc-web typecheck` 通过。
- `pnpm --filter @safety-training/pc-web lint` 通过。
- `pnpm build:pc` 通过。

## 已知限制

- 自动化测试框架按用户要求未启用，未新增测试用例。
- 需要在具备真实角色菜单的后端环境中，使用浏览器完成一次登录、退出和菜单切换联调。
