## MODIFIED Requirements

### Requirement: 学员作答页以服务端截止时间计时

作答页 MUST 以服务端 `deadlineTime` 为唯一计时事实。离开页面不得暂停或延长截止时间。缺失或非法时间 MUST 显示异常而非自动交卷；已到期作答 MUST 按超时结果结束；终态 MUST 进入结果。

#### Scenario: 无效截止时间

- **GIVEN** 作答状态为 `ANSWERING` 但截止时间缺失或不可解析
- **WHEN** 客户端加载作答页
- **THEN** 显示作答数据异常并禁止编辑
- **AND** 不调用客户端超时交卷
