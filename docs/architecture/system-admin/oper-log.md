# 操作日志功能实现设计

> 本文由模板契约改写入本仓库知识库，已对齐 `sts-*` 模块与 `com.sts` 包名；已剔除 IoT/设备/驱动相关表述。

> 本文件是 `detailed-design.md § 3.1` 的实现级补充。
> 核心目标：**零侵入、注解驱动、异步非阻塞、一行代码接入任意接口**。

---

## 一、现状梳理（已存在，无需改动）

| 资产                        | 位置                                                                           | 说明                                                           |
|---------------------------|------------------------------------------------------------------------------|--------------------------------------------------------------|
| 数据库表 `us_sys_oper_log`    | `backend/sts-service-admin-init/src/main/resources/schema/schema-system.sql` | 含 title / businessType / method / operParam / jsonResult 等字段 |
| 实体 `SysOperLog`           | `admin/entity/SysOperLog.java`                                               | 继承 `BaseAuditEntity`，无 deleted/version                       |
| Mapper `SysOperLogMapper` | `admin/mapper/SysOperLogMapper.java`                                         | 继承 `AppBaseMapper<SysOperLog>`                               |
| `ObjectMapper` Bean       | `ResultEasyTransAdvice` 已注入                                                  | 可直接复用 Jackson                                                |
| 菜单 id=210                 | `menus.json`                                                                 | type=C, path=operlog, comp=system/operlog/index, pid=209     |
| 权限按钮 id=1081/1082/1083    | `menus.json`                                                                 | query / remove / export                                      |

**待实现：** 注解 + AOP切面 + 异步机制 + 查询侧（Service/Controller）+ 前端页面。

---

## 二、整体架构

```
Controller 方法
    │  @OperLog(title="用户管理", businessType=DELETE)
    ▼
OperLogAspect (@Around)
    ├── 执行原方法（proceed）
    ├── 捕获入参、响应体、耗时、异常
    └── ApplicationEventPublisher.publishEvent(OperLogEvent)
                                        │
                                        ▼ 异步（独立线程池）
                              OperLogEventListener
                                        │
                                        ▼
                              SysOperLogMapper.insert(log)
```

**关键设计原则：**

1. **AOP 只负责采集**，不直接调用任何 Service/Mapper，通过 Spring 事件解耦。
2. **事件异步消费**，使用独立线程池（核心 2 线程，队列 1000），满载时 `DiscardPolicy` 丢弃日志而不阻塞主线程。

---

## 三、文件清单

```
sts-service-admin/src/main/java/com/sts/admin/
├── aop/
│   ├── annotation/
│   │   ├── OperLog.java              ← 新增：注解定义
│   │   └── BusinessType.java         ← 新增：业务类型枚举
│   ├── aspect/
│   │   └── OperLogAspect.java        ← 新增：AOP 切面
│   └── event/
│       ├── OperLogEvent.java         ← 新增：Spring 事件载体
│       └── OperLogEventListener.java ← 新增：异步消费者
├── config/
│   └── AsyncConfig.java              ← 新增：线程池配置（或追加到现有 config）
└── system/
    ├── dto/OperLogQuery.java          ← 新增
    ├── vo/OperLogVO.java              ← 新增
    ├── service/SysOperLogService.java          ← 新增
    ├── service/SysOperLogServiceImpl.java      ← 新增
    └── controller/SysOperLogController.java    ← 新增
```

---

## 四、注解层

### 4.1 BusinessType.java

```java
package com.sts.admin.aop.annotation;

public enum BusinessType {
    OTHER(0),        // 其它
    INSERT(1),       // 新增
    UPDATE(2),       // 修改
    DELETE(3),       // 删除
    GRANT(4),        // 授权
    FORCE_KICKOUT(5),// 强退
    CLEAN(6),        // 清空
    EXPORT(7);       // 导出

    private final int code;

    BusinessType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
```

### 4.2 OperLog.java

```java
package com.sts.admin.aop.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperLog {
    /** 模块标题，如 "用户管理" */
    String title() default "";

    /** 业务类型 */
    BusinessType businessType() default BusinessType.OTHER;

    /** 是否保存请求参数（默认 true；上传文件等大请求可设 false） */
    boolean saveRequestData() default true;

    /** 是否保存响应结果（默认 true；大批量导出可设 false） */
    boolean saveResponseData() default true;
}
```

**接入示例（一行代码）：**

```java

@OperLog(title = "用户管理", businessType = BusinessType.DELETE)
@DeleteMapping("/{userId}")
@SaCheckPermission("sys:user:delete")
public void deleteUser(@PathVariable Long userId) { ...}
```

---

## 五、AOP 切面

### 5.1 OperLogEvent.java

```java
package com.sts.admin.aop.event;

import com.sts.admin.entity.SysOperLog;
import org.springframework.context.ApplicationEvent;

public class OperLogEvent extends ApplicationEvent {
    private final SysOperLog log;

    public OperLogEvent(Object source, SysOperLog log) {
        super(source);
        this.log = log;
    }

    public SysOperLog getLog() {
        return log;
    }
}
```

### 5.2 OperLogAspect.java

```java
package com.sts.admin.aop.aspect;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.admin.aop.annotation.OperLog;
import com.sts.admin.aop.event.OperLogEvent;
import com.sts.admin.entity.SysOperLog;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperLogAspect {

    private static final int MAX_PARAM_LENGTH = 65535;
    private static final int MAX_RESULT_LENGTH = 65535;

    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Around("@annotation(operLog)")
    public Object around(ProceedingJoinPoint pjp, OperLog operLog) throws Throwable {
        long startTime = System.currentTimeMillis();
        Object result = null;
        Exception ex = null;
        try {
            result = pjp.proceed();
            return result;
        } catch (Exception e) {
            ex = e;
            throw e;
        } finally {
            try {
                publishLog(pjp, operLog, result, ex, System.currentTimeMillis() - startTime);
            } catch (Exception ignored) {
                // 日志采集不能影响主流程
            }
        }
    }

    private void publishLog(ProceedingJoinPoint pjp, OperLog operLog,
                            Object result, Exception ex, long elapsed) {
        SysOperLog log = new SysOperLog();
        MethodSignature sig = (MethodSignature) pjp.getSignature();

        log.setTitle(operLog.title());
        log.setBusinessType(operLog.businessType().getCode());
        log.setMethod(pjp.getTarget().getClass().getName() + "." + sig.getName());
        log.setStatus(ex == null ? 1 : 0);

        if (ex != null) {
            String msg = ex.getMessage();
            log.setErrorMsg(msg != null && msg.length() > 2000 ? msg.substring(0, 2000) : msg);
        }

        // 操作人
        try {
            if (StpUtil.isLogin()) {
                // operatorCode 可从 Sa-Token Session 或 userService 取得
                Object loginId = StpUtil.getLoginId();
                log.setOperatorName(loginId != null ? loginId.toString() : "");
            }
        } catch (Exception ignored) {
        }

        // HTTP 上下文
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            log.setOperIp(request.getRemoteAddr());
            log.setOperUrl(request.getRequestURI());
            log.setRequestMethod(request.getMethod());
        }

        // 请求参数
        if (operLog.saveRequestData()) {
            log.setOperParam(buildParamJson(pjp.getArgs()));
        }

        // 响应体
        if (operLog.saveResponseData() && result != null) {
            try {
                String json = objectMapper.writeValueAsString(result);
                log.setJsonResult(truncate(json, MAX_RESULT_LENGTH));
            } catch (Exception ignored) {
            }
        }

        eventPublisher.publishEvent(new OperLogEvent(this, log));
    }

    private String buildParamJson(Object[] args) {
        List<Object> filtered = new ArrayList<>();
        for (Object arg : args) {
            if (arg instanceof HttpServletRequest
                    || arg instanceof HttpServletResponse
                    || arg instanceof MultipartFile) {
                continue; // 跳过不可序列化对象
            }
            filtered.add(arg);
        }
        try {
            String json = objectMapper.writeValueAsString(
                    filtered.size() == 1 ? filtered.get(0) : filtered);
            json = maskSensitiveFields(json);
            return truncate(json, MAX_PARAM_LENGTH);
        } catch (Exception e) {
            return "";
        }
    }

    /** 脱敏：将 JSON 中 password 系列字段值替换为 ****** */
    private String maskSensitiveFields(String json) {
        return json.replaceAll(
                "\"(password|newPassword|oldPassword|confirmPassword)\"\\s*:\\s*\"[^\"]*\"",
                "\"$1\":\"******\""
        );
    }

    private String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) : s;
    }
}
```

---

## 六、异步消费

### 6.1 AsyncConfig.java（追加 operLogExecutor Bean）

```java
package com.sts.admin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("operLogExecutor")
    public Executor operLogExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("oper-log-");
        // 队列满时直接丢弃，不阻塞主线程
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.initialize();
        return executor;
    }
}
```

### 6.2 OperLogEventListener.java

```java
package com.sts.admin.aop.event;

import com.sts.admin.mapper.SysOperLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OperLogEventListener {

    private final SysOperLogMapper operLogMapper;

    @EventListener
    @Async("operLogExecutor")
    public void onOperLogEvent(OperLogEvent event) {
        try {
            operLogMapper.insert(event.getLog());
        } catch (Exception e) {
            log.warn("操作日志写入失败: {}", e.getMessage());
        }
    }
}
```

**扩展新的消费者只需新增一个 `@EventListener`，现有代码零改动：**

```java
@EventListener
@Async("operLogExecutor")
public void onOperLogEventKafka(OperLogEvent event) {
    kafkaTemplate.send("iot.oper.log", JSON.toJSONString(event.getLog()));
}
```

---

## 七、查询侧（后端）

### 7.1 OperLogQuery.java

```java

@Data
public class OperLogQuery {
    private String title;
    private String operatorCode;
    private Integer businessType;
    private Integer status;          // 0-异常 1-正常
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime beginTime;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endTime;
}
```

### 7.2 OperLogVO.java

```java

@Data
public class OperLogVO {
    private Long operLogId;
    private String title;
    private Integer businessType;
    private String method;
    private String requestMethod;
    private String operatorCode;
    private String operIp;
    private String operUrl;
    private String operParam;
    private String jsonResult;
    private Integer status;
    private String errorMsg;
    private LocalDateTime createTime;
}
```

> 无需 EasyTrans 翻译。`operatorCode` 在写入时已是字符串，不存 ID。

### 7.3 SysOperLogController.java

```java

@RestController
@RequestMapping("/system/oper-logs")
@RequiredArgsConstructor
public class SysOperLogController {

    @GetMapping
    @SaCheckPermission("sys:operlog:list")
    public PageResult<OperLogVO> pageOperLogs(PageRequest<OperLogQuery> request)

    @GetMapping("/{operLogId}")
    @SaCheckPermission("sys:operlog:query")
    public OperLogVO detail(@PathVariable Long operLogId)

    @DeleteMapping
    @SaCheckPermission("sys:operlog:remove")
    public void batchDelete(@RequestBody List<Long> ids)

    @DeleteMapping("/clean")
    @SaCheckPermission("sys:operlog:remove")
    public void cleanAll()
}
```

ServiceImpl 模式与 `SysLoginLogServiceImpl` 完全一致，不重复描述。

---

## 八、前端实现

### 8.1 system.ts 追加内容（要点）

```ts
export interface OperLogVO {
    operLogId: number;
    title: string;
    businessType: number
    method: string;
    requestMethod: string;
    operatorCode: string
    operIp: string;
    operUrl: string;
    operParam: string
    jsonResult: string;
    status: 0 | 1;
    errorMsg: string
    createTime: string
}

export interface OperLogQuery {
    title?: string;
    operatorCode?: string
    businessType?: number;
    status?: number
    beginTime?: string;
    endTime?: string
}

export const listOperLogs = (params: PageRequest<OperLogQuery>) => request(...)
export const detailOperLog = (operLogId: number) => request(...)
export const batchDeleteLogs = (ids: number[]) => request(...)
export const cleanOperLogs = () => request(...)
```

### 8.2 页面 views/system/operlog/index.vue

**搜索栏**：模块标题、操作人员、操作类型（下拉）、操作状态（下拉）、操作时间区间

**表格列**：系统模块 / 操作类型（字典翻译）/ 请求方式 / 操作人员 / 操作IP / 操作状态（Tag）/ 操作时间 / 操作（详情弹窗）

**详情弹窗**：展示 operParam（请求参数）和 jsonResult（响应体），用 `<pre>` 格式化展示 JSON。

**工具栏**：批量删除（`sys:operlog:remove`）、清空（`sys:operlog:remove`）。

### 8.3 路由

```ts
{
    path: 'system/log/operlog',
        name
:
    'SysOperLog',
        component
:
    () => import('@/views/system/operlog/index.vue'),
        meta
:
    {
        title: '操作日志', keepAlive
    :
        true
    }
,
}
```

---

## 九、operatorCode 获取策略

`SysOperLog.operatorCode` 字段在写入时直接存用户名（字符串），不存 userId，查询时无需 JOIN。

AOP 切面获取方式：

```java
// 方案A（推荐）：从 Sa-Token TokenSession 取已缓存的 userCode
SaSession session = StpUtil.getTokenSession();
String userCode = session != null ? (String) session.get("userCode") : "";
log.setOperatorName(userCode);

// 方案B：直接用 loginId（若 loginId 就是 userId 则需再查 DB，避免用此方案）
```

与 `SysUserOnlineService` 中写入 Session 时的 Key 保持一致：`SESSION_KEY_USERCODE`。

---

## 十、扩展方式总结

| 场景            | 改动范围                                 |
|---------------|--------------------------------------|
| 给新接口开启日志      | 加一行 `@OperLog(...)` 注解               |
| 新增业务类型（如"导入"） | 在 `BusinessType` 枚举追加一项              |
| 关闭某接口的参数保存    | `@OperLog(saveRequestData = false)`  |
| 关闭某接口的响应保存    | `@OperLog(saveResponseData = false)` |
| 日志截断长度调整      | 修改 `MAX_PARAM_LENGTH` 常量（单处）         |

---

## 十一、菜单初始化数据（已存在）

| id   | type | name | perms                | path                                         |
|------|------|------|----------------------|----------------------------------------------|
| 209  | M    | 日志管理 | —                    | `/system/log`                                |
| 210  | C    | 操作日志 | `sys:operlog:list`   | `operlog`（相对，经 convertToMenuItem 修复后自动拼绝对路径） |
| 1081 | F    | 日志查询 | `sys:operlog:query`  | —                                            |
| 1082 | F    | 日志删除 | `sys:operlog:remove` | —                                            |
| 1083 | F    | 日志导出 | `sys:operlog:export` | —                                            |

> 导出功能（id=1083）本期设计为预留，不在本次实现范围内。

---

## 十二、注意事项

1. **`@EnableAsync` 位置**：放在 `AsyncConfig` 上，确保 Spring 扫描到。
2. **AOP 依赖**：Spring Boot Starter AOP（`spring-boot-starter-aop`）已通过 `spring-boot-starter-web` 传递引入，无需单独声明依赖。
3. **`@Around` vs `@AfterReturning + @AfterThrowing`**：用 `@Around` 统一处理成功/失败，代码更简洁；`finally` 确保异常时也能记录。
4. **`SysOperLog` 物理删除**：同 `SysLoginLog`，无 `deleted` 字段，`BaseServiceImpl.removeById` 自动走物理删除。
5. **`createTime` 不需要手动 set**：`MybatisMetaObjectHandler.insertFill` 会自动填充（entity 字段为 null 时），无需在监听器里手动赋值。
