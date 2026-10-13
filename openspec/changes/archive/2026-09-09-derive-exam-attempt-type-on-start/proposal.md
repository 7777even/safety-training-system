# Proposal：开考类型由服务端派生

学员进入考试或培训题库时，请求常带 `attemptType=FIRST` 且每次新令牌。后端只对 `RETAKE`
校验补考，课程测验以外没有「已有终态则复用」护栏，可反复创建首次答卷，扰乱成绩和培训投影。

## Why

能否再开一卷、这卷是首考还是补考，属于业务状态，必须由考生已有答卷派生。前端只提供令牌和设备信息；补考是单独明确动作。不能把请求里的
`FIRST`/`RETAKE` 当成创建授权。

## What Changes

- 通用开考、培训 enter、课程测验/题库开考忽略请求中的 `attemptType`（兼容旧客户端仍可传）。
- 无作答则创建首考（可重复练习类创建 `PRACTICE`）；有进行中则恢复；正式考试/培训考试/培训题库/课程测验已有非作废终态则返回原卷。
- 仅 `POST /exam/candidates/{id}/retake` 可创建 `RETAKE`，并继续走既有策略护栏。
- 课程章节题库、独立练习在无进行中时可再开 `PRACTICE`，进行中仍恢复。
- 无新路径、无新权限、无 DDL。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `exam-retake`：通用开考不得凭请求创建补考。
- `training-execution-loop`：培训进入不得凭 `attemptType` 新建首次答卷。

## Impact

- 后端：`ExamAttemptServiceImpl.start`、培训 Adapter/enter、课程考核开考；对应零 DB。
- 契约：`docs/api/exam.openapi.json`、`docs/api/training.openapi.json` 说明 `attemptType` 由服务端派生。
- 仅后端；前端以 OpenAPI 为准另开。

## 功能项追溯

- 功能项 16、24：考试作答次数与正式结果。
- 功能项 34、48、56：培训考试/题库形成可执行结果，学员任务反映真实作答。

## 目标

1. 交卷后再进入，返回已有答卷，不插入第二份 `FIRST`。
2. 补考只能走补考接口。
3. 旧客户端继续传 `FIRST` 不导致错误，也不再生效。

## 非目标范围

- 不改 `frontend/`。
- 不改补考次数、窗口、范围算法。
- 不改学时口径、投影刷新、练习重置作废已提交卷。
- 不提前写参训人最终 PASSED/FAILED。

## 前后端影响

仅后端；前端以 OpenAPI 为准另开。
