# QA：M1 课程闭环（后端口径关闭）

日期：2026-07-24  
协作身份：`git user.name=lims`（只交付后端 + OpenAPI，不关注前端）

## 关闭口径

M1 在 **后端能力** 上关闭：素材基础 CRUD、课程编排、版本/开课/报名/学习事实/评价 API、参与范围与资源授权钩子、对应 OpenAPI 与自动化测试均已具备。  
COURSE-06/07/08（关 Mock、学员 PC/移动页面）**不纳入 lims 交付与本关闭证据**，由前端协作方按 `docs/api/*.openapi.json` 另开。

转码（COURSE-02）仍后置阶段 10，不阻塞 M1。

## 验收证据（后端）

| 能力 | 证据 |
|------|------|
| 素材 | `docs/api/asset.openapi.json`；migration `V20260722020000`；素材后端测试 |
| 课程编排 | `docs/api/course.openapi.json`；课程正式 migration；课程单测/IT |
| 开课学习 | 课程版本/开课/报名/学习 migration 与后端测试；`course.openapi.json` |
| 参与范围准入 | BASE-03；报名 `audienceId` contains 校验 |
| 跨机构课程 | BASE-04；课程详情 USE 钩子 |
| 管理端 Mock 页 | COURSE-05 历史交付；真实联调属 COURSE-06（前端另开） |

## 未纳入本关闭

- 关闭默认 `VITE_USE_API_MOCK` 与管理端真实联调（COURSE-06）
- 学员 PC / 移动学习页（COURSE-07/08）
- 素材转码与媒体预览（COURSE-02 / M6）

## 结论

进度表 M1、COURSE-09（后端验收）标记已完成；COURSE-06/07/08 记为延期（前端另开，不阻塞 lims 里程碑）。下一步可启动 M2 题库/试卷后端 OpenSpec。
