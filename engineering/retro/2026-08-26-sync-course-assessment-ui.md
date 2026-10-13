# 课程考核与独立考试前端同步复盘

日期：2026-08-26  
OpenSpec Change：`2026-08-26-sync-course-assessment-ui`

## 做得好的地方

- 以 `course.openapi.json` 与 `exam.openapi.json` 为唯一调用依据，课程 Attempt 与独立考试 Attempt 已分域。
- 契约读取测试覆盖了课程资源直连、两端课程 Attempt，以及独立考试旧上下文字段回归。

## 发现的问题与改进

- 旧独立考试门户及 Mock 中仍有 `contextType/contextId`，会让课程来源错误进入独立考试中心。
- 已在本次验收前改为 `sourceType/sourceBusinessId`，并增加回归断言；后续 Contract 变更继续先更新共享类型和测试，再调整页面。
