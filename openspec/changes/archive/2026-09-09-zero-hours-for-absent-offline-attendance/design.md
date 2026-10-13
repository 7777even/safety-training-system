# Design：缺勤确认后不计线下获得学时

## Context

`readDraft` 线下分支用 `confirmedBy != null` 当作有效出勤，`effectiveOfflineSeconds` 在非签退场次直接返回场次时长。
`attendanceStatus` 只写入 `sourceStatus`。教师确认 `ABSENT`/`LEAVE` 后仍计满学时，无及格线时还写成 `COMPLETED`
。确认接口已挂钩单行刷新，修投影即可。

## Goals / Non-Goals

**Goals:**

- 线下获得学时与完成判定读取已确认出勤状态。
- `ABSENT`/`LEAVE` 确认后 0 学时、`FAILED`。
- `PRESENT`/`LATE` 维持现有有效时长与签退规则。
- 契约写明该口径。

**Non-Goals:**

- 迟到折扣、请假部分学时、综合成绩缺勤否决。
- 改签到写入、确认入参或让前端传学时。
- 应到名单、场次状态机、监管下钻、DDL。

## Decisions

### D1：投影读取出勤状态，确认不传学时

在 `effectiveOfflineSeconds` 与完成判定读取 `attendanceStatus`：

- 未确认：0 秒，非终态。
- `PRESENT`/`LATE`：沿用现有 cap 与 `SIGN_IN_OUT` 进出时间。
- `ABSENT`/`LEAVE`：0 秒，`resultStatus=FAILED`，`sourceStatus` 仍为出勤状态。成绩不能把缺勤改成 `COMPLETED`。

不在确认接口增加学时字段。

### D2：缺勤/请假用 FAILED 终态

`FAILED` 已是结束门禁终态，计划可以结束；必修缺勤会让参训人结论为 `FAILED`。不另造 `SKIPPED`。请假与缺勤本期同一口径，不做比例折算。

### D3：迟到不计折扣

无产品规则时 `LATE` 与 `PRESENT` 同学时。未知或空状态按未出勤：0 秒、不得 `COMPLETED`；已确认则 `FAILED`。

## Risks / Trade-offs

- 存量已确认缺勤仍带着满学时 → 部署后对计划做一次 `results/synchronize` 补账。
- 必修缺勤后参训人 `FAILED` → 符合未出勤未完成；不是综合成绩缺勤否决公式。

## Migration Plan

无 DDL。部署后新确认立即按状态计；存量全量同步一次。回滚即去掉状态判断。

## Open Questions

无。
