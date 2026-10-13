# Design：人员门禁按人工批阅需求分支

## 规则

| 考试类型 | 批阅人 | 交卷结果 |
| --- | --- | --- |
| `BANK_PRACTICE` | 不配置、不校验 | 简答 `NOT_EVALUATED`，其他题自动反馈，结果直接生效且无正式分数 |
| `OFFLINE` | 不配置、不校验 | 管理员录分即发布，权限为 `exam:score:publish` |
| `ONLINE` / `SPECIAL_PRACTICE` | 保留 | 仅其主观题人工批阅链路维持现状 |

独立题库练习以 `assessmentKind=BANK_PRACTICE` 为稳定识别条件，避免把 `STANDALONE_PRACTICE` 中的专项练习误改为题库练习。执行不设置及格线；客观题冻结可核对分值（历史规则未提供分值时默认每题 1 分），简答题分值为空并标记为 `RECORD_ONLY`。

前端仅在 `ONLINE`、`SPECIAL_PRACTICE` 展示批阅人区域、批阅人核查和发布门禁。题库练习的发布核查明确提示“自动反馈、不生成成绩”；线下考试提示“发布后由有录分权限人员录入成绩”。
