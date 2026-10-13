# QA：课程中心素材与组织范围选择优化

日期：2026-08-10
Change：`2026-08-10-optimize-course-center-scope-selectors`

## 验证结果

- TDD：先在 `course-operations-ui.test.mjs` 新增三条失败测试，分别覆盖素材详情按需加载、普通管理员机构边界与参与范围中文枚举；实现后
  12/12 通过。
- `node --test tests/course-operations-ui.test.mjs`：12/12 通过。
- `pnpm exec vue-tsc --noEmit --ignoreDeprecations 5.0`：通过。仓库常规 typecheck 仍受 `tsconfig.json` 中
  `ignoreDeprecations: "6.0"` 与当前 TypeScript 不兼容影响，未修改全局配置。
- `pnpm build`：通过。
- `git diff --check`：通过。

## 功能核验

- 素材选择器不再对分页结果逐条调用详情接口；仅打开时回显已选素材、或用户主动点击素材名称预览时读取详情。
- 普通管理员的人员、讲师和开课参与范围固定使用 `userInfo.organizationId`，不渲染机构选择器，因此不触发机构分页请求。
- 具备既有 `system:tenant:list` 权限的用户才可显示并切换机构；参与范围切换机构后重置规则，避免遗留旧机构引用。
- 参与范围的解析模式、包含/排除和主体类型均使用中文 `label` 与 Contract `value` 映射，提交值保持 OpenAPI 枚举。

## 双轴 Review

- 规格符合性：通过。实现范围仅覆盖已确认的三个 Task，未新增 API、字段、权限点、路由或数据结构。
- 代码质量：通过。删除了素材列表 N+1 详情请求；复用既有租户上下文工具和枚举常量；会话缺少机构时阻止保存并提示重新登录。
