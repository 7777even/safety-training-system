# Design：M4 最小界面业务闭环

## 最短业务链

```text
管理员：问卷草稿 → 题目/选项 → PC/手机预览 → 投放/扫码链接 → 本人一次提交 → 授权统计
管理员：培训已结束 → 综合成果/奖惩与学员详情 → 更新一期档案 → 企业/个人/一期查看及下载
管理员：证书模板 → 发布 → 课程/理论考试/培训签发规则 → 来源达标后自动签发 → 管理员查看
学员：个人中心 → 我的证书 → 在线查看/下载
专家：培训中打开已绑定查课表 → 提交意见 → 管理员回看
```

## 契约与数据边界

- 问卷复用 survey OpenAPI 的 CRUD、投放、`qrContent`、`resultVisibility`、填写和统计端点。选择与问卷用途一致的投放用途；不新增匿名答卷或新权限。
- 综合结果复用 training `ended-result` 的统计、`participants` 和 `rewardPenalties`；学员详情优先跳转既有培训参训详情，不重造结果计算接口。
- 档案复用 archive 的一期、企业、个人查询、`create-from-plan` 与文件端点。管理员从已结束计划上下文更新；档案总入口提供业务选择，不允许手输主键。`missingItems` 非空时明确显示并禁下载。
- 当前一期档案文件是 `application/json`，下载应使用 `.json`；个人档案文件端点修复为同格式的受控文件，内容只包含当前可见、已结束的本人培训档案事实，不伪造文件 ID。
- 证书复用 certificate 的模板、规则、签发结果及本人文件端点；在线预览和下载使用同一受控文件，遵守当前登录态与后端授权。本人列表按 API 分页，不只显示前 20 条。
- 不改现有业务表；菜单新增属于正式初始化数据，需同步 init 菜单种子、幂等 Flyway、schema-version 与菜单权限测试。不得复用可能冲突的线上菜单 ID。

## 路由与菜单

- `/admin/result/survey` 保留问卷管理入口；填写页仍是登录保护的 `/survey/fill/:surveyInstanceId`。
- `/admin/business-center/training/plan/:trainingPlanId/ended-result` 保持计划上下文，不添加无参数“培训成果”叶子页。
- `/admin/result/archive` 作为档案业务选择入口；计划上下文路由继续可直达一期档案。
- `/admin/result/certificate` 下保留模板、签发规则、已签发证书三个叶子页；附件给出的组件路径对照实际文件和动态解析器逐条验证。
- 权限按钮仅映射当前 Controller `@SaCheckPermission`；菜单可见性不能替代后端授权。

## 验证与关闭口径

- Red → Green：先用定向测试重现各 Task 缺口，再最小修复。前端至少运行受影响 Node 测试、typecheck 与目标 lint；后端只运行受影响模块测试。菜单 migration、档案文件及租户隔离需 MySQL/Testcontainers IT。
- 用非生产环境完成管理员、专家、填写人、学员的真实登录浏览器路径，验证请求载荷、权限、重复提交、文件 MIME/内容以及失败态。环境不可用则记录“待真实验收”，Task 不勾完成。
- 每 Task 完成后更新 `tasks.md`、QA、必要的 OpenAPI/进度表并做规格符合性和代码质量双轴 Review。阶段最终关闭需所有 Task 和真实端到端证据齐全。
