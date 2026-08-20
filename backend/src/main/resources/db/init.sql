-- Stellar Admin 初始化脚本 (PostgreSQL)
-- 使用前请先创建数据库: CREATE DATABASE stellar;

CREATE TABLE IF NOT EXISTS sys_user (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(64)  NOT NULL UNIQUE,
    password    VARCHAR(128) NOT NULL,
    nickname    VARCHAR(64),
    avatar      VARCHAR(255),
    status      SMALLINT     DEFAULT 1,
    deleted     SMALLINT     DEFAULT 0,
    create_time TIMESTAMP,
    update_time TIMESTAMP,
    must_change_password SMALLINT DEFAULT 0
);

COMMENT ON TABLE  sys_user IS '系统用户表';
COMMENT ON COLUMN sys_user.id IS '主键';
COMMENT ON COLUMN sys_user.username IS '用户名';
COMMENT ON COLUMN sys_user.password IS '密码(BCrypt)';
COMMENT ON COLUMN sys_user.nickname IS '昵称';
COMMENT ON COLUMN sys_user.avatar IS '头像URL';
COMMENT ON COLUMN sys_user.status IS '状态: 0禁用 1启用';
COMMENT ON COLUMN sys_user.deleted IS '逻辑删除: 0未删 1已删';
COMMENT ON COLUMN sys_user.create_time IS '创建时间';
COMMENT ON COLUMN sys_user.update_time IS '更新时间';
COMMENT ON COLUMN sys_user.must_change_password IS '是否强制改密: 0否 1是（默认口令首次登录后强制改密）';

-- 管理员账号由 DataInitializer 在应用启动时自动播种 (admin / 123456)
