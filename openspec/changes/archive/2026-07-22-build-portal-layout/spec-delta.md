# Spec Delta：前台门户布局

## ADDED Requirements

### Requirement：统一前台门户布局

PC Web 必须将 `/portal/home`、`/portal/class`、`/portal/exams`、`/portal/training`、`/portal/teaching-aids`
渲染在同一门户顶层布局内。布局必须保留品牌、基于已授权菜单生成的顶栏导航、用户菜单和既有管理后台入口；其内容区域必须使用
`RouterView` 渲染当前前台子页面。

#### Scenario：用户在前台菜单间切换

- **WHEN** 用户点击一个已授权且可见的前台顶栏菜单
- **THEN** 浏览器进入该菜单配置的 `path`
- **AND** 统一门户顶栏保持显示
- **AND** `.portal-content` 中只渲染该路径对应的页面组件

### Requirement：前台菜单页面骨架

附件所列的五个 `component` 必须能按 PC Web 目录约定解析。首页组件承接现有门户首页静态内容；课程中心、考试中心、培训中心和教学辅助组件必须提供不包含业务数据或操作的中文占位提示。

#### Scenario：访问已配置的非首页入口

- **WHEN** 已授权用户访问 `/portal/class`、`/portal/exams`、`/portal/training` 或 `/portal/teaching-aids`
- **THEN** 页面在门户统一布局内显示对应中文名称和占位提示
- **AND** 页面不发起新增 API 请求
