# glw-backend

光联微进销存单体后端基础工程。Java 21、Spring Boot 4.1.1、MyBatis-Plus 3.5.17、JJWT 0.13.0。统一响应只有 `code/message/data/timestamp`，使用普通应用日志。

## 本地启动

配置 JDK 21 的 JAVA_HOME 后执行：

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run
```

默认端口8080、profile为mock，不依赖MySQL/Redis。测试账号 `admin / Admin123!`、`demo / Demo123!`。生产使用 `SPRING_PROFILES_ACTIVE=prod`，并替换 application.yml 中 DB、REDIS、JWT_SECRET 环境变量。数据库表定义位于 `src/main/resources/db/schema.sql`，不自动建表。生产用户密码须为 BCrypt 哈希。

## 认证接口

| 方法 | 路径 | 用途 | 凭据 |
| --- | --- | --- | --- |
| GET | /api/users/login-key | 获取加密公钥和一次性凭据 | 无 |
| POST | /api/users/login | 登录并获取双Token | 加密请求体，见下文 |
| POST | /api/users/token | 获取双Token，与登录等价 | 加密请求体，见下文 |
| POST | /api/users/refresh-token | 刷新双Token | JSON refreshToken |
| POST | /api/users/logout | 撤销当前登录会话 | Bearer accessToken |
| GET | /api/users/me | 当前用户 | Bearer accessToken |

登录不再接受明文账号密码；加密前的 JSON 结构保持不变。

登录和刷新返回 `data.accessToken`、`data.refreshToken`、`data.tokenType="Bearer"`、`data.expiresAt`、`data.refreshExpiresAt`、`data.user`；时间为ISO-8601 UTC。后续业务请求通过 `Authorization: Bearer <accessToken>` 携带访问凭据。

accessToken默认15分钟，普通refresh会话8小时，保持登录7天，可在 `app.jwt` 修改。刷新同时换发两个Token，旧Token立即失效，且不会延长最初的会话截止时间。access与refresh使用不同type声明，不能互换。刷新会再次检查账户是否启用；会话轮换通过内存原子操作或Redis Lua原子比较更新，重放旧refreshToken返回401。

退出撤销当前整组凭据，其他独立登录会话保留。mock重启后所有会话失效；prod会话存Redis且有TTL，Redis异常时返回503，不降级放行。会话ID用于登录撤销，不用于日志跟踪。

## 白名单与统一错误

在 `application.yml` 的 `app.security.whitelist` 配置方法和路径，过滤器及Spring Security共用同一套匹配规则。当前放行 GET 登录公钥及 POST 登录、获取Token和刷新接口。刷新接口仍校验请求体中的refreshToken；白名单不是免除其业务校验。新增接口默认要求accessToken；CORS预检作为浏览器基础通信处理，普通OPTIONS请求仍需要认证。

所有业务、参数校验和认证错误使用统一响应：400参数错误、401未登录/凭据无效、403无权限、404不存在、405方法不支持、409冲突、415格式不支持、500服务错误、503存储不可用。错误码见 `common/ErrorCode.java`。

## 前端配合

glw-web默认使用后端认证，Vite将 `/api` 代理到8080。业务页面仍使用本地mock数据。前端统一请求层自动带accessToken，快过期或收到401时只刷新一次，并在刷新成功后重试；并发请求共用刷新任务。refreshToken失效会退出到登录页，网络失败则保留可重试状态。

前端 `.env.local` 设置 `VITE_AUTH_MODE=mock` 可继续独立静态演示；这些演示凭据不能调用真实后端。上线需将 `/api` 反向代理到后端，或配置VITE_API_BASE_URL并调整CORS允许来源。

## 结构与验证

`controller/`、`service/`、`repository/`、`mapper/`、`model/dto|entity|vo/` 按层组织。`auth/` 包含JWT签发校验、过滤器、白名单匹配、会话存储；`config/` 包含JWT、白名单、CORS、MyBatis配置。

`requests/users.http` 提供完整请求示例。自动化测试覆盖Token签名/类型/有效期、刷新轮换/重放、并发轮换、退出撤销、白名单方法匹配、参数校验、CORS及统一错误。真实MySQL/Redis环境需替换配置后联调。

## 登录传输加密

1. 匿名 GET `/api/users/login-key` 获取 `keyId/publicKey/challenge/expiresAt`，响应禁止缓存。公钥为标准 Base64 编码的 X.509 SPKI。
2. 每次登录生成 AES-256 密钥和随机 12 字节 IV，以 AES-GCM 加密 UTF-8 JSON `{username,password,remember}`，认证附加数据为 UTF-8 `keyId.challenge`，认证标签为 128 位并追加在密文末尾。
3. 使用 RSA-2048 OAEP 包装 AES 原始密钥，摘要和 MGF1 均为 SHA-256，label 为空。向 POST `/api/users/login` 或 `/api/users/token` 发送 `{keyId,challenge,encryptedKey,iv,ciphertext}`；后三项为标准 Base64。
4. 后端解密、校验参数后执行现有 BCrypt 登录验证，继续返回 accessToken/refreshToken。明文请求、篡改、过期、重放均拒绝；失败后再次登录必须获取新 challenge。

challenge 有效期 120 秒、仅能使用一次。后端进程启动时生成 RSA 密钥，私钥仅保存在后端内存，不写入前端或配置。重启会使未提交的 challenge 失效，前端重新登录即可；此实现面向单实例，多个后端实例部署需改为共享 challenge 存储和统一密钥管理。内存凭据设上限并定期清理。

上线仍必须使用 HTTPS，以认证公钥来源并保护 Token 和其他接口；不能把应用层加密当作 HTTPS 替代。Web Crypto 不可用时前端明确报错，不回退到明文。前端 mock 模式仅在浏览器内模拟密钥与解密过程，不具备服务端安全边界，也不能用于生产认证。不要在代理或业务日志中记录解密参数、密码或 Token。

## 业务表实体与持久层

最新业务表定义位于 `src/main/resources/db/glw.sql`，登录表仍使用 `src/main/resources/db/schema.sql`。已提供 14 张业务表的 Entity、Mapper/XML、Service/Impl，使用说明见 [持久层说明](docs/persistence-layer.md)。
