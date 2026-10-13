# 测试策略契约（单元测试 + 集成测试）

> 本文由模板契约改写入本仓库知识库，已对齐 `sts-*` 模块与 `com.sts` 包名；已剔除 IoT/设备/驱动相关表述。

> **依赖**：`../../architecture/stack.md`（JUnit 5 / Mockito / Spring Boot Test）、[
`optimistic-lock.md`](./optimistic-lock.md)、
> [`logic-delete.md`](./logic-delete.md)、[`super-admin-bypass.md`](./super-admin-bypass.md)、[
`password-security.md`](./password-security.md)。
> **当前强制范围（暂行）**：⚠️ **仅限系统管理模块（system-module）**。本规范虽与业务无关、设计上可整包复用,但
> 现阶段**只对 system-module 的 Controller / Service / 认证策略强制执行**;其它业务模块(如 device-module)**暂不纳入**,
> 待其正式 Implement 时再按同规范逐步扩展。
> **交付门槛**：system-module 每个交付的业务节点必须带对应测试且全绿,方可进入 Archive 阶段。见 §7。

---

## 1. 设计决策：两层测试

| 层                        | 目标         | 连真库?             | 速度 | 抓什么                                      |
|--------------------------|------------|------------------|----|------------------------------------------|
| **单元测试（零 DB）**           | 业务逻辑分支     | 否（Mapper 全 mock） | 毫秒 | superAdmin 护栏、唯一/存在校验、密码哈希、树装配、参数绑定、状态码  |
| **集成测试（Testcontainers）** | 真 SQL + 落库 | 是（临时 MySQL 容器）   | 秒级 | 逻辑删除、分页过滤、乐观锁冲突、唯一索引、ancestors 路径、字典改名级联 |

**原则**：单元测试验「逻辑对不对」（假数据）,集成测试验「SQL 与落库对不对」（真库）。集成测试**不重复**单元测试已覆盖的逻辑分支。

---

## 2. 单元测试契约（零 DB）

### 2.1 依赖与位置

- **无需改 `build.gradle`** —— `spring-boot-starter-test`（含 JUnit 5 / Mockito 5 内联 / AssertJ）由共享脚本
  `gradle/spring-boot-app.gradle` 注入。
- 单测放 `backend/sts-service-admin/src/test/java/...`,包路径与被测类一致,类名 `XxxTest`。
  **该 source set 只放零 DB 单测**;集成测试在独立 source set `src/integrationTest/java`（见 §3），两者物理隔离。
- 运行：`mvn -pl sts-service-admin:test`（只编译/跑 `src/test/java`,天然零 DB、不依赖任何外部服务，也不加载 Testcontainers）。

### 2.2 Controller 层 —— MockMvc `standaloneSetup`

```java
mockMvc = MockMvcBuilders.standaloneSetup(new SysUserController(userService))
    .setCustomArgumentResolvers(new PageRequestArgumentResolver())  // 真实分页解析器
    .build();
```

- 只装被测 Controller + mock 的 Service;**刻意不引入** `SaTokenConfig`（鉴权拦截器 / `@SaCheckPermission`）与
  `ResultEasyTransAdvice`（`Result` 包装 + EasyTrans 翻译）,故断言的是 Controller **原始返回体**。
- 覆盖：`@Valid` 校验（非法 body → 400 且不进 service）、`PageRequest` 参数绑定（用 `ArgumentCaptor` 校验
  pageNum / pageSize / `query.*`）、路径变量透传。

### 2.3 Service / 认证策略 —— 纯 Mockito

- `mock()` 全部协作方（Mapper、Converter、`CacheEvictService`…）,手动 `new` 被测 Impl。
- 断言 `BusinessException` 的 `getStatus()` + `getCode()`（护栏分支）。
- 用**真** `BCryptPasswordEncoder(10)` 验证「前端 SHA-256 + 后端 BCrypt」双层哈希（见 `password-security.md`）。

### 2.4 Sa-Token 静态调用 —— `mockStatic`

`StpUtil.login / getTokenValue / getTokenSession / hasPermission / checkLogin` 用 `mockStatic(StpUtil.class)`
（Mockito 5 内联,starter-test 自带）隔离：

```java
try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
    stp.when(() -> StpUtil.hasPermission("*")).thenReturn(true);
    // ... 调用被测方法 ...
    stp.verify(() -> StpUtil.login(2L));
}
```

### 2.5 Web 切片 —— 响应「外壳契约」（仍零 DB，属 `test` 任务）

§2.2 的 `standaloneSetup` 断言的是 Controller **原始返回体**（刻意不挂 advice）。Web 切片是它的**补充**：`standaloneSetup`
额外 `setControllerAdvice(...)` 挂上**真实的** `HttpApiExceptionHandler`（sts-common）+ `ResultEasyTransAdvice`，验证
Controller
之外的「外壳」层。service 仍 mock、不连任何中间件,所以依旧零 DB、必绿。

```java
MockMvcBuilders.standaloneSetup(new SysUserController(userService))
    .setCustomArgumentResolvers(new PageRequestArgumentResolver())
    .setControllerAdvice(new HttpApiExceptionHandler(),
                         new ResultEasyTransAdvice(mock(TranslationExecutor.class), new ObjectMapper()))
    .build();
```

覆盖点（参考 `SysUserControllerWebSliceTest`）：

- **Result 统一包装**：裸对象 / `PageResult` 返回被包成 `Result`（`code=0` / `data=...`）。
- **失败必带真实 HTTP 状态（铁律）**：service 抛 `BusinessException(409)` → 响应**真为 409** + `Result.fail(业务码)`,**绝不
  200 装失败**。
- **`@Valid` 失败** → 400 + `Result.fail(1001)`。
- **鉴权异常映射**：`NotLoginException`→401/1002、`NotPermissionException`→403/1003（验的是 `HttpApiExceptionHandler` 的映射;
  `@SaCheckPermission` 注解的**触发**是 Sa-Token 自身职责,不在本切片重验）。
- **序列化脱敏**：`@Sensitive(PHONE)`（Jackson `@JsonSerialize` 序列化器）让手机号 JSON 输出被打码,不回传明文;
  password 用 `@JsonProperty(WRITE_ONLY)` 只入不出。

---

## 3. 集成测试契约（Testcontainers）

### 3.0 独立 source set（与零 DB 单测物理隔离）

集成测试放**独立 source set** `src/integrationTest/java`,不与单测混在 `src/test/java`。这样:

- `mvn test` 只见 `src/test/java` → **天然零 DB**,不再需要 `exclude '**/*IT.class'` 这种脆弱的名字过滤,
  Testcontainers 也不泄漏进单测编译 classpath。
- `mvn -Pintegration-test test` 只见 `src/integrationTest/java`（需 docker）。
- IT 通过 `compileClasspath += sourceSets.test.output` 复用单测的公共辅助类。

`build.gradle` 配方（照抄）：

```groovy
sourceSets {
    integrationTest {
        compileClasspath += sourceSets.main.output + sourceSets.test.output
        runtimeClasspath += sourceSets.main.output + sourceSets.test.output
    }
}
configurations {
    integrationTestImplementation.extendsFrom testImplementation   // 继承 starter-test
    integrationTestRuntimeOnly.extendsFrom testRuntimeOnly
}
tasks.register('integrationTest', Test) {
    useJUnitPlatform()
    testClassesDirs = sourceSets.integrationTest.output.classesDirs
    classpath = sourceSets.integrationTest.runtimeClasspath
    shouldRunAfter tasks.named('test')
}
```

### 3.1 依赖（Testcontainers 只挂 integrationTest，不进单测）

```groovy
integrationTestImplementation 'org.testcontainers:mysql'        // 版本由 Spring Boot BOM 统一管理
integrationTestImplementation 'org.testcontainers:junit-jupiter'
```

### 3.2 容器 + 建表

```java
@Container @ServiceConnection   // 自动把容器接到 spring.datasource,无需手写连接串
static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.45");  // 与线上同版本

static {
  mysql.start();
  executeModularInitSchemas(mysql);
  registerFlywayBaseline(mysql, "20260811150000");
  Flyway.configure()
      .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
      .locations("classpath:db/migration")
      .load()
      .migrate();
}
```

- 完整 schema 以 `backend/sts-service-admin-init/src/main/resources/schema/schema-*.sql` 为真相源；测试执行相同模块
  SQL并登记 baseline，随后由
  `backend/sts-service-admin/src/main/resources/db/migration/V*.sql` 验证 Admin 增量接管，不维护测试 DDL 副本。
- 容器用 `static` → 整个测试类共用一个,只起一次。


全 `@SpringBootTest` 会去连 Redis / Nacos 然后失败。集成测试必须切薄,两种落法：

- **Mapper 层（推荐起步）**：`@MybatisPlusTest` + `@AutoConfigureTestDatabase(replace = NONE)` → 只加载 datasource +
  Mapper,
  直接验 SQL。
  顶掉依赖 Redis 的 bean（`SaTokenDao`、缓存）。

### 3.4 数据隔离

测试类加 `@Transactional` → 每个用例跑完**自动回滚**,容器里不留痕、用例互不污染。类名 `XxxIT`,置于
`src/integrationTest/java`（§3.0 的独立 source set），包路径与被测类一致。

### 3.5 参考实现（已落地骨架，照抄即可）

- 依赖来源可靠性优先：切片**不用**名称不确定的 `mybatis-plus-*-starter-test`,改用已在 classpath 的
  `MybatisPlusAutoConfiguration` + `@ImportAutoConfiguration` 显式列出;容器连接用 `@DynamicPropertySource`（比
  `@ServiceConnection` 更兼容切片,无需 `spring-boot-testcontainers`）。
- `com.sts.admin.support.AbstractMysqlIntegrationTest` — 单例 `MySQLContainer("mysql:8.0.45")`
  + Flyway `classpath:db/migration` 建库 + `@DynamicPropertySource` 注入 datasource。
- `com.sts.admin.support.MapperItConfig` — `@SpringBootConfiguration` +
  `@ImportAutoConfiguration({DataSourceAutoConfiguration, DataSourceTransactionManagerAutoConfiguration, JdbcTemplateAutoConfiguration, MybatisPlusAutoConfiguration})`
    + `@MapperScan("...admin.mapper")` + `@Import({MybatisPlusConfig, MybatisMetaObjectHandler})`。要点:
        - `MybatisPlusConfig` → 分页 + 乐观锁拦截器（两条只有真库能验的链路）。
        - `MybatisMetaObjectHandler` → insert 走真实审计填充链路;它对"未登录"已 try/catch 降级,切片里无 Sa-Token 也不炸
          （`createBy/updateBy` 填 null、`createTime/deleted/version` 照填）。
        - `JdbcTemplateAutoConfiguration` → 让 IT 能用 `JdbcTemplate` **绕过 `@TableLogic`** 直读原始列（如断言软删后
          `deleted` 列真的等于主键）。
- 具体 IT（均 `@SpringBootTest(classes = MapperItConfig.class, webEnvironment = NONE)` + `@Transactional` 回滚）：
    - `mapper.SysUserMapperIT` — insert/selectById 往返（审计字段填充）、`like` 过滤、`selectPage` 分页（total/records/pages）、
      `updateById` 乐观锁过期版本影响 0 行、`updateByIdWithVersionCheck` 过期 → `BusinessException(409, 2040)`。
    - `mapper.LogicDeleteServiceIT` — 用嵌套 `@TestConfiguration` 注册最小 `BaseServiceImpl<SysUserMapper,SysUser>` 子类,
      验 `removeById` 把 `deleted` 置为**主键ID**（JdbcTemplate 直读）、`selectById` 因 `@TableLogic` 查不到、
      软删后同 `user_code` 可重新插入（唯一键 `(user_code, deleted)` 不撞）。
    - `mapper.SysDictTypeMapperIT` — `LambdaUpdateWrapper.set` 按列改名在真库生效（§5 那条单测跑不了的坑，真库 TableInfo
      下可验）。
    - `mapper.RbacPermissionQueryIT` — 覆盖 `SysMenuMapper`/`SysRoleMapper` 的**自定义 SQL**（单测完全碰不到）：
      `selectRoleCodesByUserId`（2 表 join + `status=1` 过滤）、`selectPermsByUserId`（4 表 join + 排除 null perms /
      `is_visible=0` /
      停用角色）、`selectAccessibleMenusByUserId`（**递归 CTE** 从授权叶子向上补全父级目录）。`@BeforeEach` seed 一张小 RBAC
      图。
    - `mapper.AssignmentMapperIT` — 中间表（`assignMenus`/`assignRoles` 依赖的真库写路径）：「按 fk 全删再重插」重建授权、
      唯一键 `(role_id,menu_id)`/`(user_id,role_id)` 重复插入抛 `DuplicateKeyException`、C 类中间表**物理删除**（delete
      后行真消失）。
- build.gradle：独立 `integrationTest` source set（§3.0）;
  `integrationTestImplementation 'org.testcontainers:mysql' / :junit-jupiter'`
  （版本由 BOM 管）;`processIntegrationTestResources` 拷 schema;`integrationTest` 任务用该 source set 的
  classesDirs/classpath。
  单测 `src/test/java` 与 IT `src/integrationTest/java` 由目录物理隔离,`test` 任务不再需要 `exclude '**/*IT.class'`。
- 运行：`mvn -pl sts-service-admin:integrationTest`（需 docker）;`mvn -pl sts-service-admin:test` 只跑零 DB 单测。

---

## 4. 分层职责（谁抓什么）

| 盲区（单元测试测不到）                                           | 由谁抓                                                                          |
|-------------------------------------------------------|------------------------------------------------------------------------------|
| SQL 条件 / 列 / join 写错（`.like` 写成 `.eq`…）               | 集成测试（真 SQL 执行）                                                               |
| 实体字段 ↔ 表列 不匹配、类型错                                     | 集成测试                                                                         |
| 逻辑删除（`removeById` 把 `deleted` 置为主键）                   | 集成测试（见 `logic-delete.md`）                                                    |
| 乐观锁 `version` 冲突 409                                  | 集成测试（见 `optimistic-lock.md`）                                                 |
| 唯一索引真拦重复                                              | 集成测试                                                                         |
| `Result` 统一包装、失败真实状态码、鉴权异常→401/403、`@Sensitive` 序列化脱敏 | Web 切片（§2.5，`standaloneSetup`+真实 advice，已落地 `SysUserControllerWebSliceTest`） |

---

## 5. 已知坑（写测试必看）

- **`verify(mapper).updateById(any())` 歧义** —— `BaseMapper` 有 `updateById(T)` 与 `updateById(Collection<T>)` 两个重载,
  `any()` 无法消歧;用 `updateById(any(SysXxx.class))`。
- **`LambdaUpdateWrapper.set(Entity::getX, v)` 需 MyBatis 列缓存** —— 它在 build 期**急切**解析列名,纯单测无
  `TableInfo` 会抛 `can not find lambda cache`;而 `LambdaQueryWrapper.eq(...)` 是**惰性**解析(mock 的 mapper 从不执行
  SQL),所以安全。**结论**：含 `.set` 的分支（如字典类型改名级联）不在单测里驱动,改「类型未变」路径绕开,级联归集成测试。
- **集成测试里乐观锁/并发用例：别用两次 `selectById` 造两个副本** —— MyBatis 一级缓存（session/事务内）会让同一 id 的
  两次 `selectById` 返回**同一个对象实例**;客户端 A `updateById` 后拦截器把自增的 `version` **回写进这个共享实例**,
  「客户端 B」也跟着变成最新版本,过期写便拦不住。**正解**：过期副本要**手工 `new` 一个独立实体**、显式 `setVersion(0)` +
  只填要改的字段,不经 `selectById`（见 `SysUserMapperIT#staleCopy`）。

---

## 6. 覆盖基线

- **每个 Controller**：分页/查询 + 增改删透传 + 各写接口 `@Valid` 校验（非法 → 400 且不进 service）。
- **每个 Service**：全部护栏分支（存在性、唯一性、superAdmin 特权、占用检查）+ 核心成功路径（不落库部分）。
- **需真库的路径**（逻辑删除落库、`pageXxx` 组装、`getUserProfile`、级联）→ 归集成测试,不在单测强求。

---

## 7. 严格执行（交付门槛）

**强制范围（暂行）：仅系统管理模块（system-module）**——其它模块暂不纳入(见文首「当前强制范围」)。在该范围内,测试是三阶段流水线 *
*Audit** 阶段的一部分,不是可选项：

- system-module 内新增或修改的 Controller / Service,**必须**同步提交对应 `XxxTest`,且 `mvn -pl sts-service-admin:test` *
  *全绿**。
- 未带测试、或测试为红 → **不得** Archive、不得写 `MODULE_LOG.md`。

---

## 8. 当前实现参考（system-module）

首次落地：系统管理已建 **29 个测试类 / 162 个单元测试（零 DB）**,全绿。覆盖模块：登录、用户、角色、菜单、部门、配置、字典、
字典目录聚合、缓存管理、在线用户、操作日志、登录日志、认证凭证;其中 `SysUserControllerWebSliceTest`（7）是 §2.5 的 Web 外壳切片
（Result 包装 / 失败真实状态码 / 401·403 / 手机脱敏）。基础设施委托类的注入要点：

- `SysUserOnlineServiceImpl` —— `mockStatic(StpUtil)` + 临时替换公有静态字段 `StpUtil.stpLogic` 为 mock（@AfterEach 还原）,
  验会话→VO 映射 / 失效跳过 / userCode 模糊·ipaddr 精确过滤 / 内存分页。
- `SysOperLog`/`SysLoginLogServiceImpl` —— 只用基类 `ServiceImpl.baseMapper`,用
  `ReflectionTestUtils.setField(service,"baseMapper",mock)`
  注入;只测 `pageXxx`/`detail`/`cleanAll`（走 `selectPage`/`selectById`/`remove(wrapper)→delete`）。*
  *`deleteById`/`batchDelete`
  经 `removeById` 需 `TableInfoHelper`,纯单测抛 `MybatisPlusException` → 归集成**（同 `deleteUser`）。
- `SysAuthIdentityServiceImpl.deleteByUserId` —— 唯一方法「查该用户全部凭证 → 提取 `identityId`（过滤 null）→ `removeByIds`
  逐条逻辑删除」。用 **`spy(service)`** 只打桩最底层碰 `TableInfoHelper` 的单条 `removeById` 加以隔离,注入 `baseMapper` 让
  `list()` 真查、让 `removeByIds` 的**循环与空短路真实执行**,从而验证「提取 + 过滤 null + 逐条委托删除」整条链;
  `deleted=identityId` 落库由 `LogicDeleteServiceIT` 证明,不重复。**这是第四种基础设施注入手法（spy
  部分打桩隔离 `TableInfoHelper`）。**
  集成测试层：**Testcontainers 真库用例已系统化铺开并全绿**（独立 source set `src/integrationTest/java`，见 §3.0/§3.5）——
  2 个支撑类 + 5 个 IT、**共 16 个用例**（`mvn -pl sts-service-admin:integrationTest` 起 mysql:8.0.45 容器实跑验证过）：

| IT                      | 数 | 覆盖的「只有真库能验」链路                                                                                                  |
|-------------------------|---|----------------------------------------------------------------------------------------------------------------|
| `SysUserMapperIT`       | 5 | insert/selectById 往返（审计填充）、`like`、`selectPage` 分页、`updateById` 乐观锁过期→0 行、`updateByIdWithVersionCheck`→409/2040 |
| `LogicDeleteServiceIT`  | 2 | `removeById` 置 `deleted`=主键（JdbcTemplate 直读）、`@TableLogic` 隐藏软删行、软删后重插                                         |
| `SysDictTypeMapperIT`   | 2 | `LambdaUpdateWrapper.set` 改名                                                                                   |
| `RbacPermissionQueryIT` | 3 | 自定义 SQL：角色码/权限 join + `selectAccessibleMenusByUserId` 递归 CTE                                                   |
| `AssignmentMapperIT`    | 4 | 中间表全删重插重建、唯一键 `DuplicateKeyException`、C 类物理删除                                                                  |

**覆盖边界（何时加 IT，何时不加）**——这决定了为什么只有 16 条而不是把 100 多个单测在真库重抄：

- ✅ **该进 IT**：自定义/多表/递归 SQL、中间表写路径与唯一键、逻辑删除落库、乐观锁冲突、分页 SQL、`.set` 级联、审计填充。
- ❌ **不进 IT（否则纯重复）**：① 护栏/业务分支（存在性、唯一性、superAdmin、占用检查）——单测已用 mock mapper 覆盖;
  ② 逻辑删除 / 乐观锁 / 审计填充这类 `BaseEntity`+拦截器**横切框架行为**——同一份代码路径,**验一次即可**（已在 `SysUser`
  上验），
  逐个实体重测收益近乎零;③ 树/可见性级联等**内存逻辑**——单测已覆盖,DB 侧只剩 `updateById`+简单 eq（框架级已验）;
  ④ 平凡列往返 / 每模块分页与唯一键的机械重复。**新模块 Implement 时按这条边界补 IT,不逐方法照抄单测。**

单测 `src/test/java`（162，含 Web 切片）与 IT `src/integrationTest/java`（16）由目录物理隔离;`mvn -pl sts-service-admin:test`
只跑前者、天然零 DB。
未纳入单测的系统子模块仅剩 **文件管理（`SysFileInfo`）——该模块尚处「待设计」阶段,当前实现为占位,故暂不测**,待其设计定稿后
再按同规范补齐。补记:届时它**并非无逻辑可测**——`upload` 空文件护栏(400/5001)+ 编排 + 后缀提取、`deleteFile` 记录不存在护栏
(404/5002)+「先删存储后删库」顺序(`removeById` 同撞 `TableInfoHelper`,spy 隔离)、`pageFiles` 可选条件 wrapper,均可单测
(`StorageService`/mapper/converter 全 mock);只有真实 MinIO 字节流上传/下载属外部存储集成、本就不在单测范围。

---

## 9. 交付清单

- [x] Controller 层 `standaloneSetup` 单测（含 `@Valid`、`PageRequest` 绑定）
- [x] Service 层护栏分支单测（断言 `BusinessException` code/status）
- [x] Sa-Token 静态调用 `mockStatic` 隔离
- [x] Web 切片（§2.5）：Result 包装 / 失败真实 4xx-5xx / 401·403 鉴权异常映射 / `@Sensitive` 手机脱敏（
  `SysUserControllerWebSliceTest`）
- [x] 已知坑规避（`updateById` 消歧、`LambdaUpdateWrapper.set` 归集成）
- [x] 集成测试骨架（Testcontainers + `@DynamicPropertySource` + schema 拷贝 + 独立 source set/`integrationTest` 任务;见
  §3.0/§3.5）
- [x] 逻辑删除（`deleted=主键` + `@TableLogic` 过滤 + 软删后重插）/ 乐观锁冲突（0 行 + 409）/ `LambdaUpdateWrapper.set` 改名（
  `SysUserMapperIT` / `LogicDeleteServiceIT` / `SysDictTypeMapperIT`）
- [x] RBAC 自定义 SQL（角色码/权限 join + 递归 CTE 菜单树）与中间表写路径（全删重插 / 唯一键 / 物理删除）（
  `RbacPermissionQueryIT` / `AssignmentMapperIT`）
- [x] **16 个集成用例经 docker 实跑全绿**;覆盖边界（该/不该加 IT）见 §8
