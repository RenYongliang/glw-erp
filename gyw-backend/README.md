# gyw-backend

GYW 进销存后端基础工程，包名 `com.gyw.backend`。默认使用 mock 用户和内存会话，前端 `gyw-web` 暂未改成调用本项目。

## 技术版本

核对日期：2026-09-23。使用官方文档当时列出的最新稳定版 Spring Boot，不使用 milestone/snapshot。

| 组件 | 版本 / 用途 |
| --- | --- |
| Java | 21 LTS |
| Spring Boot | 4.1.1 |
| Spring Security | Boot BOM 管理，JWT 无状态认证、方法级授权 |
| MyBatis-Plus | 3.5.17，专用 `mybatis-plus-spring-boot4-starter` |
| JJWT | 0.13.0，API + impl + Gson |
| MySQL Connector/J、Spring Data Redis、Lettuce | Boot BOM 管理兼容版本 |
| Maven | 3.6.3+，建议 3.9.x |

版本依据：[Spring Boot](https://docs.spring.io/spring-boot/)、[MyBatis-Plus 安装](https://baomidou.com/getting-started/install/)、[JJWT Releases](https://github.com/jwtk/jjwt/releases)。Boot 4 的 JSON 响应使用 Jackson 3；JJWT 单独使用 Gson 序列化 JWT，不重复引入 MyBatis starter。

## 项目结构

```text
src/main/java/com/gyw/backend/
  GywBackendApplication.java
  common/          统一结果、错误码、业务异常、全局异常处理、分页结果、traceId
  config/          JWT/CORS 配置、SecurityFilterChain、MyBatis-Plus、密码编码器
  auth/            JWT 签发/校验、认证过滤器、内存/Redis 会话实现
  user/
    controller/    UserController
    service/       UserService
    dto/           登录请求/响应
    model/         内部账号、对外用户信息
    entity/        SysUser
    mapper/        UserMapper extends BaseMapper
    repository/    mock 用户 / MyBatis 用户查询
src/main/resources/
  application.yml
  application-mock.yml
  application-prod.yml
  db/schema.sql
src/test/java/      登录链路、Token 签名/过期/篡改、会话撤销测试
requests/users.http 可直接在 IDE 中执行的接口示例
```

## 本地 mock 模式

安装 JDK 21 并设置 `JAVA_HOME` 后，用 IDEA 打开 `pom.xml` 即可导入。已附官方 Maven Wrapper，首次执行会下载 Maven 3.9.11，不需要单独配置全局 Maven。默认端口 8080，默认 profile 为 `mock`。

```powershell
cd E:\PycharmProject\gyw-erp\gyw-backend
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run
```

mock 不初始化数据库连接，用户在内存中，密码使用 BCrypt 校验；Redis starter 已配置，但 mock 登录链路不访问 Redis。账户与前端演示账号一致：

| 账号 | 密码 | 角色 |
| --- | --- | --- |
| admin | Admin123! | ADMIN |
| demo | Demo123! | USER |

两个账号均可登录；角色权限扩展可在接口上添加 `@PreAuthorize("hasRole('ADMIN')")`。

## 接口约定

| 方法 | 路径 | 鉴权 | 功能 |
| --- | --- | --- | --- |
| POST | `/api/users/login` | 无 | 验证账号密码，签发 Token |
| POST | `/api/users/logout` | Bearer Token | 撤销当前 Token 的会话 |
| GET | `/api/users/me` | Bearer Token | 返回当前用户 |

登录请求：

```json
{"username":"admin","password":"Admin123!","remember":false}
```

成功响应结构（时间和 Token 由运行时生成）：

```json
{
  "code": 0,
  "message": "成功",
  "data": {
    "accessToken": "eyJ...",
    "tokenType": "Bearer",
    "expiresAt": "2026-09-23T08:00:00Z",
    "user": {"id":"1","username":"admin","name":"管理员","role":"ADMIN","department":"运营管理部"}
  },
  "timestamp": "2026-09-23T00:00:00Z",
  "traceId": "由服务端生成"
}
```

后续请求头：`Authorization: Bearer <accessToken>`。错误沿用上述外层结构，`data` 为 null；HTTP 状态码与错误类别一致：400 参数/JSON 错误、401 未登录或凭据错误、403 无权限、404 无资源、405 方法错误、409 数据冲突、415 内容类型错误、500 未知异常、503 数据库/Redis 不可用。业务错误码见 `common/ErrorCode.java`。响应头 `X-Trace-Id` 可用于定位服务端日志；日志不记录密码/完整 Token。

JWT 为 HS256，校验签名、签发方、过期时间与用户身份；每次请求还检查会话白名单和用户启用状态。普通登录 8 小时，remember 登录 7 天，不提供自动续期/refresh Token。

登出只撤销当前会话，其他设备上的登录保留；重复用已撤销 Token 登出返回 401。mock 重启清空会话，因此之前签发的 Token 也失效。真实模式使用 Redis TTL 保存会话，Redis 不可用时拒绝认证，不降级放行。

## 切换真实 MySQL / Redis

1. 创建 MySQL 8+ 数据库，在该库手工执行 `src/main/resources/db/schema.sql`。应用不自动建表，不覆盖数据。
2. 填写 `application.yml` 中的数据库、Redis 地址，或优先通过下列环境变量配置。
3. 启用 `prod`（不要与 `mock` 同时启用）。此时自动切换到 `MybatisAccountRepository` 和 `RedisSessionStore`。
4. 在 `sys_user` 中创建实际用户，`password_hash` 必须是 `PasswordEncoder.encode(rawPassword)` 生成的 BCrypt 值；角色为 `ADMIN` 或 `USER`，enabled=1，deleted=0。真实模式没有默认账号。

```powershell
$env:SPRING_PROFILES_ACTIVE = 'prod'
$env:DB_URL = 'jdbc:mysql://your-host:3306/gyw?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai'
$env:DB_USERNAME = 'your-user'
$env:DB_PASSWORD = 'your-password'
$env:REDIS_HOST = 'your-redis-host'
$env:REDIS_PORT = '6379'
$env:REDIS_PASSWORD = 'your-redis-password'
$env:JWT_SECRET = 'replace-with-a-random-secret-at-least-32-bytes'
$env:CORS_ALLOWED_ORIGINS = 'https://your-frontend.example'
.\mvnw.cmd spring-boot:run
```

JWT_SECRET 是原始 UTF-8 字符串，不是 Base64 解码配置。可通过 `openssl rand -base64 48` 生成随机文本，并将输出作为环境变量值。prod 不提供默认密钥，避免意外使用演示密钥。不要将真实密码写入版本库。

MyBatis-Plus 已启用驼峰映射、逻辑删除、分页（每页上限 200）、全表更新/删除拦截。实体不直接作为接口返回值，避免暴露密码哈希。

## 前端接入

前端目前仍是纯前端 mock，后续在登录页调用 login 接口，取 `data.accessToken`，统一给请求添加 Authorization 头；401 时清理本地状态并跳转登录页。退出时先调用 logout 再清理本地状态。若仅清理浏览器缓存而未调用 logout，服务端会话会一直保留到过期。

前端开发地址 localhost:5173 和 127.0.0.1:5173 已加入 CORS 白名单。使用 Header Token，不使用 Cookie 登录，因此关闭 CSRF 与 Session。新增接口默认必须登录，匿名接口需要在 `SecurityConfig` 明确放行。

本工程是开发基础配置：尚未实现注册、验证码、限流、密码找回、刷新 Token 或完整业务模块。

## 交付验证

2026-09-23 使用 JDK 21.0.9、Maven 3.9.11 执行 `mvn verify`：编译、14 项测试及可执行 JAR 打包通过。测试覆盖 mock 应用上下文、登录/用户信息/登出、旧 Token 失效、独立会话、remember 默认值与有效期、JWT 过期/错误签名/篡改、参数校验、404/405 统一结果与 CORS。

真实 MySQL/Redis 的连接与读写尚未验证，需替换配置后联调。交付目录不包含临时构建工具、依赖缓存或 target 构建产物；可通过 Wrapper 重新生成。
