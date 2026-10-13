# Design：运营工作台 / 监管预警后端（M5）

## 包与扫描

| 包                    | 职责                         |
|----------------------|----------------------------|
| `com.sts.ops`        | 工作台待办、统计概览、下钻列表、导出任务       |
| `com.sts.regulation` | 监管汇总、预警规则/条件、检测、预警、证据、操作流水 |

## 数据模型

- 预警：rule / condition / warning / evidence / action（DDL 原文）。
- OPS 导出：可选轻量表 `sts_ops_export_job`（job_status/file_id/request_json），或复用内存同步返回；**采用**
  `sts_ops_export_job` 最小表写入 migration，避免无落库任务。
- 检测幂等：`dedup_key = hash(ruleId|regulatedOrgId|planId|detectionCycle)`；evidence `evidence_key` 唯一。

## 指标最小集（暂定）

| metric_code       | 含义                              | 比较    |
|-------------------|---------------------------------|-------|
| PLAN_TOTAL_HOURS  | 计划要求学时合计 vs 标准 TOTAL_HOURS      | LT 触发 |
| PLAN_ONLINE_RATIO | 计划线上学时占比 vs 类型 online_max_ratio | GT 触发 |

检测入口：`POST /regulation/warnings/detect`（orgId + optional planId + detectionCycle）。

## API 摘要

- `/ops/workbench/enterprise|learner|teacher` 摘要
- `/ops/stats/courses|exams|trainings|results` 计数
- `/ops/stats/drilldown` 按维度返回业务 ID 列表
- `/ops/exports` create/list/get
- `/regulation/overview` 监管汇总
- `/regulation/warning-rules` CRUD + conditions replace
- `/regulation/warnings` list/get；detect；assign/view/comment/close/ignore/reopen

## 技术取舍

- 统计全部 SQL/Mapper 聚合现有表，不写汇总事实表。
- 检测同步执行；大批量后续可异步（本期不强制）。
- warning_level 暂定 INFO/WARN/CRITICAL。

## 风险

指标口径简化；与产品最终字典可能偏差，以 OpenAPI 字段说明为准。
