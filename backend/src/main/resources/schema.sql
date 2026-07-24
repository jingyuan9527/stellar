CREATE TABLE IF NOT EXISTS sys_user (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(64)  NOT NULL UNIQUE,
    password    VARCHAR(128) NOT NULL,
    nickname    VARCHAR(64),
    avatar      VARCHAR(255),
    status      SMALLINT     DEFAULT 1,
    deleted     SMALLINT     DEFAULT 0,
    create_time TIMESTAMP,
    update_time TIMESTAMP
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

CREATE TABLE IF NOT EXISTS sys_log (
    id              BIGSERIAL PRIMARY KEY,
    module          VARCHAR(64),
    operation_type  VARCHAR(32),
    operator        VARCHAR(64),
    request_method  VARCHAR(10),
    request_url     VARCHAR(255),
    java_method     VARCHAR(255),
    params          TEXT,
    status          SMALLINT,
    error_msg       TEXT,
    ip              VARCHAR(64),
    duration        BIGINT,
    create_time     TIMESTAMP
);

COMMENT ON TABLE  sys_log IS '系统操作日志表';
COMMENT ON COLUMN sys_log.id IS '主键';
COMMENT ON COLUMN sys_log.module IS '操作模块';
COMMENT ON COLUMN sys_log.operation_type IS '操作类型: LOGIN/LOGOUT/INSERT/UPDATE/DELETE/QUERY/EXPORT/OTHER';
COMMENT ON COLUMN sys_log.operator IS '操作人用户名';
COMMENT ON COLUMN sys_log.request_method IS 'HTTP请求方法';
COMMENT ON COLUMN sys_log.request_url IS '请求URL';
COMMENT ON COLUMN sys_log.java_method IS '执行Java方法(类.方法)';
COMMENT ON COLUMN sys_log.params IS '请求参数(已脱敏)';
COMMENT ON COLUMN sys_log.status IS '操作状态: 1成功 0失败';
COMMENT ON COLUMN sys_log.error_msg IS '异常信息';
COMMENT ON COLUMN sys_log.ip IS '操作IP';
COMMENT ON COLUMN sys_log.duration IS '执行耗时(ms)';
COMMENT ON COLUMN sys_log.create_time IS '创建时间';

CREATE INDEX IF NOT EXISTS idx_sys_log_create_time ON sys_log (create_time DESC);
CREATE INDEX IF NOT EXISTS idx_sys_log_operator ON sys_log (operator);
CREATE INDEX IF NOT EXISTS idx_sys_log_module ON sys_log (module);
CREATE INDEX IF NOT EXISTS idx_sys_log_status ON sys_log (status);
CREATE INDEX IF NOT EXISTS idx_sys_log_op_time ON sys_log (operator, create_time DESC);

CREATE TABLE IF NOT EXISTS tts_record (
    id          BIGSERIAL PRIMARY KEY,
    text        VARCHAR(2000) NOT NULL,
    voice       VARCHAR(100) NOT NULL,
    rate        DOUBLE PRECISION DEFAULT 1.0,
    pitch       DOUBLE PRECISION DEFAULT 1.0,
    volume      DOUBLE PRECISION DEFAULT 1.0,
    audio_data  BYTEA,
    file_size   BIGINT,
    operator    VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted     SMALLINT DEFAULT 0
);

COMMENT ON TABLE  tts_record IS '语音合成记录表';
COMMENT ON COLUMN tts_record.id IS '主键';
COMMENT ON COLUMN tts_record.text IS '合成文本';
COMMENT ON COLUMN tts_record.voice IS '发音人名称';
COMMENT ON COLUMN tts_record.rate IS '语速 0.5~2.0';
COMMENT ON COLUMN tts_record.pitch IS '音调 0~2.0';
COMMENT ON COLUMN tts_record.volume IS '音量 0~1.0';
COMMENT ON COLUMN tts_record.audio_data IS 'MP3音频数据';
COMMENT ON COLUMN tts_record.file_size IS '音频文件大小(字节)';
COMMENT ON COLUMN tts_record.operator IS '操作人用户名';
COMMENT ON COLUMN tts_record.create_time IS '创建时间';
COMMENT ON COLUMN tts_record.deleted IS '逻辑删除: 0未删 1已删';

CREATE INDEX IF NOT EXISTS idx_tts_record_create_time ON tts_record (create_time DESC);
CREATE INDEX IF NOT EXISTS idx_tts_record_operator ON tts_record (operator);
