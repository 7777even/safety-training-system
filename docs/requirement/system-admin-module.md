# 系统管理模块概述

`sts-service-admin` 提供安全教育培训系统的后台底座：组织与 RBAC、字典与参数、日志审计、缓存运维、文件元数据。

## 能力矩阵

### 1. 组织与 RBAC

| 能力 | 说明 |
|------|------|
| 用户 | `us_sys_user` + 凭证表 `us_sys_auth_identity`；密码不落用户主表 |
| 角色 | `us_sys_role`，`role_code` 全局唯一 |
| 菜单 | 目录(M)/菜单(C)/按钮(F)；结构以种子数据维护，管理端主要调可见性 |
| 部门 | 树形组织（`parent_id`）；培训场景下表示监管/企业/机构等组织层级 |
| 在线用户 | 基于 Sa-Token 会话（Redis）；支持强退 |

### 2. 元数据与运维

| 能力 | 说明 |
|------|------|
| 字典 | `us_sys_dict_type` / `us_sys_dict_data`；业务 VO 不回写 `*Text`，前端用字典目录翻译 |
| 参数 | `us_sys_config`；变更后刷新缓存 |
| 缓存管理 | 查看 L1 域并整库/按域清理；Pub/Sub 通知集群（详见 API 文档） |
| 登录日志 / 操作日志 | 只增不改流水表 |
| 文件 | 本地存储（可扩展对象存储）；元数据入库 |

## 权限链路（摘要）

1. 登录签发 Sa-Token 会话。
2. 接口用 `@SaCheckPermission` 校验 `perms`。
3. 超管角色走通配旁路（见 [super-admin-bypass.md](../architecture/super-admin-bypass.md)）。
4. 前端按钮权限与后端校验双防线（见 [button-permission.md](../architecture/button-permission.md)）。

## 相关文档

- 详设：[system-admin-detailed-design.md](./system-admin-detailed-design.md)
- OpenAPI：[../api/system-admin.openapi.json](../api/system-admin.openapi.json)
- 初始化：[../deployment/admin-init.md](../deployment/admin-init.md)
- DDL：[../database/ddl-conventions.md](../database/ddl-conventions.md)
