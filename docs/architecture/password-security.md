# 🔒 后台管理密码安全架构设计与红线规约 (前端离散 + 后端慢哈希)

> 本文由模板契约改写入本仓库知识库，已对齐 `sts-*` 模块与 `com.sts` 包名；已剔除 IoT/设备/驱动相关表述。

> **依赖组件**：`BCryptPasswordEncoder` (来自于 Spring Security Crypto, 见 `stack.md`)。  
> **关联业务**：系统管理用户注册、修改密码、登录凭证校验（见 `./detailed-design.md` §1.1）。

本规范定义了 **STS (统一互联)** 安全教育培训系统后台管理系统（`sts-service-admin`）中，用户密码从前端输入到后端持久化落盘的纯技术安全架构与红线规约。

---

## 🏗️ 一、 密码防穿透技术拓扑 (Security Topology)

为了彻底阻断用户的原始明文密码在后端系统（如业务日志、异常堆堆栈、内存 dump）中裸奔或被意外记录，系统采用“前端预散列离散 +
后端多维慢哈希”的闭环协同防御体系：

```text
 [ 前端输入明文密码 ]
         │
         ▼
 1. 前端物理阻断 ──► 立即执行 SHA-256(password)，将明文转化为 64 位不可逆哈希串发给后端
         │
         ▼ (后端接收 64 位哈希串，绝不碰原始明文密码)
 2. 后端慢写控制 ──► 注册/改密时：执行 Bcrypt 慢哈希 ──► 强动态加盐密文 ──► 入库 MySQL (us_sys_auth_identity.credential)
         │
         └───► 登录校验时：提取数据库密文，强制通过 Bcrypt 互斥安全对齐 (.matches)
```

---

## 🛠️ 二、 可执行契约与 Spring 装配 (Spring Config)

后端统一采用 Spring Security Crypto 生态中的 `BCryptPasswordEncoder`。

### 1. 加密器配置类 (SecurityPasswordConfig)

```java
package com.sts.admin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SecurityPasswordConfig {

    /**
     * Bcrypt 核心加密器
     * 设置工作因子（Cost）为 10，计算耗时约 80-100ms，人为拉长单次计算时间，完美阻断针对数据库脱库后的离线暴力破解
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
```

### 2. 业务层认证与持久化契约 (Service Rules)

在用户执行注册、改密、登录验证时，业务代码必须死锁以下安全红线：

1. **输入假定红线**：后端收到的 `password` 字段，**必须假定已经是前端通过 SHA-256 计算后的 64 位十六进制字符串**
   。后端绝不碰触原始明文密码。
2. **持久化加盐红线**：注册/改密时必须调用 `passwordEncoder.encode(frontSha256Pwd)`。Bcrypt
   内部会通过强随机数发生器生成“一户一盐（强动态盐）”并揉入最终密文，严禁人工干预或手动拼盐。最终存储在
   `us_sys_auth_identity.credential` 字段中。
3. **安全对齐红线**：登录验证时**绝对禁止**手动调用 `encode()` 之后用 `==` 或 `equals()` 对比字符串。必须无条件调用安全框架自带的
   **`passwordEncoder.matches(raw, encoded)`** 接口，由算法自动从数据库密文中提取历史盐值进行安全解码对比。

#### 💡 认证服务层标准代码落地规约：

```java
package com.sts.admin.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserSecurityAuthService {

    private final PasswordEncoder passwordEncoder;
    private final AuthIdentityRepository authIdentityRepository; // 凭证持久层数据源

    /**
     * 💾 场景 A：用户注册 或 修改密码
     *
     * @param userId 关联的用户ID
     * @param frontSha256Password 来自前端 SHA-256 离散后的 64 位密码串
     */
    public void registerOrUpdatePassword(Long userId, String frontSha256Password) {
        // 自动动态加盐并哈希，生成包含算法版本、工作因子和强密文的长字符串
        String dbEncryptedPassword = passwordEncoder.encode(frontSha256Password);

        // 密文结果形如：$2a$10$X7vO... 直接存入 us_sys_auth_identity.credential 字段
        authIdentityRepository.updateCredential(userId, "local", dbEncryptedPassword);
    }

    /**
     * 🏎️ 场景 B：用户登录身份验证
     *
     * @param userId 关联的用户ID
     * @param frontSha256Password 来自前端 SHA-256 离散后的 64 位密码串
     */
    public boolean validateUserLogin(Long userId, String frontSha256Password) {
        // 从数据库中捞出该用户之前持久化的加密密文
        String dbStoredPassword = authIdentityRepository.getCredential(userId, "local");
        if (dbStoredPassword == null) {
            return false;
        }

        // 刚性红线：利用 matches 原生接口自动解码密文头、提取动态盐并完成密码学互斥验证
        return passwordEncoder.matches(frontSha256Password, dbStoredPassword);
    }
}
```

---

## 🤖 三、 给 AI 助手的直接启动开发指令 (Cursor / Windsurf Prompt)

当您在项目中需要让 AI 帮你生成这套纯技术协同的密码校验逻辑时，直接把这段话发给它：

> 。当前我正在编写用户认证与系统管理模块，请为我无脑遵循说明书中的【密码防穿透技术拓扑】与【业务层认证与持久化契约】标准，一次性生成以下核心代码：
> 1. 编写配置类 `SecurityPasswordConfig`，向 IOC 容器中注入 Spring Security Crypto 的 `BCryptPasswordEncoder(10)` 作为默认的
     `PasswordEncoder`。
> 2. 编写业务类 `UserSecurityAuthService`（或在 `AuthStrategy` 实现类中）。在处理用户注册/改密时，调用 `.encode()` 动态加盐哈希后写入
     `us_sys_auth_identity.credential` 字段；在处理用户登录时，强制通过 `.matches()` 执行安全对齐，绝对严禁使用任何
     `equals` 或 `==` 字符串对比。
> 3. 后端入参 `password` 一律视为前端已执行 SHA-256 散列后的 64 位哈希字符串，后端和日志完全无法感知到原始明文密码。请输出最高效、无安全漏洞的
     Java 代码。”**

---

## 🎯 四、 Audit 阶段自检清单

- [ ] 配置 `SecurityPasswordConfig` 注入 `BCryptPasswordEncoder(10)`
- [ ] 注册/改密写入 `us_sys_auth_identity.credential` 时，强制使用 `passwordEncoder.encode(...)`
- [ ] 登录校验账号密码时，无条件强制使用 `passwordEncoder.matches(...)` 自解盐，**禁止使用 `equals` 或 `==`**
- [ ] 后端所有密码入参（如 DTO/Form 中）接收 64 位的 SHA-256 哈希散列串，禁止接收原始明文密码
