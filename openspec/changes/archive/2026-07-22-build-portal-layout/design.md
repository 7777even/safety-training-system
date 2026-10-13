# Design：前台门户统一布局与子页面承载

## 系统设计

动态路由继续以用户资料中的 `menus` 为唯一来源。五个前台菜单均为非 `/admin` 顶层页面，现有路由解析器会将其注册为独立路由，并按其
`component` 字段解析组件。

本次将门户页面关系调整为：

```text
动态菜单路由
├─ /portal/home          -> views/portal/home/index.vue
├─ /portal/class         -> views/portal/class/index.vue
├─ /portal/exams         -> views/portal/exams/index.vue
├─ /portal/training      -> views/portal/training/index.vue
└─ /portal/teaching-aids -> views/portal/teaching-aids/index.vue

views/portal/index.vue
├─ 顶部品牌、动态导航和用户菜单
└─ .portal-content
   └─ RouterView
```

由于附件菜单均直接解析到各自页面组件，`portal/index.vue`
作为统一布局时需要在路由构建中成为这些入口的共同父级；路由调整仅改变前台路由的组件嵌套方式，不改变菜单路径、菜单顺序或授权过滤规则。

## 页面职责

| 页面                               | 职责                      |
|----------------------------------|-------------------------|
| `portal/index.vue`               | 统一展示门户顶栏，并在内容容器渲染当前子页面。 |
| `portal/home/index.vue`          | 承接现有首页的静态概览、待办和快捷入口。    |
| `portal/class/index.vue`         | 显示“课程中心”建设中的最小占位提示。     |
| `portal/exams/index.vue`         | 显示“考试中心”建设中的最小占位提示。     |
| `portal/training/index.vue`      | 显示“培训中心”建设中的最小占位提示。     |
| `portal/teaching-aids/index.vue` | 显示“教学辅助”建设中的最小占位提示。     |

## 路由与权限

- 菜单的 `path`、`component`、`menuName`、`sortOrder` 继续由 profile 返回数据决定。
- 门户顶栏仅显示已授权且可见的非 `/admin` 顶层菜单。
- `RouterView` 只渲染已由现有动态路由注册的页面；不新增前端静态菜单映射。
- 管理后台入口的可见性继续由已有菜单数据推导，不硬编码角色。

## API 与数据模型

不新增、删除或变更 API、请求字段、返回结构、数据模型或数据库资产。

## 技术方案

- 调整动态路由构建逻辑，使路径以 `/portal/` 开头的前台入口以门户布局为共同父路由，并保留菜单页面作为子路由组件。
- `portal/index.vue` 移除固定首页主体，仅保留框架与 `RouterView`。
- 创建五个符合附件 `component` 字段的 Vue 页面；其中首页接管原有静态内容，其余四个页面只显示中文占位信息。
- 页面使用 Vue 3 `script setup`、TypeScript 和 scoped Less，沿用现有设计 Token 与 Element Plus 组件。

## 风险与取舍

- 将 `/portal/*` 路由嵌套进统一布局会影响动态路由解析，需用路由单元测试覆盖菜单路径、导航和组件解析。
- 本次不设计业务页面内容；占位页仅保证导航和路由可用，避免凭空定义课程、考试或培训规则。
