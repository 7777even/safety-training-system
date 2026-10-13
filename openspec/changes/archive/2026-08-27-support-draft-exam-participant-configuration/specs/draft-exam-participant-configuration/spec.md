# draft-exam-participant-configuration Specification Delta

## ADDED Requirements

### Requirement: 独立考试必须支持发布前配置具体考生和阅卷人

系统 MUST 允许管理员在独立考试处于 `DRAFT` 时配置具体考生和阅卷人。Candidate 与 Reviewer MUST 从首次保存起归属于该考试唯一的
`AssessmentExecution`，MUST NOT 恢复按 `examId` 保存人员的第二套执行归属。

#### Scenario: 首次绑定草稿考试考生

- **GIVEN** 一个存在且处于 `DRAFT` 的独立考试尚无执行对象
- **AND** 当前考试资源与规则足以形成完整执行预备投影
- **WHEN** 管理员提交可解析为非空用户集合的考生选择
- **THEN** 系统幂等创建该考试唯一的执行对象
- **AND** 将具体 Candidate 保存到该执行对象

#### Scenario: 首次保存草稿考试阅卷人

- **GIVEN** 一个存在且处于 `DRAFT` 的独立考试尚无执行对象
- **AND** 当前考试资源与规则足以形成完整执行预备投影
- **WHEN** 管理员提交非空阅卷人集合
- **THEN** 系统幂等创建该考试唯一的执行对象
- **AND** 在该执行对象上整体保存 Reviewer

#### Scenario: 首次提交空人员配置

- **GIVEN** 一个 `DRAFT` 独立考试尚无执行对象
- **WHEN** 管理员提交解析结果为空的考生选择或空阅卷人集合
- **THEN** 系统成功返回空结果
- **AND** 不创建执行对象或人员关系

#### Scenario: 并发首次配置人员

- **GIVEN** 一个 `DRAFT` 独立考试尚无执行对象
- **WHEN** 两个请求并发首次保存有效人员配置
- **THEN** 系统最终只有一个活动执行对象
- **AND** 人员关系按既有唯一边界幂等保存

### Requirement: 草稿批阅人读取必须无副作用地返回空集合

读取独立考试阅卷人时，系统 MUST 先验证考试存在。存在的 `DRAFT` 考试尚无执行对象时 MUST 返回成功空数组且不得创建数据；非草稿考试缺失执行对象时
MUST 继续返回 `7062`，暴露数据一致性异常。

#### Scenario: 首次进入草稿人员配置

- **GIVEN** 一个存在且处于 `DRAFT` 的独立考试尚无执行对象
- **WHEN** 管理员读取该考试阅卷人
- **THEN** 系统返回 HTTP 200 和空数组
- **AND** 不写入执行对象或人员关系

#### Scenario: 读取不存在考试的阅卷人

- **GIVEN** 请求中的考试不存在
- **WHEN** 管理员读取阅卷人
- **THEN** 系统返回既有考试不存在错误
- **AND** 不以空数组掩盖错误

#### Scenario: 已发布考试异常缺失执行对象

- **GIVEN** 一个非 `DRAFT` 独立考试异常缺失执行对象
- **WHEN** 管理员读取或保存人员配置
- **THEN** 系统返回 `7062`
- **AND** 不在读取或人员写入路径静默修复发布数据

### Requirement: 独立考试发布必须在原执行对象上冻结最终快照

草稿期提前物化的执行对象只承担人员归属。系统 MUST 在发布事务中以最终考试、资源和策略数据覆盖执行快照并重算稳定摘要，MUST
保留原
`assessmentExecutionId`、Candidate 和 Reviewer。执行对象存在本身 MUST NOT 使草稿考试可见、可开考或可作答。

#### Scenario: 配置人员后修改草稿并发布

- **GIVEN** 草稿考试已经配置考生和阅卷人并存在执行对象
- **AND** 管理员随后更换试卷或修改考试规则与策略
- **WHEN** 管理员发布考试
- **THEN** 系统在原执行对象上保存最终资源、规则和策略快照并重算摘要
- **AND** 原 Candidate 与 Reviewer 关系保持不变

#### Scenario: 发布冻结失败

- **GIVEN** 草稿考试已经配置人员
- **WHEN** 发布过程中的资源校验、快照写入或考试状态更新失败
- **THEN** 整个发布事务回滚
- **AND** 不产生已发布考试与旧快照并存或人员关系丢失的结果

#### Scenario: 草稿执行对象不得授权作答

- **GIVEN** 草稿考试因人员配置已存在执行对象和 Candidate
- **WHEN** 学员查询任务或尝试开始作答
- **THEN** 系统仍按考试发布状态拒绝该执行
- **AND** 不得只因执行对象存在而授予考试资格

### Requirement: 删除草稿考试必须清理未投入执行的人员归属

删除已经提前物化执行对象的 `DRAFT` 考试时，系统 MUST 在同一事务中清理其 Reviewer、Candidate 和执行对象，再按既有规则删除策略和考试。
若草稿执行对象异常存在 Attempt，系统 MUST 拒绝删除并保留数据供一致性处理。

#### Scenario: 删除已配置人员的草稿考试

- **GIVEN** 一个 `DRAFT` 考试已经配置 Candidate 和 Reviewer 但没有 Attempt
- **WHEN** 管理员删除考试
- **THEN** 系统清理该考试的活动人员关系和执行对象
- **AND** 不留下会阻塞后续创建或查询的孤立事实

#### Scenario: 草稿执行对象异常存在作答

- **GIVEN** 一个 `DRAFT` 考试的执行对象异常存在 Attempt
- **WHEN** 管理员删除考试
- **THEN** 系统拒绝删除
- **AND** 不级联破坏作答事实
