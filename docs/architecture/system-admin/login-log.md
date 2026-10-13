# 登录日志功能实现设计

> 本文由模板契约改写入本仓库知识库，已对齐 `sts-*` 模块与 `com.sts` 包名；已剔除 IoT/设备/驱动相关表述。

> 本文件是 `detailed-design.md § 3.2` 的实现级补充，覆盖后端代码结构与前端页面规约。

---

## 一、现状梳理（已存在，无需改动）

| 资产                         | 位置                                                                           | 说明                                                         |
|----------------------------|------------------------------------------------------------------------------|------------------------------------------------------------|
| 数据库表 `us_sys_login_log`    | `backend/sts-service-admin-init/src/main/resources/schema/schema-system.sql` | 含 userCode / ipaddr / status / loginTime 等字段               |
| 实体 `SysLoginLog`           | `admin/entity/SysLoginLog.java`                                              | 继承 `BaseAuditEntity`，无 deleted / version 字段                |
| Mapper `SysLoginLogMapper` | `admin/mapper/SysLoginLogMapper.java`                                        | 继承 `AppBaseMapper<SysLoginLog>`                            |
| 写入逻辑 `saveLoginLog()`      | `auth/service/AuthApplicationServiceImpl.java`                               | 登录成功、失败均写入，catch 后静默忽略                                     |
| 菜单初始化 id=211               | `sts-service-admin-init/.../menus.json`                                      | type=C, path=loginlog, comp=system/loginlog/index, pid=209 |
| 权限按钮 id=1091               | `menus.json`                                                                 | `sys:loginlog:remove`（删除）                                  |
| 权限按钮 id=1092               | `menus.json`                                                                 | `sys:loginlog:clean`（清空）                                   |

**本次只需实现查询/删除/清空侧，写入侧已完备。**

---

## 二、后端实现

### 文件清单

```
sts-service-admin/src/main/java/com/sts/admin/system/
├── dto/
│   └── LoginLogQuery.java          ← 新增
├── vo/
│   └── LoginLogVO.java             ← 新增
├── converter/
│   └── SysLoginLogConverter.java   ← 新增
├── service/
│   ├── SysLoginLogService.java     ← 新增
│   └── SysLoginLogServiceImpl.java ← 新增
└── controller/
    └── SysLoginLogController.java  ← 新增
```

---

### 2.1 LoginLogQuery.java

```java
package com.sts.admin.system.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
public class LoginLogQuery {
    private String userCode;
    private String ipaddr;
    private Integer status;      // 0-失败  1-成功
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime beginTime;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endTime;
}
```

---

### 2.2 LoginLogVO.java

```java
package com.sts.admin.system.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LoginLogVO {
    private Long loginLogId;
    private String userCode;
    private String ipaddr;
    private String loginLocation;
    private String browser;
    private String os;
    private Integer status;        // 0-失败  1-成功
    private String msg;
    private LocalDateTime loginTime;
}
```

> 无外键依赖，不需要 `@Translatable` / EasyTrans 翻译字段。

---

### 2.3 SysLoginLogConverter.java

```java
package com.sts.admin.system.converter;

import com.sts.admin.entity.SysLoginLog;
import com.sts.admin.system.vo.LoginLogVO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SysLoginLogConverter {
    LoginLogVO toVO(SysLoginLog entity);
}
```

---

### 2.4 SysLoginLogService.java

```java
package com.sts.admin.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sts.admin.entity.SysLoginLog;
import com.sts.admin.system.dto.LoginLogQuery;
import com.sts.admin.system.vo.LoginLogVO;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;

import java.util.List;

public interface SysLoginLogService extends IService<SysLoginLog> {
    PageResult<LoginLogVO> pageLoginLogs(PageRequest<LoginLogQuery> request);

    void deleteById(Long loginLogId);

    void batchDelete(List<Long> ids);

    void cleanAll();
}
```

---

### 2.5 SysLoginLogServiceImpl.java

```java
package com.sts.admin.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sts.admin.entity.SysLoginLog;
import com.sts.admin.mapper.SysLoginLogMapper;
import com.sts.admin.system.converter.SysLoginLogConverter;
import com.sts.admin.system.dto.LoginLogQuery;
import com.sts.admin.system.vo.LoginLogVO;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.common.service.BaseServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SysLoginLogServiceImpl
        extends BaseServiceImpl<SysLoginLogMapper, SysLoginLog>
        implements SysLoginLogService {

    private final SysLoginLogConverter converter;

    @Override
    public PageResult<LoginLogVO> pageLoginLogs(PageRequest<LoginLogQuery> request) {
        LoginLogQuery q = request.getQuery();
        LambdaQueryWrapper<SysLoginLog> wrapper = Wrappers.lambdaQuery();
        if (q != null) {
            wrapper.like(StringUtils.hasText(q.getUserCode()), SysLoginLog::getUserCode, q.getUserCode())
                    .like(StringUtils.hasText(q.getIpaddr()), SysLoginLog::getIpaddr, q.getIpaddr())
                    .eq(q.getStatus() != null, SysLoginLog::getStatus, q.getStatus())
                    .ge(q.getBeginTime() != null, SysLoginLog::getLoginTime, q.getBeginTime())
                    .le(q.getEndTime() != null, SysLoginLog::getLoginTime, q.getEndTime());
        }
        wrapper.orderByDesc(SysLoginLog::getLoginTime);

        Page<SysLoginLog> page = this.page(new Page<>(request.getPageNum(), request.getPageSize()), wrapper);
        return PageResult.of(page, converter::toVO);
    }

    @Override
    public void deleteById(Long loginLogId) {
        // SysLoginLog 继承 BaseAuditEntity（非 BaseEntity），BaseServiceImpl.removeById
        // 检测到实体类不含 deleted 字段，自动走 MyBatis-Plus 原生物理删除
        this.removeById(loginLogId);
    }

    @Override
    public void batchDelete(List<Long> ids) {
        this.removeByIds(ids);
    }

    @Override
    public void cleanAll() {
        this.remove(Wrappers.emptyWrapper());
    }
}
```

**物理删除说明**：`SysLoginLog` 继承 `BaseAuditEntity`，不含 `deleted` 字段，`BaseServiceImpl.removeById`
内部通过 `BaseEntity.class.isAssignableFrom(getEntityClass())` 判断为 `false`，直接走
`super.removeById()`（MyBatis-Plus 物理删除），行为符合日志表需求。

---

### 2.6 SysLoginLogController.java

```java
package com.sts.admin.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.sts.admin.system.dto.LoginLogQuery;
import com.sts.admin.system.service.SysLoginLogService;
import com.sts.admin.system.vo.LoginLogVO;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/system/login-logs")
@RequiredArgsConstructor
public class SysLoginLogController {

    private final SysLoginLogService loginLogService;

    @GetMapping
    @SaCheckPermission("sys:loginlog:list")
    public PageResult<LoginLogVO> pageLoginLogs(PageRequest<LoginLogQuery> request) {
        return loginLogService.pageLoginLogs(request);
    }

    @DeleteMapping("/{loginLogId}")
    @SaCheckPermission("sys:loginlog:remove")
    public void deleteById(@PathVariable Long loginLogId) {
        loginLogService.deleteById(loginLogId);
    }

    @DeleteMapping
    @SaCheckPermission("sys:loginlog:remove")
    public void batchDelete(@RequestBody List<Long> ids) {
        loginLogService.batchDelete(ids);
    }

    @DeleteMapping("/clean")
    @SaCheckPermission("sys:loginlog:clean")
    public void cleanAll() {
        loginLogService.cleanAll();
    }
}
```

---

## 三、前端实现

### 文件清单

```
app/frontend/src/
├── api/
│   └── system.ts          ← 追加接口类型和函数
├── views/system/loginlog/
│   └── index.vue          ← 新增页面
└── router/
    └── routes.ts          ← 追加路由
```

---

### 3.1 system.ts 追加内容

```ts
// ---------- 登录日志 ----------
export interface LoginLogVO {
    loginLogId: number
    userCode: string
    ipaddr: string
    loginLocation: string
    browser: string
    os: string
    status: 0 | 1        // 0-失败  1-成功
    msg: string
    loginTime: string
}

export interface LoginLogQuery {
    userCode?: string
    ipaddr?: string
    status?: number
    beginTime?: string
    endTime?: string
}

export const listLoginLogs = (params: PageParams<LoginLogQuery>) =>
    request.get<PageResult<LoginLogVO>>('/system/login-logs', {params})

export const deleteLoginLog = (loginLogId: number) =>
    request.delete<void>(`/system/login-logs/${loginLogId}`)

export const batchDeleteLogs = (ids: number[]) =>
    request.delete<void>('/system/login-logs', {data: ids})

export const cleanLoginLogs = () =>
    request.delete<void>('/system/login-logs/clean')
```

---

### 3.2 views/system/loginlog/index.vue 页面规约

**三段式布局**（Golden Three-Section，与其他系统管理页保持一致）：

#### 搜索栏字段

| 字段   | 控件                         | 绑定                                    |
|------|----------------------------|---------------------------------------|
| 登录账号 | `el-input` 模糊              | `params.query.userCode`               |
| 登录IP | `el-input` 模糊              | `params.query.ipaddr`                 |
| 登录状态 | `el-select`（全部/成功/失败）      | `params.query.status`                 |
| 登录时间 | `el-date-picker` daterange | `dateRange` → `beginTime` / `endTime` |

#### 工具栏按钮

| 按钮   | 权限                    | 行为                                               |
|------|-----------------------|--------------------------------------------------|
| 批量删除 | `sys:loginlog:remove` | 需选中行，`disabled` 无选中时                             |
| 清空   | `sys:loginlog:clean`  | `ElMessageBox.confirm` 二次确认后调 `cleanLoginLogs()` |

#### 表格列

| 列     | 字段              | 备注                                             |
|-------|-----------------|------------------------------------------------|
| 选择框   | —               | `type="selection"`                             |
| 登录账号  | `userCode`      | —                                              |
| IP 地址 | `ipaddr`        | —                                              |
| 登录地点  | `loginLocation` | —                                              |
| 浏览器   | `browser`       | —                                              |
| 操作系统  | `os`            | —                                              |
| 登录状态  | `status`        | `el-tag` type：`success`（成功）/ `danger`（失败）      |
| 提示消息  | `msg`           | —                                              |
| 登录时间  | `loginTime`     | —                                              |
| 操作    | —               | `fixed="right"`，仅一个删除按钮（`sys:loginlog:remove`） |

---

### 3.3 routes.ts 追加路由

```ts
{
    path: 'system/log/loginlog',
        // 路径来源：菜单 id=209（日志管理）path=/system/log
        //         + 菜单 id=211（登录日志）path=loginlog
        //         → 完整 URL /system/log/loginlog
        name
:
    'SysLoginLog',
        component
:
    () => import('@/views/system/loginlog/index.vue'),
        meta
:
    {
        title: '登录日志', keepAlive
    :
        true
    }
,
}
```

---

## 四、菜单初始化数据（已存在，确认无需新增）

| id   | type | name | perms                 | path           |
|------|------|------|-----------------------|----------------|
| 209  | M    | 日志管理 | —                     | `/system/log`  |
| 211  | C    | 登录日志 | `sys:loginlog:list`   | `loginlog`（相对） |
| 1091 | F    | 日志删除 | `sys:loginlog:remove` | —              |
| 1092 | F    | 日志清空 | `sys:loginlog:clean`  | —              |

---

## 五、注意事项

1. **物理删除**：`SysLoginLog` 不含 `deleted`/`version`，`BaseServiceImpl` 自动走物理删除，无需额外配置。
2. **cleanAll 实现**：`this.remove(Wrappers.emptyWrapper())` 生成 `DELETE FROM us_sys_login_log WHERE 1=1`，清空全表。
3. **路由路径层级**：登录日志在 M 类型菜单「日志管理」下，路由 path 需写 `system/log/loginlog`（不带前导 `/`）。
4. **browser 字段**：写入时已截取前 64 字符（User-Agent），前端直接展示，不需要 tooltip。
5. **写入侧不改动**：`AuthApplicationServiceImpl.saveLoginLog()` 已覆盖所有认证路径，本次改动范围仅限查询/删除。
