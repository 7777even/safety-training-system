# M5 范围基线盘点（Task 1）

日期：2026-09-20  
协作方：lims（仅后端；前端以 OpenAPI 为准另开）  
“授权”语义未人工选择，不新增授权模型。

## 保留（可追溯功能项）

| 入口                                | 功能项 | 处置                                                          |
|-----------------------------------|-----|-------------------------------------------------------------|
| 机构 CRUD `/org/organizations`      | 4   | 增加 `TRAINING_INSTITUTION`；监管租户可管理企业/培训机构；授权另开               |
| 监管工作台                             | 1、3 | 合并 `/regulation/overview` 与 `/regulator/aggregates/tenants` |
| 学习统计与下钻、CSV                       | 3、7 | 新建监管 API                                                    |
| 学时预警列表/详情/已查看/CSV                 | 2   | 自动刷新；删除规则 CRUD、detect、人工处置                                  |
| 企业工作台 `/ops/workbench/enterprise` | 8   | 权限化入口 + 可解释概览/分类待办                                          |
| 监管培训计划只读下钻                        | 1   | 保留                                                          |

## 删除公开入口

| 入口                                                | 调用方                                  | 结论       |
|---------------------------------------------------|--------------------------------------|----------|
| `/ops/workbench/teacher`、`/ops/workbench/learner` | 仅 OPS Controller / OpenAPI / 零 DB 测试 | 删除       |
| `/ops/stats/*`、`/ops/stats/drilldown`             | 同上；监管角色曾授 `ops:stats:*`              | 删除       |
| `/ops/exports*`                                   | 占位任务，无真实文件                           | 删除       |
| `/regulation/warning-rules*`、`/conditions`        | 规则引擎产品入口                             | 删除       |
| `POST /regulation/warnings/detect`                | 手工检测                                 | 删除       |
| assign/comment/close/ignore/reopen                | 人工处置工单                               | 删除       |
| `GET /regulator/aggregates/tenants`               | 与 overview 口径重复                      | 删除，并入工作台 |
| `GET /regulation/overview`                        | 旧按机构行计数                              | 删除，并入工作台 |

## SQL COMMENT

见 Design 第 10.3 节：warning 表保留；evidence 内部保留；rule 内部固定规则；condition/action/export_job/assignee 暂时隐藏。

## 重复监管 API 合并方案

统一为 `RegulationController` 监管工作台/学习统计/预警。`RegulatorAggregateMapper` 仅作跨租户只读 SQL，不再对外暴露第二套
URL。

## 菜单种子

`menus-back.json` 与 Flyway `V20260920190000`：删除统计/导出/学员教师工作台/预警规则/手工检测；新增数据中心与导出按钮；监管概览更名为监管工作台。
