# M5 完成度核验（2026-10-08）

> **结论：按「符合功能项 + 最小化实现」标准，M5 已达成，可登记完成。**  
> 功能项 1/2/3/4/7/8 的代码、契约、数据与真实登录态验收均已闭环；下述遗留项全部属  
> **证据强度**（本机环境限制）或**超出最小化范围的体验优化**，不构成功能缺口。

## 一、本轮核验方法与实测结果

| 项               | 命令 / 方式                                                                                                                                                                                                      | 结果                                                                                          |
| --------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------- |
| OpenSpec（M5 规格） | `openspec validate m5-minimum-closure --type spec --strict`                                                                                                                                                  | **✓ valid**                                                                                 |
| 归档完整性           | `git show --stat c982d2e5`                                                                                                                                                                                   | Change 5 个文档已移入 `archive/`；delta **+61/−4** 已并入 `openspec/specs/m5-minimum-closure/spec.md` |
| 规格基线            | `grep '^### Requirement:'`                                                                                                                                                                                   | **10 条 Requirement**、166 行（归档前 8 条）                                                         |
| PC typecheck    | `vue-tsc --noEmit`                                                                                                                                                                                           | `EXIT=0`（无输出）                                                                               |
| PC 定向 lint      | `eslint src/views/admin/regulation src/views/admin/ops src/api/regulation.ts src/api/ops-workbench.ts --max-warnings=0`                                                                                      | `EXIT=0`                                                                                    |
| PC 契约测试         | `tests/m5-regulation-contract / m5-ops-contract / organization-management`                                                                                                                                   | **5/5 通过**（3+1+1）                                                                           |
| 后端定向单测          | `SysMenuServiceImplTest / RegulationWorkbenchServiceTest / OpsWorkbenchServiceImplTest / OrganizationServiceImplTest / PlanHoursWarningRefreshServiceTest / SysDeptServiceImplTest / SysRoleServiceImplTest` | **64/64，BUILD SUCCESS**                                                                     |
| migration 链一致性  | `sts.init.schema-version` × `FlywayMigrationIT.INIT_SCHEMA_VERSION` × 最新文件名                                                                                                                                  | 三处均为 **`20260930180000`**                                                                   |
| 菜单数据            | 远程库只读查询                                                                                                                                                                                                      | 9752 幽灵树 `deleted=menu_id`（9 行全软删）、顶级目录 `path` **无重复**                                      |
| 权限数据            | 远程库只读查询                                                                                                                                                                                                      | 非监管角色 **已无** 9532/1381/1382/1383 绑定；监管 role5 绑定 **46 条**                                    |
| 工作树             | `git status --short`                                                                                                                                                                                         | 9 改动 + 1 新增（登录入口增强，**未提交**，见 §四）                                                            |

**方法学提醒**：三个 PC 契约测试是 `readFileSync` + 正则的**源码文本断言**，不是运行时行为测试；  
本轮**未重跑端到端登录态**（后端 20080 未在运行，前端 5173 在线），监管/企业真实登录态证据沿用  
`2026-09-30-m5-pc-regulation-acceptance.md` §10.6 / §11 / §12 的实测记录。

## 二、功能项覆盖对照

| 功能项 | 语义                              | 实现落点                                                                     | 验收状态                                   |
| --: | ------------------------------- | ------------------------------------------------------------------------ | -------------------------------------- |
|   1 | 监管工作台：园区/企业统计、培训类型、特种作业、机构与人员统计 | `views/admin/regulation/workbench`、`GET /api/regulation/workbench`       | 真实登录态已验收（监管会话 `200/code=0`）            |
|   2 | 学时不足预警、待查看列表、真实导出               | `views/admin/regulation/warnings`、`PlanHoursWarningRefreshService`       | 幂等生成 / 首看留时 / 修正后 `ENDED` / 再不足重开 均已验收 |
|   3 | 证书、课程、数据中心快捷入口；园区与机构二级页面        | `shortcuts=["CERTIFICATE","DATA_CENTER"]`、`regulation/{plans,learning}`  | 收敛后两条快捷入口已验证                           |
|   4 | 机构分类创建/编辑/授权/停用，进入只读管理中心        | `views/admin/system/org/organization`（菜单 9814）、`OrganizationServiceImpl` | 开户、分类、只读进入均已验收                         |
|   7 | 员工账号数、上课次数/人数、参与课程数、人均次数、参与率    | `views/admin/regulation/learning`、`GET /api/regulation/learning-stats`   | 六项口径与 CSV 一致性已验收                       |
|   8 | 企业管理员工作台：功能入口、概览、分类待办           | `views/admin/ops/workbench`、`OpsWorkbenchServiceImpl`                    | 权限化入口 + 待办分类求和已验收                      |

**范围说明**：功能项 5（培训类型）、6（培训计划）虽同列"政府/园区监管端"，但语义属培训主数据构建，  
已由 M3 侧 Change（`2026-09-01-add-training-plan-nature` 等）覆盖，**不在 M5 最小闭环范围**，本 Change 的非目标已  
明确"不重新建设 M3/M4 业务事实"。

## 三、剩余问题清单

### A. 证据强度类（不阻断完成，受本机环境限制）

| #  | 问题                       | 现状                                                                                                                                                              |
| -- | ------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| A1 | **容器内 Flyway 自动执行链路未验证** | 本机无 Docker，`-P integration-test` 跑不起来。`V20260930110000/150000/160000/170000/180000` 的 5 个 IT 用例只做到 `test-compile` + `javap` 字节码核对，SQL 本身已在共享库**手工执行并逐条复核**（幂等）。 |
| A2 | **页面渲染截图未取得**            | 本机无可用无头 Chrome。前端渠道收敛结论由 **Vite `ssrLoadModule` 真实执行 + 真实菜单树**支撑，非截图。                                                                                           |
| A3 | PC 契约测试为**源码字符串断言**      | 不能替代运行时行为测试；与 A2 共同构成前端侧的"最后一公里"证据缺口。                                                                                                                           |

### B. 待人工决策类（超出"最小化实现"，属体验完善）

| #  | 问题                       | 说明                                                                                                                                                                                                                                                            |
| -- | ------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| B1 | 「已签发证书」页**人员姓名静默降级为 ID** | 监管租户缺 `common:person:list`。**两条常规修法都不通**：① 补授权会把刚在 `V20260930160000` 解绑的 `9531 课程中心`/`300 课程管理` 祖先链带回侧栏；② `PersonPickerServiceImpl` 按会话租户强过滤，跨租户给了权限也查不到企业持证人。正解是**后端 easytrans 展示名翻译**（`@TranslateField(source="holderUserId", type="USER_SERVICE")`），属独立改动。 |
| B2 | 机构管理页**两个监管下钻按钮**是否恢复    | 当初删除理由（`orgType` 不存在、守卫恒 false）已不成立，属产品交互决策，本轮未动。                                                                                                                                                                                                             |
| B3 | 监管侧栏"菜单管理"**只读入口**的可见副作用 | 已确认接受；新增/编辑/删除按钮因 `canOperate(...)=false` 不渲染。                                                                                                                                                                                                                |

### C. 范围外 / 存量问题（非 M5 引入）

| #  | 问题                                                                         | 说明                                                                                                                                                                                                                                                                                                |
| -- | -------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| C1 | **裕龙 27 项无任何 Change 覆盖**                                                   | 不属 M5（M5 为中山 1~50 的监管端 + 企业工作台）。"不在本期范围"还是"漏开 Change"需产品与开发负责人确认口径。                                                                                                                                                                                                                               |
| C2 | `OrganizationServiceImpl` 的 `notIn(moduleId, 950L, 9810L)` **NULL 三值逻辑隐患** | 会连带排掉 `module_id IS NULL` 的菜单。既有问题，本轮未修改。                                                                                                                                                                                                                                                         |
| C3 | **11 个存量 spec 的 strict 校验失败**                                              | `openspec validate --all --strict` 报 11 项（`learner-course-reading`、`tenant-auth`、`training-api-contract`、`menu-permission-consistency`、`menu-tree-alignment`、`regulation-personal-training-records`、`training-material` 等），原因均为 `## Purpose` 仍是占位符。**与 M5 无关**，`m5-minimum-closure` 自身 strict 通过。 |
| C4 | 工作树 9 改 + 1 新增**未提交**                                                      | 内容为"机构专属登录链接 + 退出回带 `orgCode` + 侧栏显示所属机构名"，属**新增强**（`LoginPage.vue`/`PortalLayout.vue`/`routes.ts`/`AdminLayout.vue`/`organization/index.vue`）。触及 M5 页面但与 M5 功能项无冲突，需按独立功能项另行提交。                                                                                                                  |

## 四、需人工决策

1. **B1 证书页人员姓名降级**：是否排期后端 easytrans 翻译修复。
2. **B2 两个监管下钻按钮**：产品决定保留或恢复。
3. **C1 裕龙 27 项**：确认"不在本期范围"或补开 Change。
4. **C4 工作树未提交改动**：确认归属（独立功能项）与提交时机。
5. （环境）如需补齐 A1/A2 证据，须提供 **Docker 环境**与**可用无头浏览器**。

## 五、结论

M5 的功能范围（中山监管端闭环 + 企业管理员工作台）已按最小化实现交付，代码、契约、权限数据与  
真实登录态均已闭环；遗留问题不改变"完成"判定。**M5 可登记完成，转入 M6。**
