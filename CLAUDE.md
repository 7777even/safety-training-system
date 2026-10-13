# Claude Code 项目规则

## 1. 规则入口

Claude 在本仓库工作时，必须先读取根 `AGENTS.md`，并继续读取目标目录及其父目录适用的分层 `AGENTS.md`
。OpenSpec、TDD、验证、Review、权限边界和文档目录职责均以这些规则为准。

本仓库含前端。当 `git config user.name` 为 **`lims`** 时，Claude **只写后端、不关注前端**：

- 禁止修改 `frontend/`（除非用户当场授权写前端）。
- 写 OpenSpec 时设计/任务不含页面、Mock、前端联调；交付后端 + 规范的 `docs/api/*.openapi.json` 给前端即可。
- 细则：根 `AGENTS.md` §17.1；`.cursor/rules/backend-only-collaboration.mdc`。

本文件只补充 Claude 必须遵守的端到端开发路线，不替代 `AGENTS.md` 或 OpenSpec。

## 2. 开发路线真相源

- 长期路线：`docs/requirement/end-to-end-development-roadmap.md`
- 进度记录：`engineering/plans/end-to-end-development-progress-tracker.md`
- 业务规格与实施任务：当前已确认的 `openspec/changes/<change>/`，其中 `tasks.md` 是唯一任务依据
- API Contract：`docs/api/`（编写规范：`docs/architecture/openapi-contract-writing-guide.md`；四条铁律：按 Controller 分类、接口注释、字段注释、example；样板：`asset.openapi.json` / `org.openapi.json`）

## 3. 业务开发顺序

Claude 必须顺着依赖主线推进：

1. 素材基础 CRUD 与课程闭环：补齐机构/参与范围、素材元数据和文件关联 CRUD、管理端真实联调、学员 PC/移动学习和端到端验收；转码不前置。
2. 题库与试卷：知识点、题目版本、导入导出、组卷和试卷版本。
3. 考试与练习：安排、作答、判分、批阅、成绩发布、补考/练习及课程考试项回接。
4. 培训标准与计划输入：监管关系、类型、标准、模板、严格机构隔离和评价表单。
5. 培训计划与执行：计划、参训人、线上/线下实例、签到、成绩、奖惩和教师任务。
6. 问卷、评价与综合结果：问卷、评价、来源明细、计算批次和正式结果。
7. 档案与证书：三级档案、归档版本、证书模板和签发闭环。
8. 运营分析与工作台：真实待办、统计、下钻和导出。
9. 园区监管监控与预警：接入稳定事实，完成规则、证据、预警和处置。
10. 素材转码与媒体增强：最后实施真实异步转码、回调、派生文件、预览/播放、失败恢复、课程媒体学习回接和监控。

仿真软件、仿真练习/考试和人脸识别本期延期，未经独立 OpenSpec Change 确认不得实施。

## 4. 执行与状态维护

1. L3 / L4 开工前读取路线图、进度表和当前已确认 Change，只实现当前 `tasks.md` 范围。
2. 路线图是依赖顺序，不是开发授权；不得借路线图提前开发后续阶段或自行扩大需求。
3. 前一阶段未形成真实纵向闭环时，不为后续阶段创建占位业务实现。
4. Mock、桩、占位页、设计草稿 DDL 或单层代码不得标记为端到端完成。
5. 统计、档案、证书和预警必须建立在稳定、可追溯的业务事实及已确认口径上。
6. 完成 Task 后先更新 OpenSpec `tasks.md`，再更新进度表的状态、负责人、日期、验收证据、阻塞项和行更新时间。
7. 只有端到端验收、自动化测试、双轴 Review 和文档同步全部通过，才关闭对应里程碑。
8. 进度表不承载实施细节，不得成为 `openspec/` 之外的第二套 Task。
9. 素材阶段当前只做基础 CRUD；阶段 1～9 不开发真实转码和媒体派生，完成 M5 后才能为阶段 10 创建并确认独立 Change。

## 5. Admin 模块两层测试规则

适用范围：`backend/sts-service-admin/src/main/java/com/sts/admin/**`。详细设计和样例见
`docs/architecture/test-strategy.md`；实际 source set 与命令以 `backend/sts-service-admin/pom.xml` 为准。

### 5.1 分层边界

1. 零 DB 测试放 `src/test/java`、类名 `*Test`，验证业务逻辑和 HTTP 契约。
2. 带 DB 测试放 `src/integrationTest/java`、类名 `*IT`，通过 Testcontainers MySQL 验证真实 SQL、MyBatis 配置和数据库约束。
3. 两层测试不得重复堆同一业务分支；mock Mapper 能验证的逻辑留在零 DB，只有真 SQL/落库才能证明的语义放 IT。
4. Admin 新增行为或缺陷修复必须在正确层执行 Red → Green → Refactor。

### 5.2 零 DB 原则

1. 不启动完整 Spring 上下文，不连接 MySQL、Redis、Nacos，不启动 Testcontainers。
2. Controller 用 `MockMvcBuilders.standaloneSetup` + mock Service；分页接口使用真实 `PageRequestArgumentResolver`。
3. `XxxControllerTest` 覆盖参数/路径透传、分页与 `query.*` 绑定、CRUD 委托、`@Valid` 失败 400 且不进 Service。
4. 横切 HTTP 契约变化时用 `XxxControllerWebSliceTest` 挂真实 `HttpApiExceptionHandler` 和
   `ResultEasyTransAdvice`，验证 Result 包装、真实失败状态码、1001、401/403 和脱敏；业务协作方仍 mock。
5. Service/认证/缓存用纯 Mockito 手动构造，覆盖成功路径及存在性、唯一性、superAdmin、占用、状态和权限护栏；同时断言
   `BusinessException` 状态、业务码、关键副作用和未发生调用。
6. `StpUtil` 用 try-with-resources 内的 `mockStatic`；修改静态全局对象必须在 `@AfterEach` 还原。
7. 密码哈希使用真实 `BCryptPasswordEncoder` 验证。
8. 依赖 `TableInfoHelper`、`LambdaUpdateWrapper.set`、真实逻辑删除、分页/乐观锁插件或 SQL 的行为转入 IT，不在单测硬凑。

### 5.3 带 DB 原则

1. 继承 `AbstractMysqlIntegrationTest`，共享 `mysql:8.0.45` 容器并用 `@DynamicPropertySource` 注入数据源。
2. 使用 `MapperItConfig` 等最小 DataSource/MyBatis 切片，禁止为了持久化验证启动完整应用、Web、Redis 或 Nacos。
3. schema 复用 `backend/sql/` 中由 POM 拷入的脚本，禁止在测试目录复制第二份 DDL。
4. 默认使用 `@Transactional` 自动回滚；数据自包含，不依赖执行顺序或历史残留。
5. 真库覆盖 Entity/列映射、审计填充、分页、乐观锁、`deleted=主键`、软删后复用唯一值、唯一索引、关联表物理删除/重建、
   自定义 join/递归 CTE 和过滤条件。
6. 只有验证 Service 事务与真实持久化协作时才用最小 `@TestConfiguration`；其余优先 Mapper IT。
7. `JdbcTemplate` 只用于绕过 `@TableLogic` 读取原始列，不能替代被测 Mapper 写路径。
8. 乐观锁过期对象手工创建并设置旧 `version`，避免 MyBatis 一级缓存掩盖冲突。
9. Docker 不可用时如实报告 IT 未执行，不能用零 DB 通过代替。

### 5.4 命令与交付

在 `backend/` 目录执行：

- 零 DB：`mvn -pl sts-service-admin -am test`
- 带 DB：`mvn -pl sts-service-admin -am -Pintegration-test test`

参考零 DB 样例：`SysUserControllerTest`、`SysUserControllerWebSliceTest`、`SysUserServiceImplTest`。参考带 DB 样例：
`SysUserMapperIT`、`LogicDeleteServiceIT`、`RbacPermissionQueryIT`、`AssignmentMapperIT`、`MenuCrudPersistenceIT`。

Admin 代码修改至少运行受影响零 DB 测试；涉及 Entity、Mapper、SQL、schema/索引、逻辑删除、分页、乐观锁或持久化事务时同时运行相关
IT。
交付必须报告实际命令、结果和未运行项。
