# Spec Delta：完善考试补考管理与成绩状态展示

## ADDED Requirements

### Requirement：考试可配置补考策略

系统 SHALL 在创建和编辑考试时，通过既有考试 `policy` 保存补考开关、补考范围、补考窗口、补考次数、成绩取值方式和补考记录可见性。

#### Scenario：配置不及格或缺考人员补考

- **WHEN** 管理人员启用补考并选择补考范围
- **THEN** 系统保存选择的既有策略值
- **AND** 不创建新的考试安排

### Requirement：管理员可为指定考生开补考

系统 SHALL 在成绩处置中，针对指定 `examCandidateId` 使用既有补考接口开一次新作答。

#### Scenario：开补考失败后重试

- **WHEN** 管理人员对同一开补考操作重试
- **THEN** 系统复用首次生成的 `clientAttemptToken`
- **AND** 不提交考试时间、时长、试卷、范围、次数或成绩字段

### Requirement：成绩相关枚举应使用中文展示

系统 SHALL 将用户可见的作答状态、成绩发布方式和成绩结果映射为中文业务文案。

#### Scenario：查看已发布的不及格成绩

- **WHEN** 发布流水返回 `PUBLISHED`、`SINGLE`、`FAILED`
- **THEN** 页面分别展示对应的中文文案
