# Design：基础工程初始化

## 技术选择

- PC Web：Vite + Vue 3 + TypeScript。
- 移动端：官方 uni-app Vue 3 + Vite 模板。
- 后端：Eclipse Temurin JDK 21 LTS + Spring Boot 3.4.13 + Maven。

## 工程边界

PC Web 仅建立根 layout 和 `/portal`、`/user-center`、`/admin`
的空白壳层路由；它们不包含业务页面、菜单或权限判断。移动端只提供应用入口与通用基础布局，不实现具体业务流程。后端只建立启动类及
Controller、Service、Mapper、Entity、DTO、VO、Config、Exception 包，不声明业务接口。

## 协作边界

`frontend/shared/` 保持无运行时依赖，仅用于未来已确认契约产生的公共类型和纯工具。未定义 API Contract，因此本 change 只建立
API/Mock 目录和说明文件，不自行定义接口。
