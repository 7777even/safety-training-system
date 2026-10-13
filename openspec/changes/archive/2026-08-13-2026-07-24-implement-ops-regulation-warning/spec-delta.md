# Spec Delta：运营工作台 / 监管预警（M5）

## ADDED Requirements

### Requirement: 工作台待办与统计投影

系统 MUST 提供企业/学员/教师工作台摘要与课程/考试/培训/结果统计，数据 MUST 来自已有业务事实而非独立统计表。

### Requirement: 统计下钻与导出

系统 MUST 支持按维度下钻到业务 ID 列表，并支持创建导出任务记录。

### Requirement: 监管汇总视图

系统 MUST 按监管关系聚合被监管机构的计划与预警数量。

### Requirement: 预警规则与幂等检测

系统 MUST 支持规则与条件维护；检测 MUST 按 dedup_key 幂等生成预警与证据。

### Requirement: 预警处置流水

系统 MUST 支持查看、分派、评论、关闭、忽略、重开，并追加 action 流水。

## REMOVED Requirements

无。

## MODIFIED Requirements

无。
