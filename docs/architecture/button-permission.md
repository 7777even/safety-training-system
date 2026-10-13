# 🔒 按钮级功能权限控制架构设计说明书

> 本文由模板契约改写入本仓库知识库，已对齐 `sts-*` 模块与 `com.sts` 包名；已剔除 IoT/设备/驱动相关表述。

本规范定义了 **STS (统一互联)** 安全教育培训系统中，按钮操作级功能的权限控制架构。系统通过前后端双防线协同，确保功能操作的绝对安全与高并发下的极致性能。

---

## 🏗️ 一、 权限双防线拓扑 (Two-Way Defense Flow)

```text
[ 用户登录成功 ] ──► 异步加载当前主体的权限编码集合 (Permission Codes) 存入上下文/缓存
│
├──► 1. 前端视图层 (DOM Guard)
│       利用自定义指令 v-hasPermi="['sys:user:add']"
│       - 若当前主体 Code == "superAdmin" ──► 旁路放行，直接渲染按钮
│       - 若当前主体权限集包含该编码 ──────► 渲染按钮
│       - 否则 ──────────────────────────► 物理销毁/隐藏按钮，不可见
│
└──► 2. 后端接口层 (API Guard)
        利用控制器方法上的声明式注解 @SaCheckPermission("sys:user:add")
        - 第一步：校验主体 Code == "superAdmin" ──► 0 微秒特权旁路直接放行
        - 第二步：非超管用户，执行标准的集合匹配 (.contains)
        - 匹配失败 ─────────────────────────────► 强行中断请求，抛出 403 异常
```

---

## 📐 二、 核心概念：权限编码 (Permission Code)

按钮权限不依赖于 URL，而是依赖于一段人为约定的“字符串暗号”。
通常采用 `模块:功能:操作` 的三段式命名法，例如：

* 用户新增按钮：`sys:user:add`
* 用户删除按钮：`sys:user:delete`

这些编码会持久化在菜单权限表（`us_sys_menu`）的 `perms` 字段中，作为按钮类型（`menu_type = 'F'`）的资源。

---

## 🔒 三、 后端拦截规约 (API Guard)

后端必须在 **控制器 (Controller) 的接口方法上** 设置物理防线。

### 1. 技术选型

使用 **Sa-Token** 框架提供的 `@SaCheckPermission` 注解。

### 2. 特权旁路实现 (superAdmin)

在 `StpInterfaceImpl` 中实现权限加载逻辑时，必须首行判定：

```java
if("superAdmin".equals(user.getUserCode())){
        return Collections.

singletonList("*"); // 返回通配符，Sa-Token 自动放行所有权限
}
```

* **注意**：`admin` 角色无此特权，必须严格走数据库/缓存匹配。

### 3. 使用示例

```java

@RestController
@RequestMapping("/system/users")
public class SysUserController {
    @DeleteMapping("/{userId}")
    @SaCheckPermission("sys:user:delete")
    public void deleteUser(@PathVariable Long userId) {
        // ...
    }
}
```

---

## 📐 四、 架构设计红线 (Design Rules)

### 1. 权限编码唯一性

* 按钮资源必须在 `us_sys_menu` 表中拥有唯一的字符串标识。
* 常规管理员（如 `admin` 角色）必须在 `us_sys_role_menu` 表中建立与该编码的物理关联。

### 2. 超级管理员 `superAdmin` 绝不走码比对

* 校验判定逻辑的第一行必须判定 `superAdmin`。
* 直接击穿校验管道，消除不必要的散列计算与 I/O 损耗。

### 3. 普通管理员 `admin` 严格走数据漏斗

* `admin` 角色及其他自定义角色无特权旁路，其权限完全取决于分配的权限集。
