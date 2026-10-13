# Spec Delta：培训考试恢复与双端统一

## ADDED Requirements

### Requirement: 培训理论考试须知只读读取

系统 MUST 允许当前登录参训人在运行中培训计划读取本人 `EXAM` 内容的计划专属考试须知。读取 MUST NOT 创建或恢复 Candidate/Attempt，也 MUST NOT 修改开始时间、截止时间、次数或培训结果。

#### Scenario: 待开始理论考试先阅读须知

- **GIVEN** 当前用户是运行中培训计划的参训人，考试内容状态为 `PENDING` 或 `NOT_STARTED`
- **WHEN** 用户进入理论考试
- **THEN** 客户端读取并展示须知，且不调用统一进入接口
- **WHEN** 用户在须知页明确开始答题
- **THEN** 客户端调用统一进入接口创建或恢复作答

## MODIFIED Requirements

### Requirement: 培训考试进入与截止时间

培训内容 `EXAM` 的 `IN_PROGRESS` 作答 MUST 直接使用统一进入接口恢复；`QUESTION_BANK` MUST 直接使用统一进入接口而不读取考试须知。作答页 MUST 以服务端 `deadlineTime` 为唯一计时事实。离开页面 MUST NOT 暂停或延长截止时间；缺失或无效截止时间 MUST NOT 触发客户端自动交卷；终态作答 MUST 进入结果回看。

#### Scenario: 进行中考试恢复

- **GIVEN** 当前用户有状态为 `IN_PROGRESS` 的培训理论考试
- **WHEN** 用户从培训详情再次进入该内容
- **THEN** 客户端直接调用统一进入接口并恢复原作答
- **AND** 不展示考试须知

#### Scenario: 退出后按绝对截止时间恢复

- **GIVEN** 作答为 `ANSWERING` 且服务端截止时间还有十分钟
- **WHEN** 学员退出并在五分钟后重进
- **THEN** 页面显示约五分钟剩余时间
- **AND** 截止时间不暂停、不延长
