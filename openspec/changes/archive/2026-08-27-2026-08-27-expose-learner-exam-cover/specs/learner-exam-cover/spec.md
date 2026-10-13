# learner-exam-cover Specification Delta

## ADDED Requirements

### Requirement: 学员必须按考试 ID 受控读取封面

系统 MUST 提供 `GET /api/exam/learner/exams/{examId}/cover`。授权边界 MUST 是考试，MUST NOT 接受客户端传入的文件 ID。默认内联；
`download=true` 时作为附件下载。可见性 MUST 与学员任务列表一致。

#### Scenario: 本人可见考试下载封面

- **GIVEN** 当前学员可见该已发布独立考试且封面文件可读
- **WHEN** 请求封面接口
- **THEN** 返回该考试真实封面字节流及安全响应头

#### Scenario: 考试对当前学员不可见

- **GIVEN** 考试不存在、未发布，或当前用户不是该独立考试 Candidate
- **WHEN** 请求封面接口
- **THEN** 返回 404 且业务码为 7001

#### Scenario: 可见考试没有可用封面

- **GIVEN** 当前学员可见该考试，但没有封面或文件不可读
- **WHEN** 请求封面接口
- **THEN** 返回 404 且业务码为 7071
