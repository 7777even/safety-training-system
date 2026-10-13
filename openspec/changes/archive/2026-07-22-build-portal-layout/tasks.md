# Tasks：搭建前台门户统一布局

## Task 1：完成前台门户菜单的统一布局承载

- **Owner**: AI
- **输入**: 附件中的五项前台 `menus` 配置、现有 `views/portal/index.vue`、动态路由解析器。
- **输出**: `/portal/*` 菜单页面以统一门户顶栏和 `.portal-content > RouterView` 渲染；现有导航、用户菜单、退出登录及后台入口行为保持不变。
- **前后端影响**: 仅 PC Web 路由组织与布局调整；后端无影响。
- **API 依赖**: 既有 `GET /api/system/users/profile` 返回的 `menus`。
- **数据影响**: 无。
- **验收标准**:
  - 五个菜单路径继续直接来自 profile 菜单配置。
  - 访问任一 `/portal/*` 页面均显示统一门户顶栏和对应内容区。
  - 未授权或隐藏菜单不显示，也不注册为可访问页面。
- **测试方式**: 为动态路由增加或更新测试；运行 PC Web typecheck 与相关测试。
- **状态**: 已完成。

## Task 2：建立五个前台入口的可路由页面骨架

- **Owner**: AI
- **输入**: 附件中的 `component` 字段及 Task 1 的门户布局。
- **输出**: `portal/home`、`portal/class`、`portal/exams`、`portal/training`、`portal/teaching-aids`
  对应页面文件；首页保留原静态概览，其余四页提供最小中文占位提示。
- **前后端影响**: 仅 PC Web 页面文件；后端无影响。
- **API 依赖**: 无新增 API；首页沿用已存在的用户资料状态。
- **数据影响**: 无。
- **验收标准**:
  - 附件列出的每个 `component` 都可由现有目录约定解析。
  - 首页静态内容仍可显示。
  - 四个非首页入口不包含虚构业务数据、接口调用或操作入口。
- **测试方式**: 运行 PC Web typecheck 与 build；手工核对五条菜单路径的页面切换。
- **状态**: 已完成。
