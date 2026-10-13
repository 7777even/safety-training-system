# Spec Delta：开考类型由服务端派生

## Modified Capabilities

### exam-retake

通用开考 MUST NOT 根据请求 `attemptType` 创建 `RETAKE`。补考 MUST 仅由
`POST /api/exam/candidates/{examCandidateId}/retake` 创建。

### training-execution-loop

培训学员 enter / 兼容开考 MUST 只接受令牌与设备信息。作答类型 MUST 由服务端按已有答卷派生；已有非作废终态时 MUST
返回原答卷，MUST NOT 因客户端传 `FIRST` 插入新首次作答。
