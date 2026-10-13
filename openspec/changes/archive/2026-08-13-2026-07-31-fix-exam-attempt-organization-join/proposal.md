# Proposal：修复考试作答查询机构表关联

## 背景

`ExamAttemptQueryMapper.xml` 的教师批阅任务与考试作答记录查询关联了不存在的 `sts_organization`，而机构表真相源为
`sts_org_organization`，导致真实 MySQL 查询失败。

## 目标

- 修复两条考试作答聚合查询的机构名称关联。
- 恢复 `ExamAttemptQueryMapperIT` 真库验证。

## 范围

- 修改考试查询 Mapper XML 中两处机构表名。
- 运行考试查询定向零 DB/真库测试。

## 非目标

- 不修改 API Contract、权限、数据库结构或前端。
- 不调整考试、批阅或数据范围业务规则。
