# Tasks：课程考试内容项绑定与正式成绩回接

前后端影响：仅后端；前端以 OpenAPI 为准另开。Owner：lims

> 状态：已确认实施。类型映射和完成口径见 proposal / design。

## Task 1：完成课程考试资源绑定与锁定

- 状态：已实现，MySQL IT 环境阻塞
- Owner：lims
- 输入：课程项既有 examId 字段、考试资源、EXAM USE 授权、已确认类型映射
- 输出：Item create/update 校验、只读考试展示字段、锁定规则、course OpenAPI
- 前后端影响：仅后端；前端以 OpenAPI 为准另开
- API 依赖：`docs/api/course.openapi.json`
- 数据影响：无 schema 变化
- 验收标准：考试存在/发布/启用/授权均由服务端验证；参与后禁止换考试；ASSET 与考试引用互斥
- 测试方式：TDD 零 DB + Mapper IT

## Task 2：完成课程上下文开考与成绩发布回接

- 状态：已实现，MySQL IT 环境阻塞
- Owner：lims
- 输入：Task 1、课程报名/版本快照、考试 Candidate/Attempt、正式成绩发布
- 输出：课程内容项开考入口、课程上下文 Candidate、课程任务中的考试状态/成绩可见性、发布成绩幂等回写与课程完成态重算
- 前后端影响：仅后端；前端以 OpenAPI 为准另开
- API 依赖：`docs/api/course.openapi.json`、`docs/api/exam.openapi.json`
- 数据影响：新增 Candidate 课程上下文唯一键 migration；既有非课程 Candidate 的课程字段归一为 0
- 验收标准：同一考试跨课程隔离；课程任务批量返回 active/published
  Attempt、考试任务状态、发布状态和按考试规则裁剪的正式成绩；只回写同报名同内容项；仅正式通过结果完成内容项；重试幂等
- 测试方式：TDD 零 DB + Mapper IT + OpenAPI 校验

> 已于 2026-07-29 确认并实现 Candidate 唯一键 migration；MySQL Testcontainers 因 Docker socket 不可用未执行，待环境恢复后完成
> IT。
