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

-- AI 配置表（项目级单一配置）
CREATE TABLE IF NOT EXISTS sys_ai_config (
    id          BIGSERIAL PRIMARY KEY,
    endpoint    VARCHAR(500) DEFAULT '',
    api_key     VARCHAR(500) DEFAULT '',
    model       VARCHAR(200) DEFAULT '',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE  sys_ai_config IS 'AI 配置表';
COMMENT ON COLUMN sys_ai_config.id IS '主键';
COMMENT ON COLUMN sys_ai_config.endpoint IS 'LLM 接口基础地址';
COMMENT ON COLUMN sys_ai_config.api_key IS 'API Key';
COMMENT ON COLUMN sys_ai_config.model IS '默认模型名称';
COMMENT ON COLUMN sys_ai_config.create_time IS '创建时间';
COMMENT ON COLUMN sys_ai_config.update_time IS '更新时间';

-- AI 提示词模板表
CREATE TABLE IF NOT EXISTS sys_ai_template (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    platform    VARCHAR(50) NOT NULL DEFAULT 'custom',
    prompt      TEXT NOT NULL,
    built_in    SMALLINT DEFAULT 0,
    creator_id  BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted     SMALLINT DEFAULT 0
);
COMMENT ON TABLE  sys_ai_template IS 'AI 提示词模板表';
COMMENT ON COLUMN sys_ai_template.id IS '主键';
COMMENT ON COLUMN sys_ai_template.name IS '模板名称';
COMMENT ON COLUMN sys_ai_template.platform IS '适用平台: bilibili/douyin/xiaohongshu/custom';
COMMENT ON COLUMN sys_ai_template.prompt IS '提示词内容,含 {{topic}} 占位符';
COMMENT ON COLUMN sys_ai_template.built_in IS '是否内置: 0否 1是';
COMMENT ON COLUMN sys_ai_template.creator_id IS '创建人ID';
COMMENT ON COLUMN sys_ai_template.create_time IS '创建时间';
COMMENT ON COLUMN sys_ai_template.update_time IS '更新时间';
COMMENT ON COLUMN sys_ai_template.deleted IS '逻辑删除: 0未删 1已删';
CREATE INDEX IF NOT EXISTS idx_sys_ai_template_platform ON sys_ai_template (platform);

-- AI 文案生成历史表
CREATE TABLE IF NOT EXISTS sys_ai_copy_result (
    id           BIGSERIAL PRIMARY KEY,
    topic        VARCHAR(500) NOT NULL,
    template_id  BIGINT,
    result       TEXT NOT NULL,
    generated_at BIGINT,
    creator_id   BIGINT,
    create_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted      SMALLINT DEFAULT 0
);
COMMENT ON TABLE  sys_ai_copy_result IS 'AI 文案生成历史表';
COMMENT ON COLUMN sys_ai_copy_result.id IS '主键';
COMMENT ON COLUMN sys_ai_copy_result.topic IS '视频主题';
COMMENT ON COLUMN sys_ai_copy_result.template_id IS '使用的模板ID';
COMMENT ON COLUMN sys_ai_copy_result.result IS '生成结果JSON: {titles,description,tags}';
COMMENT ON COLUMN sys_ai_copy_result.generated_at IS '生成时间戳(ms)';
COMMENT ON COLUMN sys_ai_copy_result.creator_id IS '创建人ID';
COMMENT ON COLUMN sys_ai_copy_result.create_time IS '创建时间';
COMMENT ON COLUMN sys_ai_copy_result.update_time IS '更新时间';
COMMENT ON COLUMN sys_ai_copy_result.deleted IS '逻辑删除: 0未删 1已删';
CREATE INDEX IF NOT EXISTS idx_sys_ai_copy_result_creator ON sys_ai_copy_result (creator_id, create_time DESC);

-- 菜单可见性配置表（控制哪些路由对游客公开）
CREATE TABLE IF NOT EXISTS sys_menu_visibility (
    id              BIGSERIAL PRIMARY KEY,
    route_key       VARCHAR(100) NOT NULL UNIQUE,
    route_name      VARCHAR(100),
    parent_key      VARCHAR(100),
    public_visible  SMALLINT DEFAULT 0,
    sort_order      INT DEFAULT 0,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE  sys_menu_visibility IS '菜单可见性配置表';
COMMENT ON COLUMN sys_menu_visibility.id IS '主键';
COMMENT ON COLUMN sys_menu_visibility.route_key IS '前端路由 key（与 useMenu 生成 key 一致，如 /tts/edge）';
COMMENT ON COLUMN sys_menu_visibility.route_name IS '路由名称（展示用）';
COMMENT ON COLUMN sys_menu_visibility.parent_key IS '父菜单 key（便于分组展示，如 /tts）';
COMMENT ON COLUMN sys_menu_visibility.public_visible IS '是否对游客公开: 0否 1是';
COMMENT ON COLUMN sys_menu_visibility.sort_order IS '排序';
COMMENT ON COLUMN sys_menu_visibility.create_time IS '创建时间';
COMMENT ON COLUMN sys_menu_visibility.update_time IS '更新时间';

-- 个人介绍表（单条配置，落地页用）
CREATE TABLE IF NOT EXISTS sys_profile (
    id           BIGSERIAL PRIMARY KEY,
    nickname     VARCHAR(64),
    avatar       VARCHAR(500),
    bio          TEXT,
    skills       VARCHAR(500),
    links        TEXT,
    update_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE  sys_profile IS '个人介绍表(单条)';
COMMENT ON COLUMN sys_profile.id IS '主键(固定1)';
COMMENT ON COLUMN sys_profile.nickname IS '昵称';
COMMENT ON COLUMN sys_profile.avatar IS '头像URL';
COMMENT ON COLUMN sys_profile.bio IS '简介';
COMMENT ON COLUMN sys_profile.skills IS '技能标签,逗号分隔';
COMMENT ON COLUMN sys_profile.links IS '外链JSON,如 {github,email,site}';
COMMENT ON COLUMN sys_profile.update_time IS '更新时间';

-- sys_profile 扩展字段（简历式 about 页用）。idempotent，已存在则跳过。
ALTER TABLE sys_profile ADD COLUMN IF NOT EXISTS title    VARCHAR(100);
ALTER TABLE sys_profile ADD COLUMN IF NOT EXISTS about    TEXT;
ALTER TABLE sys_profile ADD COLUMN IF NOT EXISTS location VARCHAR(100);
COMMENT ON COLUMN sys_profile.title IS '头衔(如 全栈开发 / 运维)';
COMMENT ON COLUMN sys_profile.about IS '关于我富文本(HTML,前端 v-html 渲染)';
COMMENT ON COLUMN sys_profile.location IS '所在地';

-- AI token 消费记录表（计费/统计用）
CREATE TABLE IF NOT EXISTS sys_ai_usage (
    id                BIGSERIAL PRIMARY KEY,
    subject_type      VARCHAR(16) NOT NULL,
    subject_id        VARCHAR(64) NOT NULL,
    model             VARCHAR(100),
    prompt_tokens     INT DEFAULT 0,
    completion_tokens INT DEFAULT 0,
    total_tokens      INT DEFAULT 0,
    source            VARCHAR(16) DEFAULT 'usage',
    create_time       TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE  sys_ai_usage IS 'AI token 消费记录表';
COMMENT ON COLUMN sys_ai_usage.id IS '主键';
COMMENT ON COLUMN sys_ai_usage.subject_type IS '主体类型: account/ip';
COMMENT ON COLUMN sys_ai_usage.subject_id IS '主体ID: userId 或 IP';
COMMENT ON COLUMN sys_ai_usage.model IS 'LLM 模型名';
COMMENT ON COLUMN sys_ai_usage.prompt_tokens IS '输入 token 数';
COMMENT ON COLUMN sys_ai_usage.completion_tokens IS '输出 token 数';
COMMENT ON COLUMN sys_ai_usage.total_tokens IS '总 token 数';
COMMENT ON COLUMN sys_ai_usage.source IS 'token 来源: usage(LLM返回) / estimate(字符估算)';
COMMENT ON COLUMN sys_ai_usage.create_time IS '调用时间';
CREATE INDEX IF NOT EXISTS idx_sys_ai_usage_subject ON sys_ai_usage (subject_type, subject_id, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_sys_ai_usage_create_time ON sys_ai_usage (create_time DESC);
