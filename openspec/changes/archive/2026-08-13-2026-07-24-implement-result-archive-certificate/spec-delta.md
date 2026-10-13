# Spec Delta：综合结果 / 档案 / 证书（M4）

## ADDED Requirements

### Requirement: 问卷不可变版本与投放作答

系统 MUST 支持问卷定义与题目编排；发布 MUST 写入不可变版本快照；投放实例 MUST 支持参与人绑定与一次有效提交；MUST 提供基础统计。

### Requirement: 评价记录提交

系统 MUST 支持基于已发布评价表单版本创建并提交评价记录及明细；已提交记录 MUST 不可覆盖。

### Requirement: 综合成绩不可变批次

系统 MUST 按参训人生成计算批次与来源明细；确认正式结果 MUST 唯一；重算 MUST 新批次并替代旧正式批次。

### Requirement: 培训档案归档版本

系统 MUST 从培训计划生成档案及不可变归档版本；更新 MUST 仅新增版本。

### Requirement: 证书模板与幂等签发

系统 MUST 支持证书模板版本与签发规则；签发 MUST 幂等；撤销 MUST 留痕；重发 MUST 产生新证书号。

## REMOVED Requirements

无。

## MODIFIED Requirements

无。
