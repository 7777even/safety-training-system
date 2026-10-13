# 系统管理详设（后端）

> 聚焦后端规约与数据/接口边界。前端页面布局不在本文展开。  
> 模块概述见 [system-admin-module.md](./system-admin-module.md)。

## 1. RBAC

- 用户主数据 `us_sys_user`；登录凭证在 `us_sys_auth_identity`（主体-凭证分离）。
- 角色 `us_sys_role`；用户-角色、角色-菜单中间表物理删除解绑。
- 菜单仅种子维护结构；运行时 API 以可见性调整为主。
- 权限串 `perms` 与 `@SaCheckPermission`、按钮权限对齐。
- 超管旁路见 [super-admin-bypass.md](../architecture/super-admin-bypass.md)。

## 2. 字典与参数

- 字典类型/数据两表；全量目录 API 见 [dict-catalog.md](../architecture/system-admin/dict-catalog.md)。
- 业务 VO **不**返回 `*Text`；前端用字典目录翻译。
- 参数 `us_sys_config`：更新后事务提交刷新缓存。

## 3. 日志

- 操作日志：`@OperLog` + AOP，记录
  CUD；见 [oper-log.md](../architecture/system-admin/oper-log.md)、[audit-log.md](../architecture/audit-log.md)。
- 登录日志：登录成功/失败流水；见 [login-log.md](../architecture/system-admin/login-log.md)。
- 两类均为 C 类表（只增不改）。

## 4. 缓存运维

- L1 Caffeine + EasyTrans ID→Name；失效走 Redisson Pub/Sub。
- 管理端清理 API 见 [cache-admin.md](../architecture/system-admin/cache-admin.md)。
- 横切契约见 [id-name-cache.md](../architecture/id-name-cache.md)。

## 5. 文件

- `StorageService` 抽象；当前默认 `storage.type=local`。
- 元数据表 `us_sys_file_info`；对象存储扩展为暂定。

## 6. 持久化基线

- 基类：`com.sts.common.entity.BaseEntity` / `BaseAuditEntity`。
- DDL：[ddl-conventions.md](../database/ddl-conventions.md)、
  `backend/sts-service-admin-init/src/main/resources/schema/schema-system.sql`。
- 逻辑删除 / 乐观锁：[logic-delete.md](../database/logic-delete.md)、[optimistic-lock.md](../architecture/optimistic-lock.md)。

## 7. 对象映射与脱敏

- MapStruct Converter：[mapstruct.md](../architecture/mapstruct.md)。
- `@Sensitive` 出参脱敏：[data-masking.md](../architecture/data-masking.md)。

## 8. API 入口

- 统一 `Result` / `PageRequest`：[api-standard.md](../architecture/api-standard.md)。
- 系统管理 OpenAPI：[system-admin.openapi.json](../api/system-admin.openapi.json)。
