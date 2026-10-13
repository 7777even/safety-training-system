# Design：M1 前端基础与课程真实闭环

## 1. 依据

- API：`system-admin.openapi.json`、`org.openapi.json`、`common-audience.openapi.json`、`resource-grant.openapi.json`、`asset.openapi.json`、`course.openapi.json`。
- 业务：`docs/requirement/role-workflows.md` 第 3、5.1、7 节。
- UI：`docs/design/clean-modern-style-baseline.md`、`web-design-spec.md`、`mobile-design-spec.md`。

本 Change 不新增业务字段、错误码、权限点或状态流转；页面与 Mock 均严格使用上述 Contract。

## 2. 信息架构与数据流

### 2.1 PC 管理端

- 系统管理的部门表单增加机构、部门编码、类型和描述；角色表单增加机构、角色级别、数据范围及 CUSTOM 部门范围。
- 企业管理提供机构成员、标签和岗位；课程配置提供参与范围与资源授权；监管关系位于组织协作配置。
- 参与范围编辑使用规则列表、成员预览/冻结和统一人员选择器；资源授权与监管关系提供分页 CRUD。
- 现有素材、课程、开课、报名及管理端学习详情页面保持页面入口和交互不变，仅以 API 边界切换真实数据源。

### 2.2 学员端

- PC 门户提供“我的课程”、课程详情、报名/退选、学习进度、会话上报和课程评价。
- 移动端在既有“学习”Tab 中提供课程列表、详情和学习任务；复用 Contract 类型，但采用卡片、状态文案和 44px 以上触控目标。
- 学员端只调用本人范围的学习、报名与评价接口；前端不以角色名称推断访问权限。

## 3. 模块边界

- PC API、Mock 与页面分别置于 `frontend/pc-web/src/api`、`mock`、`views`；移动端按同样职责置于 `frontend/mobile-app/src`。
- 仅将 PC 与移动端实际共用且字段一致的课程列表/学习模型提取至 `frontend/shared`；不共享 UI、路由或端特定状态。
- API 层保留页面无感的数据源选择。后端未完成的补充接口默认走 Contract Mock；待人工确认接口可用且联调通过后，再逐接口移除对应 Mock 分支。

## 3.1 菜单配置交付清单

每新增动态菜单页面，交付记录必须列出菜单名称、路由路径、组件路径和菜单权限；前端不直接修改后端菜单种子或生产菜单数据。

| 页面 | 路由路径 | 菜单组件路径 | 菜单权限 |
| --- | --- | --- | --- |
| 机构标签 | `/org/tag` | `system/org/tag/index` | `org:tag:list` |
| 机构岗位 | `/org/position` | `system/org/position/index` | `org:position:list` |
| 机构成员 | `/org/member` | `system/org/member/index` | `org:member:list` |
| 参与范围 | `/common/audiences` | `system/common/audience/index` | `common:audience:list` |
| 监管关系 | `/org/regulatory-relation` | `system/org/regulatory-relation/index` | `org:regulatory:list` |
| 资源授权 | `/common/resource-grants` | `system/common/resource-grant/index` | `common:grant:list` |

## 4. 权限、状态与错误处理

- 菜单入口仍按 profile 权限数据展示；本 Change 新增页面的按钮暂不做前端权限校验，数据范围和真正鉴权均由后端执行。
- 分页使用 `DEFAULT_LIST_PAGE_SIZE` 与 Contract 参数；更新请求携带 `version`。
- `2040` 乐观锁冲突提示刷新；401/403、参数错误、业务状态不允许、删除保护和无数据均显示中文反馈。
- 删除、发布、退选、冻结等不可逆或状态改变操作使用二次确认。

## 5. TDD 与验证

每个 Task 先增加可失败的 Node 行为测试：API 参数/响应解包、Mock 与真实边界、页面关键状态及权限入口。先观察失败，再以最小实现通过。完成 Task 后运行定向测试、PC 或移动端 `lint`、`typecheck`、`build`，并完成规格符合性和代码质量双轴 Review。

## 6. 风险与取舍

- API 不足以提供课程发现列表时，不臆造聚合接口；仅按当前 Contract 能读取的本人报名/学习记录交付，并在测试中固定该边界。
- 文件服务没有预览/播放 URL 时，不呈现伪造媒体能力。
- 当前 M1 后端已实现；真实 API 不可用时如实记录联调阻塞，不以 Mock 作为 M1 关闭证据。
