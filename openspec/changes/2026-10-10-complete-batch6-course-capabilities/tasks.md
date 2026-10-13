# 第六批已确认任务

Owner均为Codex（后端+PC管理+PC/H5学员）；一次一个Task，每连续三个Task阶段检查。

|状态|编号|输入|输出/前后端影响|API/数据影响|验收与测试|
|---|---|---|---|---|---|
|[x] T1|22|章节/内容|多层目录编辑、冻结和两端学习|章节parentId/ancestors|深层移动、防环、删除保护、单层合并、旧快照；单测/IT及前端行为测试|
|[x] T2|22/26/48|直播/报名|直播维护复制/卡片/参与|本人参与API，无新表|资格/时窗/取消/URL/幂等/零学时；单测/IT及前端行为测试|
|[x] T3|22/48|学习/答题|本人报告视图|详情API扩展，无新表|最近结果、未判题、正确率、错题详情；单测/IT及前端行为测试|
|[x] T4|67|ASSET/QUIZ|课件显式关联试题|linkedQuizItemId及冻结|同课程、清空、删除保护、发布校验、历史；单测/IT及前端行为测试|
|[x] T5|68|课程/教师|主讲教师及重点内容检索|teacherUserId/姓名冻结、syllabus复用|资格、关键词、冻结、旧空字段；单测/IT及前端行为测试|
|[x] T6|69|学习/结业测验|单次补测及认可学时|测验规则、recognizedStudySeconds|学前/待批阅/及格边界/次数/并发/幂等/下游；单测/IT及前端行为测试|
|[x] T7|62|分类/公开开班|分区岗位分类报名|学员分类树、categoryId|后代筛选/分页/公开范围/租户/报名；单测/IT及前端行为测试|
|[x] T8|67/69|菜单/权限|恢复开课编辑与删除权限|migration V20261010140000|共享库真实登录：编辑开课与配置阅卷人由 403 转为 200；IT 覆盖幂等与授权范围；四处同步|

代码通过仅记开发完成，待WorkBuddy验收。同步OpenAPI/必要数据库资产/QA/Retro/双轴Review。

阶段4（T8，验收中发现并处置）：真实验收暴露 `course:offering:edit` / `course:offering:delete` 在已部署库中完全缺失（根因：更早的 `V20260920210000` 复用了种子 `menus.json` 为这两条开课权限预留的菜单 ID 9541/9542 给监管统计，监管统计随后迁到 9810 模块并软删）。新增 `V20261010140000` 按种子口径恢复两条权限并仅补授给已持有 `course:offering:list` 的角色；同步 `sts.init.schema-version`、`FlywayMigrationIT.INIT_SCHEMA_VERSION`、`AbstractMysqlIntegrationTest.INIT_SCHEMA_VERSION` 与受影响用例期望值（4→5），并新增专用用例 `shouldRestoreCourseOfferingEditAndDeleteMenusOnUpgrade`。共享库已手工应用并真实登录复核（编辑开课 200、配置阅卷人 200、删除进入业务校验 5061 而非 1003）。

阶段1：T1-T3核心实现与后端42项、两端typecheck通过，目录行为测试2项通过；IT、独立Review和最终定向lint留最终收口。

阶段2：T4-T6复用字段、冻结与执行链路已实现，课件关联/清空/删除保护、教师资格及冻结、结业首次失败/一次补测/认可秒数的定向单测通过。认可秒数已同步培训、档案消费，普通测验及历史空值口径保持。

阶段3：T7分类树及服务端分页前筛选完成；最终后端10类175项、前端新增6项行为测试、PC/H5 typecheck及目标lint、OpenSpec strict与差异检查通过。双轴独立Review均已复审通过；并发问题修复见QA。

勾选表示开发完成，验收边界以QA最终第九节为准。WorkBuddy核心已验收；提交前补48课程名称入口（复用keyword，与分类组合，PC重置分页）通过两端目标行为测试，真实复验待补。Docker SQL/事务IT未通过，Change不归档；22仿真延期。T8新增权限升级夹具已修正为最新业务schema baseline130000只应用140000权限迁移。

验收后UI收敛（T9/T10）：按用户反馈调整PC/H5课程列表分区和卡片；最终PC对齐考试中心文本，H5紧凑展示。同步docs/design既有样式基准，真实截图见QA。QA：engineering/qa/2026-10-10-batch6-course-capabilities.md；Retro：engineering/retro/2026-10-10-batch6-course-capabilities.md。
