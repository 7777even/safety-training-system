# 多渠道登录与动态配置体系架构设计方案 (AI 提示词/上下文友好版)

> 本文由模板契约改写入本仓库知识库，已对齐 `sts-*` 模块与 `com.sts` 包名；已剔除 IoT/设备/驱动相关表述。

## 1. 架构核心原则 (Core Architecture Principles)

* **主体与凭证分离 (Principal-Credential Separation):** 将“用户主体 (Who)”与“认证通道 (How)”解耦。用户表不包含任何特定登录渠道的账密或三方
  Token。一个用户主体可绑定 N 个认证通道，实现多方式共存。
* **策略自治 (Strategy Autonomy):** 利用**策略模式 (Strategy Pattern)**
  ，将不同登录渠道（本地账密、OAuth2、三方扫码、验证码）的认证业务逻辑高度内聚到各自的策略类中。统一控制器只负责分发，不感知具体业务。
* **外挂式会话托管 (Plug-in Session Management):** 业务层仅负责“判定身份是否合法并输出系统唯一标识 `userId`
  ”。会话生命周期管理（Token 签发、续期、踢人下线、单端互斥）完全托管给 **Sa-Token**，对业务代码无侵入。

---

## 2. 领域概念模型 (Domain Conceptual Model)

```
[User (用户主体)] 1 ------- N [AuthIdentity (认证凭证)]

```

### 2.1 用户主体 (User)

* **定义：** 整个系统内部流转的唯一身份标识，与特定登录方式无关。
* **核心属性：** 全局唯一用户ID (`userId`)、账号状态（启用/禁用）、基本档案（昵称、头像）、租户/组织归属。

### 2.2 认证凭证 (AuthIdentity)

* **定义：** 具体的登录通道载体，承载各种登录方式的特有凭证。
* **核心属性：** 凭证ID、关联用户ID (`userId`)、认证渠道类型 (`identityType`)、渠道唯一标识 (`identifier`)
  、凭证密文/令牌占位符 (`credential`)。
* **多渠道映射示例：**
* `identityType == 'local'` $\rightarrow$ `identifier = 用户名`, `credential = 密码哈希`
* `identityType == 'oauth2_github'` $\rightarrow$ `identifier = GitHub OpenID`, `credential = 三方 AccessToken/留空`
* `identityType == 'sms'` $\rightarrow$ `identifier = 手机号`, `credential = 验证码临时快照/留空`

---

## 3. 动态配置与路由控制机制 (Dynamic Configuration)

系统通过配置中心（如 Nacos / Apollo 或本地 YML）维护登录控制参数，包含两个核心层级：

1. **全局启用列表 (`auth.enabled-types`)：** 一个动态可变数组，例如 `[local, oauth2_github, sms]`。
2. **渠道私有参数：** 各渠道所需的私有配置（如 OAuth2 的 `clientId`/`secret`，短信网关密钥等）。

### 联动防御流转：

* **前端感知：** 初始化时调取后端配置接口，获取当前的 `enabled-types`，动态渲染登录页组件（自动隐藏未开启的登录入口）。
* **后端拦截：** 统一登录入口收到请求后，首要步骤是匹配 `enabled-types`。若请求的渠道未在配置列表中，直接阻断并抛出未开启异常，防止绕过前端的恶意调用。

---


整体架构清晰地划分为两个阶段：**业务认证阶段（策略解耦）** $\rightarrow$ **会话接管阶段（Sa-Token 外挂）**。

```
前端请求 (POST /api/v1/auth/login {identityType, authParams})
   |
   v
[AuthController] 
   |-- 1. 校验 identityType 是否在配置的 enabled-types 中
   |-- 2. 通过 LoginStrategyFactory.getStrategy(identityType) 获取具体策略
   |
   v
[LoginStrategy (具体渠道策略类)]
   |-- 3. 解析 authParams (如账号密码、或 OAuth2 code)
   |-- 4. 执行特定的认证逻辑 (如比对密码、或请求三方接口换取 OpenID)
   |-- 5. 如果是新三方用户，触发自动注册/绑定逻辑
   |-- 6. 最终获取并返回系统统一的 [userId]
   |
   v
[AuthController (重新接管)]
   |-- 7. 执行框架落盘：StpUtil.login(userId)
   |      (Sa-Token 在底层自动生成 Token、绑定 Redis、处理同端互斥)
   |-- 8. 返回 TokenValue 给前端

```

---

## 5. AI 开发实现指导 (Prompt Guidance for Code Generation)

当根据此设计规范生成代码时，请遵循以下技术栈与架构约束：

* **技术栈：** Java 17+, Spring Boot 3.x, Sa-Token 1.x。
* **扩展性设计：** * 必须定义标准的 `LoginStrategy` 接口，包含 `getIdentityType()` 和
  `authenticate(Map<String, String> params)` 方法。
* 利用 Spring 的依赖注入特性，通过 `List<LoginStrategy>` 自动组装 `LoginStrategyFactory` 的策略 Map，严禁在工厂中使用手写
  `if-else` 或 `switch-case`。

* **解耦约束：** * 所有的 `LoginStrategy` 实现类中**不得**出现任何 `StpUtil` 的代码，保持 Service 层的纯净和可测试性。
* `StpUtil.login(userId)` 必须统一收拢在控制层 (`AuthController`) 或专门的 `AuthApplicationService` 中，作为流程的最终收尾动作。

* **安全约束：** * 针对动态切换，必须实现配置属性类 `AuthConfigProperties` 并支持 `@RefreshScope` 动态刷新。
