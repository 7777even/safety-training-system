# Retro：前端工程规范与基础设施

## 做得好的地方

- 使用 pnpm workspace 精确安装依赖，并生成根目录唯一 `pnpm-lock.yaml`。
- PC Web 与移动端分别采用 Axios 和 `uni.request`，未强行复用平台不兼容的请求实现。
- 通过构建、lint、typecheck 和静态边界检查确认未引入业务页面、业务接口或禁止依赖。

## 问题与原因

- 初始 ESLint flat config 的加载顺序使 TypeScript parser 覆盖 Vue parser，导致 `.vue` 解析失败。
- 移动端 tsconfig 初始未显式加载 `@dcloudio/types`，`uni` 全局类型未被 typecheck 识别。
- 用户提供的 Prettier `ignorePath` 与 `requireConfig` 属于 CLI 选项，写入配置文件后被当前版本忽略。

## 改进措施

- 在 Vue 项目中将 TypeScript ESLint 基础配置置于 Vue flat config 前，并以 `vue-eslint-parser` 处理 `.vue` 文件。
- uni-app 新增 TypeScript 请求封装时，先校验 `@dcloudio/types` 的 RequestOptions 定义并在 tsconfig 引入类型包。
- 新增格式化配置后，使用 `prettier --check` 校验字段兼容性，避免将 CLI 选项写入 `.prettierrc`。
