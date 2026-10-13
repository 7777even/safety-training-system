# 第七批验证记录

## 当前范围
用户2026-10-11确认方案。当前T1～T3开发完成；53/71待真实验收。T2已人工确认复用课程及发布版本内课件，T4～T7未实施。短信延期，85不整项关闭。不做本地真实登录、浏览器或真机验收；WorkBuddy负责真实测验。

## T1实现
- 手工人员特征与只读培训事实分开；姓名、部门、岗位、标签复用既有主数据。
- 固定条件分类：同字段多选并集，不同字段交集；缺失属性不匹配指定条件；年龄按当前日期计算。
- 动态培训状态与学时复用正式只读结果服务，执行中实时推导，结束后读取冻结事实；不调用同步或写数据库。
- 用户组保存条件，可实名预览和停用；T3已接入统一人员选择下发，按当次当前成员保存具体参训名单。
- PC用户管理新增特征入口，新增组管理菜单；后端鉴权、租户校验、版本冲突、字段清空、OpenAPI与数据库资产同步。
- migration `V20261011090000__add_person_classification.sql`；T1基线20261011090000（当前最新基线20261011110000）。新增菜单9830～9833，已有角色不自动扩权，新开户由现有权限绑定流程纳入。

## 自动化证据
后端在backend运行：
```powershell
mvn -pl sts-service-admin -am test "-Dtest=PersonClassificationServiceTest,PersonProfileServiceTest,PersonClassificationSqlTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```
12项通过（8分类、3特征、1真实MyBatis动态SQL展开）。动态事实测试确认来源结果变化后人员筛选变化，即使汇总列仍为旧值。

PC在frontend/pc-web运行：
```powershell
node --test tests/person-classification.test.mjs
pnpm typecheck
pnpm exec eslint src/api/person-classification.ts src/components/organization/PersonProfileDialog.vue src/views/admin/system/org/group/index.vue src/views/admin/system/org/user/index.vue --max-warnings=0
```
4项行为测试、typecheck及目标lint通过。行为包含预览请求、组创建/编辑版本、清空/错误响应及人员弹窗乱序响应隔离。

OpenSpec strict、契约本地引用检查及git diff --check通过。构建开始发现target/classes缺失与增量编译误判，仅清理后端构建产物后重编译恢复；不改业务依赖。

## MySQL IT：环境阻塞
`docker info`失败：`dockerDesktopLinuxEngine`命名管道不存在。尝试PersonClassificationIT得到`Could not find a valid Docker environment`，测试未执行成功。

已补5项分类IT：年龄边界/缺失特征、真实清空与乐观锁、跨租户、部门后代＋岗位/标签交并集、培训关联/撤回/逻辑删除/有效奖惩。另补Flyway升级测试，核对人员保留、新表/菜单及不扩权；这些均**未通过运行验证**。

恢复Docker后运行：
```powershell
mvn -pl sts-service-admin -am -Pintegration-test test "-Dtest=PersonClassificationIT,FlywayMigrationIT#shouldUpgradePersonClassificationWithoutGrantingExistingRoles" "-Dsurefire.failIfNoSpecifiedTests=false"
```

## 双轴独立Review
规格与代码质量两轴独立检查，发现人员切换异步响应可能误写及SQL事实分支覆盖缺口；修复后两轴复审均未发现新增阻断问题。Review未运行真实测验，不能替代SQL IT。

## WorkBuddy T1验收步骤（未执行）
1. 在测试环境应用迁移；为测试角色显式授予特征查看/维护、组查看/维护及需要的人员、部门、岗位、标签、计划读取权限。用另一租户及无权限角色作反向验收。
2. 用户管理→人员特征，维护出生日期、性别及全部文本特征；保存后重开核对。清空原值后重开应保持空，旧版本保存应拒绝。快速切换A/B不能串人员。
3. 人员分类与用户组→新增条件组，组合部门父节点、多个岗位/标签、年龄及文本。预览应匹配真实姓名；缺失特征不匹配，零匹配正常展示空名单。
4. 组合培训计划和PASSED/FAILED/INCOMPLETE筛选，对照已有培训统计；结果应同源，查询前后培训事实及版本不发生写入。
5. 修改人员特征后重新预览同组，结果应按当前条件变化；原有课程/考试/培训名单不变化。停用组仍可管理，后续T3会拒绝下发停用组。
6. 对其它租户人员和组ID直接请求应拒绝或返回无匹配；无权限角色不得访问受保护接口。

本记录不包含矩阵、审批、年度文件、考试岗位/组选择的完成声明。

## T2岗位矩阵交付
矩阵按岗位维护内容、课程版本及学时，课件沿用课程不可变发布版本。选中多项进入现有计划工作台核对基础信息后创建草稿；相同课程及版本合并取最大学时，课程来源和每条矩阵要求冻结，来源随后编辑不改变计划。发布优先保留明确选中的版本；未指定版本的原路径继续冻结课程当前版本。

名称最多150字、单项及合并阶段学时不超过999999.99，与既有计划表容量一致；不足编排仍在现有工作台补齐，不自动发布。新增矩阵菜单9840/9841，不扩权既有角色。

迁移 `V20261011110000__add_training_matrix.sql`，init/schema版本20261011110000。后端TrainingMatrixServiceTest 5项＋TrainingPlanPublishValidatorTest 23项=28通过；前端training-matrix 2项＋training-workspace-ui 18项=20通过。PC typecheck、目标lint、契约引用、strict通过。双轴Review提出容量边界问题并已修复、复审通过。

## T3按分类下发交付
统一人员选择新增岗位和用户组；服务端校验归属/启用状态，空组或空岗位明确拒绝。候选查询支持岗位/用户组/部门/姓名交集。失败清空旧候选并中文提示，查询代次隔离迟到响应。现有考试和课程调用同一选择器，业务自身的人员资格校验继续保留；T7专项资格与通知验收尚未实施。

TrainingPlanServiceImpl.bindSelection保存当次展开的具体参训人，重复选择不重复插入，人员/组改变不动态删除旧名单；既有发布后名单保护继续有效。LearnerNotificationAspect拦截培训发布，LearnerNotificationSourceMapper.trainings从具体参训关系取接收人，沿用事件键和接收人唯一约束。PC/H5沿用原消息和培训详情，无新增移动页面。

后端52项通过（PersonSelectionServiceImplTest 6、PersonPickerServiceImplTest 1、PersonClassificationServiceTest 8、TrainingPlanServiceImplTest 32、LearnerNotificationServiceTest 2、LearnerMessageServiceTest 2、PersonPickerControllerWebSliceTest 1）。前端37项通过（5分类下发行为、2人员选择、30培训编排）；PC typecheck/目标lint通过。双轴Review修复候选旧名单残留及IT学时夹具后复审通过。

运行命令：
```powershell
mvn -pl sts-service-admin -am test "-Dtest=PersonSelectionServiceImplTest,PersonPickerServiceImplTest,PersonClassificationServiceTest,TrainingPlanServiceImplTest,LearnerNotificationServiceTest,LearnerMessageServiceTest,PersonPickerControllerWebSliceTest" "-Dsurefire.failIfNoSpecifiedTests=false"
node --test tests/person-selection-dispatch.test.mjs tests/person-picker.test.mjs tests/training-composition-linkage.test.mjs
```

扩大检查 `exam-management.test.mjs` 共17项，10通过/7失败；在HEAD导出基线复现相同7项。失败涉及旧工作台路由/属性静态断言、缺失的grading页面路径与data URL中@shared模块解析；本次未修改这些考试业务文件，不以定向测试通过宣称全套测试通过。T2改变创建分支导致的一项培训编排断言已同步，30项培训编排全部通过。

## T2/T3 SQL验证边界
新增 TrainingMatrixIT（乐观锁、冻结来源、租户）、TrainingMatrixTransactionIT（真实事务内编排失败回滚创建计划）、PersonDispatchIT（动态组变化不修改已保存具体名单），及Flyway矩阵升级保留角色授权测试。

尝试TrainingMatrixIT/PersonDispatchIT运行得到 `Could not find a valid Docker environment`，未运行成功。所有新增IT经 `-Pintegration-test test-compile`编译；SQL运行、事务回滚和Flyway真实验证仍待Docker恢复，不用单测替代。

恢复Docker后：
```powershell
mvn -pl sts-service-admin -am -Pintegration-test test "-Dtest=PersonClassificationIT,TrainingMatrixIT,TrainingMatrixTransactionIT,PersonDispatchIT,FlywayMigrationIT#shouldUpgradePersonClassificationWithoutGrantingExistingRoles+shouldUpgradeTrainingMatrixWithoutGrantingExistingRoles" "-Dsurefire.failIfNoSpecifiedTests=false"
```

## WorkBuddy T2/T3验收步骤（未执行）
1. 测试环境应用两份迁移，关闭API Mock；显式授予矩阵查看/维护、计划新增/查看/编辑/发布、参训绑定、已有岗位/课程引用/用户组/人员读取权限。已有角色不应自动新增授权。
2. 岗位培训矩阵创建同一课程版本2、3学时要求与另一课程要求；选择后进入计划工作台，核对基础信息再保存。应生成草稿、同版本仅一个内容取3学时，显示来源岗位和完整要求，不产生发布或学员任务。
3. 在工作台核对并补足计划必需配置；修改原矩阵、发布课程新版本，计划矩阵来源和所选旧课程版本应保持。发布预检不因课程的新版本自动换课件。
4. 名称超过150字、单项学时超限及合并总学时超限应拒绝；停用/外租户岗位、矩阵或课程版本应拒绝，失败不得遗留半个计划或来源。
5. 创建具有当前成员A/B的条件组，通过计划现有参训选择“按用户组”绑定，列表显示具体A/B。修改组条件或人员属性后再次预览应变化，但旧已保存参训关系仍保留；重复绑定不重复生成关系。
6. 发布并安排培训内容，学员PC/H5在现有消息页收到培训消息并打开本人的培训任务。更改组条件后不向新成员自动发旧计划消息；重复扫描不重复通知；到期/完成行为对照现有提醒规则。
7. 候选列表先查询全员，再筛空组/空岗位：中文提示并清空候选；快速切换筛选不能被迟到响应覆盖。部门＋岗位＋组＋姓名应取交集。
8. 无权限、其它租户ID及停用组/岗位直接请求应拒绝；已发布计划不得用组变化改写名单。

当前阶段只完成T1～T3代码；T4～T7继续开放，53/71/72/73/85均不直接勾选关闭。

## T4年度文件及首次审批（开发完成，待真实验收）
新增公司年度文件独立菜单（9851）、维护权限9852及审批权限9850，仅登记菜单，不给既有角色扩权。沿用新机构管理员菜单授权流程。绑定年度文件保存原始年度/名称/文件快照；文件不解析为计划。单级不同审批人有实际权限和机构校验，审批记录不与执行状态混用。

共用计划行锁覆盖全部草稿修改与审批；人员新增/批量绑定/移除、年度关联均推进计划版本。批准只对确切版本生效，待审锁定，驳回重提保留历史，历史已发布计划不补造审批。

证据：TrainingPlanApprovalServiceTest 9、TrainingAnnualPlanServiceTest 4、TrainingPlanServiceImplTest 32、TrainingWorkflowServiceImplTest 34，共79项通过。PC审批5＋培训相关54=59项通过，typecheck和6个目标文件lint通过；OpenSpec strict、契约引用/菜单唯一性、diff通过。独立规格及代码质量Review修复名单移除门禁及前端旧版本后复审通过。

新迁移V20261011120000__add_training_plan_approval.sql、init和测试基线20261011120000。TrainingPlanApprovalIT 3项覆盖提交/编辑行锁串行、旧版本拒绝、租户和年度关联事务回滚；新增升级不扩权/无历史补造IT。所有IT源码编译通过，运行报Could not find a valid Docker environment，因此SQL、行锁及迁移**未验证**。本轮不启动浏览器、真实登录或真机。

### WorkBuddy T4验收步骤（未执行）
1. 测试环境应用新增迁移，新机构及既有角色显式配置年度查看/维护、计划读取/编辑/发布、另一人员的计划审批权限。升级前后原有角色授权不应自动扩大。
2. 年度培训计划入口填写年度和名称、上传文件；编辑替换后列表及受控下载正确。另一机构文件ID、年度ID及无权限直接请求必须拒绝。
3. 在计划工作台人员与执行步骤关联年度文件并添加人员，然后提交其他审批人；自己、停用人员、无审批权限人员不可提交。
4. 待审分别尝试基础编辑、模板复制、课表/评价保存、指派/绑定/移除人员、关联年度文件、删除及发布，均被后端拒绝。
5. 指定审批人驳回（原因必填），编辑并重提，历史保留；另一普通人员不能审批。批准后由有发布权限的人下发，系统消息/名单沿用原链路。
6. 批准后尚未发布的计划任意修改及名单/年度关联修改，原批准变成旧版本记录，发布失败，必须重审。并发旧版本提交不得覆盖。
7. 替换公司年度文件，已关联计划仍下载原文件。既有已发布计划能继续正常执行，无历史审批记录自动生成。

## T5计划变更审批（开发完成，待真实验收）
独立保存原计划/阶段/人员及拟变更请求，提交时展开并冻结人员和年度文件依据；审批不直接编辑生效计划。指定不同审批人及真实权限/租户校验，批准重检计划版本/状态/学习事实，全部写入同一事务。进行中仅四项，结束/取消/归档拒绝。审批人可展开原计划与拟变更对照。

完整变更目前只允许无任何执行安排、课程开课残留、学习/作答/结果的PUBLISHED计划。已有安排即使尚未开始，也因现有跨域端口无法原子撤销重建而明确拒绝8351，符合确认方案的“无法安全同步则拒绝生效”；不宣称已实现已有安排完整同步。临时DRAFT仅事务内部用于复用校验，失败恢复全部原始数据。

Java定向121/121，PC审批及变更行为10/10，PC typecheck及5个目标文件lint通过；独立规格/代码质量复审通过。修复线下手工创建并发竞争：与审批同锁、锁后核验阶段/内容仍存在且归属匹配。迁移V20261011130000、init/schema基线同步。TrainingPlanChangeIT 4项及Flyway升级幂等/不扩权测试源码编译成功，但执行Could not find a valid Docker environment，**SQL、锁、回滚及迁移仍未验证**。

恢复Docker后（backend目录）：
```powershell
mvn -pl sts-service-admin -am test -Pintegration-test "-Dtest=TrainingPlanChangeIT,FlywayMigrationIT#shouldUpgradePlanChangesWithoutGrantingExistingRoles" "-Dsurefire.failIfNoSpecifiedTests=false"
```

### WorkBuddy T5验收步骤（未执行）
1. 测试环境应用130000迁移，给申请人显式授予training:plan:change、plan:list及所需资源读取；审批人授予plan:approve。只拥有change而无草稿edit的申请人应可读取审批候选，不扩大原角色授权。
2. 无安排的已发布计划保存完整变更：改基础信息、课表、人员组/岗位、评价、年度关联。原计划和学员任务保持不变，审批人能查看原课表/原名单与拟变更。
3. 提交后更改用户组成员及公司年度文件：审批页面仍显示提交时人员和年度文件依据；批准后只使用冻结名单并重新校验启用/租户，失效资源或人员拒绝、不得半生效。
4. 指定另一人批准，原计划版本推进、重新冻结内容，原发布时间保持；驳回原因必填，保留记录，不发送批准通知。
5. 两份申请或在待审期间启动/生成安排：过期版本不得覆盖，已有安排申请完整变更拒绝8351。并发手工创建线下场次必须串行，旧编排引用拒绝。
6. 运行中四项基础变更批准：课程/考试/签到/学习时长/成绩保持原始事实。结束/取消/归档计划不可申请。
7. 触发内容配置或年度文件失效后批准失败，原计划、编排、人员、年度依据与申请PENDING状态全部恢复。重复批准/其它租户/非指定审批人均拒绝。

## T6培训任务管理核验（开发完成，待真实验收）
|能力|真实复用路径|本次处理|
|---|---|---|
|新建/查询|管理端plan/index、workspace；TrainingPlanController create/page|复用现有筛选与草稿|
|编辑/发布|基础、课表、评价、人员；计划update/replace/bind/publish|与T4审批、T5变更门禁对齐|
|转任务/执行|PlanExecutionPanel generate → generate-executions → TrainingExecutionProvisioner；start/end/cancel|入口明确“转为培训任务”，部分安排继续处理，安排后开始才能进入本人内容|
|统计|TrainingStatisticsController/Service、training/statistics页面及计划结果|复用既有进度/课程记录/参训结果，不重复模块|
|学员消费|PC portal/training及mobile-app培训任务；消息TRAINING→本人trainingParticipantId|核验既有任务/详情/统一内容入口与考试须知|
|提醒|LearnerNotificationSourceMapper真实参训关系；LearnerNotificationService.scan|24小时边界、完成/过期排除、变更后时间事件键；数据库唯一约束仍由真实IT补验|

Java22/22、PC43/43、H514/14及PC消息消费7/7通过。PC目标lint/typecheck通过，独立规格和质量Review通过。修复PC学员API测试加载器解析真实共享客户端模块，断言当前接口实际携带platform/operatingSystem/browser；生产API保持既有契约。H5只运行受影响培训消费测试，无生产页面变更，不重复构建。无新增T6数据库表或协议。

### WorkBuddy T6验收步骤（未执行）
1. 有计划管理权限人员从列表新建、按名称/状态查询、编辑保存，完成T4审批后下发；验证首次批准版本门禁及无权限拒绝。
2. 已发布计划点击“转为培训任务”，生成课程/考试/线下安排；部分失败显示对应内容与原因，重试保留已成功安排，不重复生成。缺安排时禁止开始；已发布零内容提示申请完整变更。
3. 全部必需安排就绪后“开始计划”，具体参训学员PC/H5从培训中心或系统消息进入本人任务，课程、考试须知、线下详情均使用原入口；非本人或跨机构ID拒绝。
4. 培训统计查看计划进度及全部课程记录，核对参训明细、内容结果与原学习时长/成绩；按现有流程结束、取消及回看记录。
5. 测试环境使用受控服务端时间核验24小时内截止提醒、恰好截止/过期/完成不提醒、重复扫描唯一消息；变更后的结束时间只按当前时间和当前冻结名单提醒，旧时间不新发提醒。自动测试仅验证键与窗口，不代表实际投递。

## 当前交付状态
T1～T6均开发完成，待WorkBuddy真实验收；本次延续完成T4～T6，已达每阶段最多三个Task。T7考试人员及通知尚未专项收口，85短信明确延期。数据库IT源码编译通过但Docker不可用，SQL/Flyway、锁及回滚运行仍阻塞；53/71/72/73/85均不直接勾选整项关闭。最终schema基线20261011130000，保留用户工作树，未提交/推送。

## T7考试人员与系统通知（已确认范围开发完成，待真实验收）
考生、评卷人均支持部门/岗位/用户组及指定姓名选择。评卷人请求新增selection与兼容reviewerUserIds二选一，服务端先展开本机构启用人员并去重，整份名单校验原exam:review或*权限，成功后替换。范围为空/无资格/跨机构/停用/冲突时不删除旧名单；空固定名单表示显式清空。assignmentSource由实际范围产生，OpenAPI/DTO/types/Mock与中文来源同步。

PC沿现有人员配置工作台和统一选择器，岗位/用户组不再错误显示为人数或空人员表；保存后回读实际评卷名单和未筛选考生总数。评审发现旧组条件残留导致单改评卷再次绑定考生，新增连续保存目标测试复现后修复：绑定成功立即清待提交考生条件；筛选分页不影响配置人数。

消息沿既有发布/绑定切面、真实exam_candidate名单及FORMAL_EXAM候选ID/考试ID引用，重复发布/绑定共用稳定事件键；PC/H5消息进入本人任务，类型/动作不匹配则安全禁用。没有新增短信供应商或模拟成功。

验证：ExamServiceImplTest48、ExamReviewerEligibilityTest2、PersonSelectionServiceImplTest6、LearnerMessageServiceTest2及最新LearnerNotificationServiceTest4，总计62项唯一后端目标用例通过（首轮61，补通知后单独4项回归覆盖最新源码，不简单累加重复）。PC人员/选择及PC/H5消息消费22/22、PC typecheck、4目标文件lint通过。已有exam-management7项HEAD基线失败维持单列，未冒称全套考试回归通过。独立规格/代码质量Review修复后通过；OpenAPI本地引用、菜单唯一性、strict/diff通过。

ExamBatch7DispatchIT2项覆盖真实组展开、考生/评卷人持久化与固定名单、动态成员变化保护、停用失败旧名单保留及清空；采用真实档案快照组件并补完整Mapper依赖。源码Maven javac编译通过，运行仍报Could not find a valid Docker environment，**SQL/事务未验证**。T7没有新增数据库表/菜单/迁移，最新schema仍20261011130000。

执行证据（backend）：
```powershell
mvn -pl sts-service-admin -am test "-Dtest=ExamServiceImplTest,ExamReviewerEligibilityTest,PersonSelectionServiceImplTest,LearnerNotificationServiceTest,LearnerMessageServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false"
mvn -pl sts-service-admin -am test -Pintegration-test "-Dtest=ExamBatch7DispatchIT" "-Dsurefire.failIfNoSpecifiedTests=false"
```
PC定向：node --test tests/exam-batch7-dispatch.test.mjs tests/person-selection-dispatch.test.mjs tests/message-exams.test.mjs tests/learner-home-message.test.mjs；不启动浏览器或真机。

### 全批交接结论
T1～T7已确认本轮范围全部开发完成，待WorkBuddy真实验收。全批步骤、环境、权限、迁移及未验证边界见 [WorkBuddy交接单](2026-10-11-batch7-workbuddy-handoff.md)。人员/矩阵/计划/审批/任务/考试系统通知均有实际服务和消费入口；真实短信延期使85仍为部分完成，各功能原文项均不直接勾选关闭。Docker SQL/Flyway/行锁/回滚仍开放，未提交/推送。


## 2026-10-12 失败修复与最新复验
本次数据库17项及Flyway升级4项共21项真实通过；PC定向75项、选择器目标1项、H5定向14项通过，考试回归恢复HEAD相同7项存量失败、零新增失败。修复仅涉及测试夹具与旧选择器断言，两轴Review通过。详情见 engineering/qa/2026-10-12-batch7-fix-and-reverification.md。本段覆盖此前Docker阻塞及本批新增失败口径；仍待真实界面验收，短信延期，整项不关闭。按用户要求运行配置不提交，新库init需显式同步schema-version 20261011130000。
