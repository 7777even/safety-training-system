# regulation-personal-training-records Specification

## Purpose

监管平台从企业汇总下钻到单个参训人的培训内容结果。

## Requirements

### Requirement: 监管可只读下钻个人培训记录

系统 MUST 为 REGULATOR 提供只读查询：目标企业培训计划分页与详情、计划参训人、参训人逐内容结果（含获得学时、成绩、完成时间、
`sourceRecordType`/`sourceRecordId`/`finalAttemptId`）。权限 MUST 为 `regulation:overview:view`。调用前 MUST 已通过
`X-Target-Tenant-Id` 切换到目标企业租户；未切换 MUST 返回 400 / `9860`。非 REGULATOR MUST 返回 403 / `4065`。系统 MUST NOT
提供监管写培训数据的入口。

#### Scenario: 切换企业后读取参训结果

- **GIVEN** 监管账号已登录且请求头指向某企业租户
- **WHEN** 查询该企业某计划某参训人结果
- **THEN** 返回与培训结果 GET 相同的投影字段

#### Scenario: 未切换视角

- **GIVEN** 监管账号未携带 `X-Target-Tenant-Id`
- **WHEN** 查询监管培训计划
- **THEN** 返回 400 / `9860` 且不返回监管租户自身数据冒充企业记录
