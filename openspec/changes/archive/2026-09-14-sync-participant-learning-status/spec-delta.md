# Spec Delta：参训人学习中状态

## MODIFIED Requirements

### Requirement: 来源事实写入后自动刷新对应逐内容投影

系统 MUST 在未冻结参训人的内容投影首次进入非 `PENDING` 状态后，将参训人的 `participantStatus` 从 `PENDING` 更新为 `LEARNING`。该更新 MUST NOT 写入最终 `PASSED` / `FAILED` 结论。

#### Scenario: 内容开始后参训人进入学习中

- **GIVEN** 运行中计划的参训人状态为 `PENDING`
- **WHEN** 任一内容投影同步为 `IN_PROGRESS`、待阅卷、待发布、完成、未通过或过期
- **THEN** 参训人状态为 `LEARNING`
- **AND** 计划结束前不得仅因该内容同步写入参训人的最终结论
