# Proposal：监管个人培训记录下钻

监管端只有企业汇总。切换 `X-Target-Tenant-Id` 后没有「计划 → 参训人 → 内容结果」的只读入口，监管账号也没有培训列表权限。

## Why

P1 要求监管能从企业汇总下钻到单个学员的可追溯培训记录。现有结果投影已有学时、成绩和来源标识，缺的是监管可调用的只读入口。

## What Changes

- 监管只读：计划分页/详情、参训人列表、参训人内容结果。
- 必须 REGULATOR，且已用 `X-Target-Tenant-Id` 切到目标企业。
- 复用现有计划/结果查询，不新建投影表。
- 线下签到来源不作为本期字段（最小子集已去掉签到 HTTP）。

## Capabilities

### New Capabilities

- `regulation-personal-training-records`：监管只读下钻个人培训记录。

### Modified Capabilities

无。

## Impact

- 后端：RegulationController 新 GET；OpenAPI `regulation.openapi.json`。
- 无 DDL、无新权限码（`regulation:overview:view`）。
- 仅后端；前端以 OpenAPI 为准另开。

## 非目标

预警新指标、统计图表、签到明细、前端页面、场次状态机、交卷空白答案。
