# Tasks：按中山 50 重整 M5 最小闭环

> 状态：已归档（2026-09-20 lims 确认「后端可交互即可」）。前端以 OpenAPI 为准另开。功能项 4「授权」未确认，不新增授权模型。

## Task 1：建立 M5 范围基线并收口超范围入口

- 状态：后端完成
- Owner：lims（仅后端）
- 输入：功能项 1、2、3、4、7、8；本 Change Design 第 1～3、10～13 节。
- 输出：能力/API/代码/SQL/调用方盘点；M5 状态纠正；删除清单和 COMMENT 清单。
- 前后端影响：仅后端；前端以 OpenAPI 为准另开。
- API 依赖：核对 `ops.openapi.json`、`regulation.openapi.json`、监管聚合 API 和 Controller。
- 数据影响：不删数据；形成 init SQL 与 COMMENT migration 的逐对象方案。
- 验收标准：每个保留入口可追溯到功能项；每个删除入口有调用方结论；重复监管 API 有明确合并方案。
- 测试方式：Controller/OpenAPI 对比、菜单权限扫描、静态调用检索、`git diff --check`。
- 证据：`inventory.md`；公开入口已按盘点删除或合并。

## Task 2：补齐机构分类与受监管机构入口

- 状态：后端完成（授权未确认，不声称覆盖功能项 4 全部语义）
- Owner：lims（仅后端）
- 输入：功能项 4；Design 第 4.4、7 节。
- 输出：企业/培训机构分类创建、查看、编辑、启停；监管租户可维护受监管机构；授权另开。
- 前后端影响：仅后端；前端以 OpenAPI 为准另开。
- API 依赖：已同步 `org.openapi.json` 与 `orgType` 调用方。
- 数据影响：复用 `org_type`，增加 `TRAINING_INSTITUTION`；既有数据不自动推断。
- 验收标准：企业与培训机构可明确分类；两类均可被监管汇总和只读进入；非监管用户不能切换目标租户。
- 测试方式：`OrganizationServiceImplTest`、`RegulatorViewResolverTest`、`OrganizationMapperIT`。

## Task 3：完成园区监管工作台与数据中心

- 状态：后端完成
- Owner：lims（仅后端）
- 输入：功能项 1、3、7；Task 2 机构分类；Design 第 4、5 节。
- 输出：园区概览、培训类型与特种作业统计、机构/人员统计、快捷入口、二级学习统计与下钻、机构/学习 CSV。
- 前后端影响：仅后端；前端以 OpenAPI 为准另开。
- API 依赖：已合并两套监管汇总接口，重写 `regulation.openapi.json`。
- 数据影响：不新增统计事实表。
- 验收标准：Design 4.5、5.4；园区、机构、明细和导出同一口径。
- 测试方式：`RegulatorAggregateMapperIT`、`RegulationWorkbenchServiceTest`、`RegulationLearningStatsServiceTest`、
  `RegulationControllerTest`。

## Task 4：完成培训计划学时预警与真实导出

- 状态：后端完成
- Owner：lims（仅后端）
- 输入：功能项 2；培训类型初培/复审学时；培训计划安排学时；Design 第 6、9 节。
- 输出：计划保存自动刷新、待查看/已查看列表、详情、计划下钻、自动结束和真实 CSV 导出。
- 前后端影响：仅后端；前端以 OpenAPI 为准另开。
- API 依赖：已重写 `regulation.openapi.json`，删除 warning-rules、detect、assign/comment/close/ignore/reopen。
- 数据影响：复用 warning/evidence；内部固定规则 `PLAN_HOURS_INSUFFICIENT`。
- 验收标准：Design 6.8；重复保存幂等；修正计划自动退出默认列表；导出真实可打开。
- 测试方式：`PlanHoursWarningRefreshServiceIT`、`WarningServiceImplTest`、`Utf8CsvExporterTest`。

## Task 5：完成企业管理员工作台

- 状态：后端完成
- Owner：lims（仅后端）
- 输入：功能项 8；Design 第 8 节。
- 输出：权限化功能入口、真实概览和分类待办。
- 前后端影响：仅后端；前端以 OpenAPI 为准另开。
- API 依赖：已重写 `ops.openapi.json`，仅保留企业工作台。
- 数据影响：只读现有事实。
- 验收标准：Design 8.4；无权限入口不可见；待办总数与分类之和一致。
- 测试方式：`OpsWorkbenchServiceImplTest`、`OpsControllerWebSliceTest`。

## Task 6：完成代码删除、SQL 注释和契约清理

- 状态：后端完成
- Owner：lims（仅后端）
- 输入：Task 1 删除清单；Design 第 10～13 节。
- 输出：删除通用 OPS、教师/学员工作台、规则引擎和处置工单公开代码；COMMENT migration；菜单种子收口。
- 前后端影响：仅后端；前端以 OpenAPI 为准另开。
- API 依赖：Controller 与 OpenAPI 对应；旧路径无后端调用方。
- 数据影响：不删表列和历史数据；init SQL、migration、schema-version `20260920190000` 与 Entity 说明一致。
- 验收标准：无占位导出、无手工检测、无规则配置、无人工处置。
- 测试方式：受影响零 DB + MySQL/Flyway IT、静态调用检索、`git diff --check`。

## Task 7：M5 联调、Review、QA 与状态关闭

- 状态：后端口径已归档
- Owner：lims（仅后端）
- 输入：Task 1～6 后端证据；功能项 4「授权」未选择。
- 输出：OpenAPI、QA、进度表。前端真实登录链路他方负责。
- 前后端影响：仅后端；前端以 OpenAPI 为准另开。
- API 依赖：监管与企业工作台契约已交付。
- 数据影响：数据库注释和历史兼容已由 Flyway/init IT 验证。
- 验收标准：后端五条能力可交互；不把 Mock、占位文件或纯后端当作 M5 关闭。
- 测试方式：见 QA `engineering/qa/2026-09-20-realign-m5-minimum-closure.md`。
