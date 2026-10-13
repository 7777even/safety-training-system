# 👑 超级管理员与普通管理员隔离设计契约

> 本文由模板契约改写入本仓库知识库，已对齐 `sts-*` 模块与 `com.sts` 包名；已剔除 IoT/设备/驱动相关表述。

> **适用范围**：`sts-service-admin` 模块鉴权底座与角色管理。

本规范定义了平台中【超级管理员特权代码 `superAdmin`】与【默认管理员角色 `admin`】的物理隔离与防守边界。

---

## 🏗️ 一、 角色矩阵与行为防线 (Role Matrix)

| 身份标识 (Code)      | 鉴权模式                            | 角色列表可见性                   | CUD 接口防御                      | 业务场景                           |
|:-----------------|:--------------------------------|:--------------------------|:------------------------------|:-------------------------------|
| **`superAdmin`** | **硬编码功能权限**<br>(一票放行 `*`，不查权限库) | ❌ **绝对隐藏**<br>(后端数据流源头过滤) | 🔒 **强行死锁**<br>(禁止任何修改、删除与分配) | 唯一平台账号；普通业务数据仍绑定登录企业。          |
| **`admin`**      | **标准 RBAC 链路**<br>(走缓存/数据库比对)   | ✅ **正常可见**<br>(允许在列表管理)   | 🔓 **正常开放**<br>(遵循标准权限控制)     | 默认的日常系统管理员角色，可在界面动态配置其菜单与按钮权限。 |

---

## ⚔️ 二、 核心架构防守红线 (Security Redlines)

AI 助手在处理鉴权逻辑与角色管理（Role Management）相关的查询与写接口时，必须无条件锁死以下物理红线：

### 1. Sa-Token 功能权限红线

* **规则**：在 `StpInterfaceImpl` 中注入权限时，判定 `user.getUserCode()` 是否全等于 **`superAdmin`**。
* **行为**：若是，直接返回 `Collections.singletonList("*")`。
* **目的**：确保 `superAdmin` 拥有全部功能权限且不产生多余的权限查询；该通配符不得解释为跨租户数据旁路。

### 2. 角色列表查询隐匿红线 (Read Filter)

* **规则**：在所有的角色列表查询、分页查询、下拉框获取角色的底层逻辑中，**必须追加硬编码过滤条件**：
  `WHERE role_code != 'superAdmin'`
* **目的**：确保向前端返回的数据包中，绝对不包含 `superAdmin` 角色（如果数据库中意外存在该记录），从源头上抹去其在界面暴露的可能。

### 3. 变更接口硬拦截红线 (Write Guard)

* **规则**：在角色的“新增”、“修改”、“删除”等所有可能改变数据状态的 CUD 接口入口处，第一行代码必须写死判定：
  `if ("superAdmin".equals(roleCode)) { throw new BusinessException(HttpStatus.FORBIDDEN, "系统根基特权，禁止物理变更"); }`
* **目的**：物理拦截任何越权、误操作、或通过黑客手段绕过前端直接访问后端的恶意行为。

---

## 📦 三、 初始化数据规约 (Initialization)

在 `sts-service-admin-init` 中：

1. **用户表**：初始化 `superAdmin` 用户（无角色关联）与 `admin` 用户（关联 `admin` 角色）。
2. **角色表**：**禁止** 初始化 `superAdmin` 角色；初始化 `admin` 角色作为常规管理角色。

---

## 🧪 四、 Audit 自检清单

- [x] `StpInterfaceImpl` 已将特权账号修改为 `superAdmin`。
- [x] `SysRoleServiceImpl.pageRoles` 已追加 `superAdmin` 过滤。
- [x] `SysRoleServiceImpl` 的 `create/update/delete` 已追加硬编码拦截逻辑。
- [x] `SeedDataFillerService` 或 JSON 配置已对齐双账号设计，且 `superAdmin` 无角色。
