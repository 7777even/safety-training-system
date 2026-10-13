# 第四批T1学习首页与消息验证记录

初始日期：2026-10-08，最新续办2026-10-09；下方续办记录优先于原始记录。Owner：fxw11。关联Change：2026-10-08-complete-batch4-learner-center。范围47/61；50非微信及52尚未进入实施。结论：原确认T1接口与前后端已实现，业务未关闭。

## 实现与回归证据

- PC门户首页、H5首页读取本人课程、考试/测试、培训任务，保留原引用与动作；消息中心提供持久化分页与受控跳转。
- 消息与接收人同事务持久化，唯一键防重复；课程安排/上下架、考试安排/开考、课程测验、培训安排/启动/取消接入。60秒逐租户扫描有效任务补通知及截止24小时催办；已交卷/完成/取消/退出不催办。
- SSE每3秒按在线用户分批计数查询；提交后变化触发页面补读，断线重连重新查询，失效会话关闭。两端Authorization请求、心跳、退出清理均已接入；不引入已读状态。
- 已同步learner.openapi.json、共享类型、Controller/VO、init、V20261008180000、schema-version及测试基线。真实接口直接接入，没有保留本批假数据运行路径；未用Mock替代验收。
- Red证据：新增首页/消息Service缺失；四页面慢查询期间事件刷新丢失（前端6测中4失败）；CONTINUE误跳须知（路由测试失败）；超过200条聚合入口缺失。最小修复后对应测试通过。

## 实跑结果

| 检查 | 结果 | 边界 |
|---|---|---|
| 后端公开Service/Controller/SSE定向单测 | 10通过，0失败/错误 | 首页分类及401条考核一次聚合、练习入口、重复收件人、截止窗口、提交状态裁剪、HTTP分页、真实MVC SSE事件/心跳/重连/撤销会话 |
| 两端行为回归 | Node 6通过 | 业务引用/继续入口、SSE分片与Authorization/退出、PC/H5四页面慢查询补读；生命周期脚本测试不等同浏览器验收 |
| PC/H5 typecheck与定向lint | 通过 | 仅受影响端及文件；未执行全端build |
| shared typecheck/定向lint | 通过 | workspace的tsc入口缺失，使用既有vue-tsc执行shared/tsconfig；未安装依赖 |
| OpenSpec strict、OpenAPI JSON/内部引用、diff check | 通过 | 规格/静态契约校验 |
| LearnerMessageIT + FlywayMigrationIT | 环境阻塞，17错误 | 无有效Docker环境；全部在容器初始化前失败，未运行真实SQL/迁移 |
| PC/H5普通学员真实登录验收 | 未验 | 尚缺可用消息表迁移环境；另发现须知管理权限依赖，详见下文 |

后端原target中出现IDE生成的Unresolved compilation problem class；清除Admin增量编译索引后仍有并发污染。最终在`.workbuddy/tmp/batch4-backend-verify`源码副本运行同一后端模块定向命令，保留原运行服务的编译目录。报告在副本sts-service-admin/target/surefire-reports；IT报告与单测报告并存。

定向命令：`mvn -pl sts-service-admin -am "-Dtest=LearnerHomeServiceTest,LearnerMessageServiceTest,LearnerNotificationServiceTest,LearnerControllerTest,LearnerMessageStreamTest,LearnerAssessmentCenterServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test -q`；IT：`mvn -pl sts-service-admin -am -Pintegration-test "-Dtest=LearnerMessageIT,FlywayMigrationIT" "-Dsurefire.failIfNoSpecifiedTests=false" test -q`。

## 双轴Review及开放事项

独立规格Review和代码质量Review均完成静态检查。已修复：首页未订阅实时变化、消息页busy丢刷新、Mapper响应泄漏Controller、考核分页重复全量聚合、通知SQL仅空值测试、独立练习缺入口。来源IT新增正向课程/随堂测试/培训/独立考试，跨机构、培训考试排除、报名/关注去重，但未实跑SQL。

开放P2：普通学员START进入既有须知页，PC getExam与H5 getLearnerExamNotice调用GET /api/exam/exams/{examId}，该接口要求exam:exam:list；本人任务可见仍会403。CONTINUE已修为原本人作答入口。拟新增本人须知GET /api/exam/learner/exams/{examId}/notice，权限沿用exam:learner:task:list并验证本人考生关系，只返回须知摘要；该新增API不在原确认表，补充设计已写入design.md，待人工确认。

关闭前必须：确认并实施本人须知补充；恢复Docker跑17项IT及消息迁移；用真实普通学员双端验证安排/在线推送/离线查询/非本人隔离/START/CONTINUE/结果/下架取消跳转。按本轮确认计划，T1关闭后才开始T2/T3，50保留微信开放子项。

本轮未提交Git、未部署、未修改远程数据库。用户并行application.yml、PC/H5 vite配置及第三批QA记录改动保留，未回退。

## 2026-10-09 续办与缺表修复（最新结论）

2026-10-09续办：本人须知按用户补充确认实现；local/vpn启用Flyway并补合并配置回归。用户确认VPN共享开发库仅缺20261008180000，已由Flyway执行并validate成功，IDEA20080消息接口code=0。后端12项/前端6项目标回归及H5类型检查通过，独立双轴复核通过。昨日消息IT3+消息迁移IT1+Flyway目标方法1通过，旧Flyway3项种子重复错误保留；今日复跑容器JDBC初始化连续失败，未得本轮通过结论。真实PC/H5首页、消息、考试发布在线自动新增、H5本人须知及开考、PC继续同一答卷已取证；真实HTTP验证未分配学员无任务/消息、非本人须知/课程拒绝及离线补读。培训生命周期、截止窗口、失效消息完整双端验收仍开放。按AGENTS.md一次只实施一个Task、连续测试失败立即暂停及本批前置顺序，T1保持开放，T2/T3未编码，未登记整批完成。验收步骤见engineering/qa/2026-10-09-batch4-verification-guide.md。

根因：IDEA默认profile=vpn，继承基础flyway.enabled=false，只有test启用迁移。local/vpn显式启用后仍保留validate-on-migrate=true、baseline-on-migrate=false和clean-disabled=true；未更改用户连接和默认profile。共享开发库此前最新版本20261008140000，本次只执行20261008180000，未清库、未手工写迁移历史、未操作生产库。调用本机IDEA接口返回code=0/message=success/total=0。

真实交互环境为独立本机batch4qa：后端20082、PC5175、H55176、MySQL23306/Redis26379。普通学员仅授予既有本人入口权限，未修改共享库角色。截图：.workbuddy/tmp/batch4-evidence/pc-continue-exam.png、h5-exam-attempt.png。

今天IT容器启动后JDBC不可达，停止该次复跑；昨日5项定向IT通过证据保留，不将本日或旧Flyway全类登记通过。T1完整验收仍未关闭，T2（50非微信）/T3（52）未实施。
