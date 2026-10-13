# Spec Delta：缺勤确认后不计线下获得学时

## Modified Capabilities

### training-execution-loop

线下获得学时 MUST 读取已确认 `attendanceStatus`。仅 `PRESENT`/`LATE` 计入；已确认 `ABSENT`/`LEAVE` 的
`effectiveDurationSeconds` MUST 为 0，`resultStatus` MUST 为 `FAILED`，MUST NOT 为 `COMPLETED`。
