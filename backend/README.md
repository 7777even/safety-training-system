# 后端工程（Maven 多模块）

JDK 21、Spring Boot 3.4.13、Maven。模块来自 `source-backend` 的 Gradle 工程移植：

| 模块                       | 说明                                                   |
|--------------------------|------------------------------------------------------|
| `sts-common`             | 公共库                                                  |
| `sts-service-admin`      | 系统管理服务                                               |
| `sts-service-admin-init` | 一次性数据库初始化 CLI（模块化完整 SQL 建库、登记 Flyway baseline 后写入种子） |

## 编译

```bash
cd backend
mvn -s .mvn/settings.xml -DskipTests compile
```

## 单元测试（不含 Docker 集成测）

```bash
mvn -s .mvn/settings.xml test
```

## 集成测试（需 Docker）

```bash
mvn -s .mvn/settings.xml -pl sts-service-admin -Pintegration-test test
```

## 运行

```bash
mvn -s .mvn/settings.xml -pl sts-service-admin -am spring-boot:run
mvn -s .mvn/settings.xml -pl sts-service-admin-init -am spring-boot:run
```
