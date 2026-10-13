# 第六批最小复用设计（用户已确认）
新发布快照schemaVersion=2，旧快照兼容1；显式替代旧规格“章节固定parentId=0”。
T1：DTO创建parentId默认0、更新未传保持，防环/同课程/非空删除；ancestors改longtext；冻结父关系，平铺存储、树序显示，SINGLE树序合并。
T2：复用直播和开班受众；本人参与校验启用、SCHEDULED/LIVING、时间/HTTP(S)/归属，幂等完成既有item_progress，秒数0；不新建参与表。
T3：本人详情补最近生效考核结果，正确率=正确/(正确+错误)，无已判题null，错题复用原作答结果；不新建报告API/表。
T4：ASSET可空linkedQuizItemId关联同课程QUIZ，显式入口，多个课件引用不重复计；清空/删除保护，发布校验冻结；不改素材库。
T5：主讲教师teacherUserId复用教师选择及后端资格验证，冻结teacherName；syllabus复用重点内容/教学大纲并检索，历史正文不变。
T6：QUIZ新增isFinalAssessment/passScore，最多一个启用结业项且必修；及格线默认试卷范围0~满分。除结业外必需内容完成才开考；考生行锁下复用现有开考，首次生效失败才能第二次，待批阅/通过/用尽不新建。失败不完成课程/发证；普通测验不变。新结业报名recognizedStudySeconds为0，首次合格完成冻结累计有效秒数；旧报名null回退totalStudySeconds。档案/培训用认可秒数，实际统计和creditAwarded不变。
T7：现有分类树/归类/选课大厅，父分类含启用后代，分页前合法范围筛选，分类名称/ID接口驱动；不新增岗位关系或推荐算法。
API：扩展course.openapi.json既有接口，新增POST /api/course/learning/enrollments/{enrollmentId}/items/{itemId}/live-participation和GET /api/course/learning/categories/tree。登录/本人归属校验，不授予后台分类权限。
数据库：只扩现有表必要字段，同步init/new migration/schema-version/Entity/IT。不改旧migration，不操作业务数据库，不引入依赖。
UI：沿用清爽现代风/pc-web-ui-standard/现有组件，PC Element Plus、门户/H5学习卡片，中文状态/校验。
验证：每Task Red→Green，单测及SQL/事务IT、两端行为测试/typecheck/定向lint、strict/双轴Review/QA/Retro；环境不可用明确阻塞，真实验收留WorkBuddy。

实施细节：结业标记写入现有考核执行完整快照policy，不修改历史执行；考生锁后以当前读（FOR UPDATE）读取作答，避免MySQL可重复读的旧快照。学习上报/考核结算/直播参与同样锁报名；迟到首测不覆盖已生效补测，重复结算不重复发证。课程行锁串行校验章节移动及结业配置。

并发校验收口：目录及内容项写入共用课程锁；锁后父链、内容项、引用计数及结业唯一计数采用FOR UPDATE当前读。结算在报名锁后用当前读读取进度及较新结果，即使上游提交事务已经建立RR快照也不遗漏并发事实。首次结业考生唯一键竞争后以当前读复用胜出记录。

验收后补齐：恢复种子定义的开班编辑/删除权限，仅补授已有开班查询权限角色（V20261010140000）；不新增权限模型。48名称筛选复用现有query.keyword，PC/H5与分类组合，PC查询回第一页；不增接口/字段。最终PC课程卡对齐考试中心文本，H5保持紧凑布局。
