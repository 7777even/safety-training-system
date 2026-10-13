# QA：后台基础交互规范优化

## 范围

- Element Plus 全局中文文案。
- 用户、角色、菜单、组织、字典管理页的分页、筛选控件与表单提示。
- Vue 模板格式化与 ESLint 规则一致性。

## 验证结果

- 根组件通过 `ElConfigProvider` 注入 `zhCn`，分页器和表格空数据等 Element Plus 系统文案使用中文。
- 所有后台列表主请求的默认 `pageSize` 统一使用 `DEFAULT_LIST_PAGE_SIZE = 20`；角色和字典下拉加载的 100 条请求以独立常量命名，未与列表分页混用。
- 查询区 `ElSelect`、`ElTreeSelect` 使用固定 214px 宽度，避免行内表单下拉收缩。
- 已补齐当前系统设置页面可编辑输入与选择组件的中文占位提示。
- 已关闭与 Prettier 输出冲突的 `vue/max-attributes-per-line` 和 `vue/singleline-html-element-content-newline` 纯排版规则，格式化后不再产生对应告警。

## 执行命令

- `pnpm exec prettier --check ...`
- `pnpm lint`
- `pnpm typecheck`
- `pnpm build:pc`
- `git diff --check`

以上命令均通过。用户已取消自动化测试框架，本次未新增测试框架或测试用例。
