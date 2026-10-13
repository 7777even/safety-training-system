# 后端技术栈

> 版本真理源：`backend/pom.xml`。变更依赖版本须同步本文。

## 版本锁定

| 组件 | 版本 | 说明 |
|------|------|------|
| JDK | 21 | 语言基线 |
| Maven | 3.9+ | 构建工具 |
| Spring Boot | 3.4.13 | 父 POM |
| Spring Cloud | 2024.0.3 | BOM（含 LoadBalancer） |
| Spring Cloud Alibaba | 2023.0.3.4 | Nacos Discovery（默认关闭注册） |
| MySQL Connector/J | 9.6.0 | 系统管理库 |
| Log4j2 | 2.25.4 | 统一日志（排除 Logback） |
| Lombok | 1.18.42 | 样板代码 |
| MyBatis-Plus | 3.5.15 | ORM |
| Sa-Token | 1.45.0 | 鉴权与会话 |
| Redisson | 4.3.1 | Redis 客户端 / 缓存失效 Pub/Sub |
| Caffeine | 3.2.4 | L1 本地缓存 |
| EasyTrans | 2.0.0 | ID→Name 翻译 |
| MapStruct | 1.6.3 | 编译期对象映射 |
| JUnit 5 | Boot BOM | 单元测试 |
| Testcontainers | Boot BOM | 集成测试（可选 profile） |

## Maven 坐标

```xml
<groupId>com.sts</groupId>
<artifactId>backend</artifactId>
<version>1.0.0-SNAPSHOT</version>
```

子模块：`sts-common`、`sts-service-admin`、`sts-service-admin-init`。

## 编码铁律

1. 校验/持久化注解使用 `jakarta.*`，禁止 `javax.*`。
2. 第三方依赖版本由父 POM 统一管理，子模块禁止硬编码版本号。
3. 模块边界见 [backend-module-boundary.md](./backend-module-boundary.md)；横切能力见本目录其余架构文档。

## 构建与运行

```bash
cd backend
mvn -s .mvn/settings.xml -DskipTests compile
mvn -s .mvn/settings.xml test
mvn -s .mvn/settings.xml -pl sts-service-admin -am spring-boot:run
mvn -s .mvn/settings.xml -pl sts-service-admin-init -am spring-boot:run
```
