# Spec Delta：学员只读本人线下场次

## Modified Capabilities

### training-execution-loop

学员 MUST 能用 `training:learner:task` 读取本人线下场次详情。该读取 MUST 按登录用户派生参训人，MUST NOT 使用管理端场次接口或
`training:plan:list`。不可见 MUST 返回 404/8155。enter MUST 仅返回跳转用场次 ID。
