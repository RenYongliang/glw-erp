-- MySQL 8+：手工在目标数据库执行，不由应用自动创建/修改表。
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
    password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt hash, never plaintext',
    name VARCHAR(64) NOT NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'USER',
    department VARCHAR(128) NOT NULL DEFAULT '',
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sys_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- 无生产默认管理员。插入真实用户时，通过 PasswordEncoder.encode(rawPassword) 生成 password_hash。
