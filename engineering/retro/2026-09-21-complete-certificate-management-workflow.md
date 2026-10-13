# Retro：补齐证书模板设计与签发规则管理闭环

## 做得好的地方

- 将“模板可编辑”明确为唯一可覆盖 DRAFT，而不是直接修改 PUBLISHED，发布历史保持不可变。
- 真实 MySQL IT 覆盖了逻辑删除唯一键复用、草稿整体替换、旧发布版归档和乐观锁，发现了 Mock 无法暴露的逻辑删除写法问题。
- 统一候选在服务端收敛课程、ONLINE 理论考试和培训计划的可绑定状态，前端无需复制跨域规则。
- 权限节点同时进入 Controller、OpenAPI、Admin-init 和 migration，并用前一版本基线验证实际升级。

## 发现的问题

- MyBatis-Plus 的 `@TableLogic` 字段不会因普通实体赋值自动参与 `updateById`；需要显式 update SQL 才能使用“主键写入
  deleted”释放联合唯一键。
- 旧 Flyway 集成测试夹具曾依赖已删除 migration target 和过时 init 结构；已删除失效的执行归属升级用例，并在学习规则脚本前还原旧表，整类可随当前
  classpath 中的 V 文件执行。
- 原证书 OpenAPI 虽已有模板 GET/PUT 路径，但只描述基础元数据，不能代表设计工作台已经闭环；验收必须继续下钻请求和响应
  Schema。

## 后续约束

- 模板公开元素继续只允许 TEXT/VARIABLE；底图统一由版本的 `backgroundFileId` 表达，不能重新引入 IMAGE 元素。
- 所有规则创建/更新都必须复用同一绑定校验，不允许 Controller 或前端绕过已发布模板与来源状态约束。
- 新 migration 的测试优先从紧邻前一版本 baseline 启动；依赖已移出 classpath 的旧 V 文件的升级夹具应删除或改为还原旧表，而不是继续用最新
  init 硬演过期路径。
- 档案签到能力不与证书管理混做；没有确认的 ATTENDANCE 事实时继续返回既有 409/9204。
