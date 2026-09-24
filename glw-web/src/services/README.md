# 认证与接口请求

默认使用后端认证。启动 glw-backend 的 mock profile 后，无需 MySQL/Redis 即可登录。业务表单目前仍使用本地 mock 数据。

复制根目录 `.env.example` 为 `.env.local` 可改配置。需要独立静态演示时设置 `VITE_AUTH_MODE=mock`；该模式使用随机演示 Token，不会在前端保存 JWT 签名密钥，也不能访问真实后端。切换模式后重新登录。

业务接口统一调用 `apiRequest<T>('/路径', { method, body: JSON.stringify(data) })`。默认自动携带 Bearer accessToken；匿名接口显式传第三个参数 `true`，且后端也必须配置白名单。

登录返回 accessToken/refreshToken 与各自到期时间。普通会话8小时，保持登录7天，accessToken15分钟。到期前30秒刷新；接口401时刷新并最多重试一次，403不刷新。并发请求共用一个刷新任务，支持 Web Locks 的浏览器还会协调同源标签页。刷新凭据旋转但不延长原始会话到期时间。

未勾选保持登录使用 sessionStorage，勾选使用 localStorage。刷新失败为401时清理会话并返回登录页；网络或503失败保留会话以便重试。退出先撤销服务端会话，再清理本地；服务端不可达时会提示，服务端凭据只能等待到期。

`/users/login` 与 `/users/token` 均接受加密请求体获取 Token，不能直接发送明文账号密码；`/users/refresh-token` 接受 refreshToken；`/users/logout` 与 `/users/me` 必须携带 accessToken。

## 登录传输加密

1. 匿名 GET `/api/users/login-key` 获取 `keyId/publicKey/challenge/expiresAt`，响应禁止缓存。公钥为标准 Base64 编码的 X.509 SPKI。
2. 每次登录生成 AES-256 密钥和随机 12 字节 IV，以 AES-GCM 加密 UTF-8 JSON `{username,password,remember}`，认证附加数据为 UTF-8 `keyId.challenge`，认证标签为 128 位并追加在密文末尾。
3. 使用 RSA-2048 OAEP 包装 AES 原始密钥，摘要和 MGF1 均为 SHA-256，label 为空。向 POST `/api/users/login` 或 `/api/users/token` 发送 `{keyId,challenge,encryptedKey,iv,ciphertext}`；后三项为标准 Base64。
4. 后端解密、校验参数后执行现有 BCrypt 登录验证，继续返回 accessToken/refreshToken。明文请求、篡改、过期、重放均拒绝；失败后再次登录必须获取新 challenge。

challenge 有效期 120 秒、仅能使用一次。后端进程启动时生成 RSA 密钥，私钥仅保存在后端内存，不写入前端或配置。重启会使未提交的 challenge 失效，前端重新登录即可；此实现面向单实例，多个后端实例部署需改为共享 challenge 存储和统一密钥管理。内存凭据设上限并定期清理。

上线仍必须使用 HTTPS，以认证公钥来源并保护 Token 和其他接口；不能把应用层加密当作 HTTPS 替代。Web Crypto 不可用时前端明确报错，不回退到明文。前端 mock 模式仅在浏览器内模拟密钥与解密过程，不具备服务端安全边界，也不能用于生产认证。不要在代理或业务日志中记录解密参数、密码或 Token。
