# Exam Attempt Query

## ADDED Requirements

### Requirement: 考试查询必须关联真实机构表

系统 SHALL 在教师批阅任务和考试作答记录查询中，通过 `sts_org_organization` 获取考生机构名称。

#### Scenario: 查询批阅任务

- **WHEN** 教师查询本人批阅任务
- **THEN** 查询可在真实 MySQL schema 上执行
- **AND** 返回考生所属机构名称

#### Scenario: 查询考试作答记录

- **WHEN** 管理员按数据范围查询考试作答记录
- **THEN** 查询可在真实 MySQL schema 上执行
- **AND** 既有筛选和排序语义保持不变
