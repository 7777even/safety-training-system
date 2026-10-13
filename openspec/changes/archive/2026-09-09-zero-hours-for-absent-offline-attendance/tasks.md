# Tasks：缺勤确认后不计线下获得学时

> 状态：已完成。Owner：lims。仅后端；前端以 OpenAPI 为准另开。

## Task 1：完成缺勤请假确认后的学时与完成态派生

- [x] 1.1 完成缺勤请假确认后的学时与完成态派生

- **状态**：已完成
- **Owner**：lims
- **输入**：design D1～D3；既有 `TrainingResultSyncServiceImpl` 线下投影。
- **输出**：投影按出勤状态计获得学时；ABSENT/LEAVE 为 0 且 FAILED；OpenAPI 标明口径。
- **前后端影响**：仅后端；前端以 OpenAPI 为准另开。
- **API 依赖**：无新路径，仅改 description。
- **数据影响**：无 DDL。
- **验收标准**：
  1. 确认 ABSENT 后该项秒数为 0、`offlineHours` 不含该场、`resultStatus=FAILED`。
  2. 确认 LEAVE 后同样 0 学时且不得 COMPLETED。
  3. 确认 PRESENT 且仅签到规则时仍计场次时长。
  4. 要求签退却未签退仍为 0。
- **测试方式**：`TrainingResultSyncServiceImplTest` 新增缺勤/请假/正常出勤用例并回归签退用例。

## 完成门槛

1. 规格符合性：学时由后端从出勤状态派生，不改确认入参。
2. 代码质量：无前端改动，无 DDL。
