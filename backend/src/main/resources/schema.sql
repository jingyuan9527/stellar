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

-- 神奇海螺 AI 匹配开关（关闭后纯随机，不调 LLM）
ALTER TABLE sys_ai_config ADD COLUMN IF NOT EXISTS conch_ai_enabled SMALLINT DEFAULT 1;
COMMENT ON COLUMN sys_ai_config.conch_ai_enabled IS '神奇海螺AI匹配开关: 0关闭(纯随机) 1开启(AI语义匹配)';

-- 最近一次拉取到的可用模型列表（逗号分隔），前端切换选择用，重新拉取时覆盖
ALTER TABLE sys_ai_config ADD COLUMN IF NOT EXISTS available_models TEXT;
COMMENT ON COLUMN sys_ai_config.available_models IS '最近拉取到的可用模型列表(逗号分隔),重新拉取时覆盖';

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

-- 游戏成绩排行榜表（数学游戏等，游客可提交、登录可跨设备）
CREATE TABLE IF NOT EXISTS sys_game_score (
    id           BIGSERIAL PRIMARY KEY,
    player_name  VARCHAR(64) NOT NULL,
    score        INT NOT NULL,
    total_time   INT NOT NULL,
    accuracy     DOUBLE PRECISION NOT NULL,
    user_id      BIGINT,
    ip           VARCHAR(64),
    create_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE  sys_game_score IS '游戏成绩排行榜表';
COMMENT ON COLUMN sys_game_score.id IS '主键';
COMMENT ON COLUMN sys_game_score.player_name IS '玩家名称(游客填/登录取昵称)';
COMMENT ON COLUMN sys_game_score.score IS '得分(答对题数)';
COMMENT ON COLUMN sys_game_score.total_time IS '用时(秒)';
COMMENT ON COLUMN sys_game_score.accuracy IS '正确率(%)';
COMMENT ON COLUMN sys_game_score.user_id IS '登录用户ID(可空,游客为NULL)';
COMMENT ON COLUMN sys_game_score.ip IS '提交IP(反作弊/统计)';
COMMENT ON COLUMN sys_game_score.create_time IS '提交时间';
CREATE INDEX IF NOT EXISTS idx_sys_game_score_rank ON sys_game_score (score DESC, total_time ASC);

-- 文件表（图片等，二进制存数据库，无磁盘依赖）
CREATE TABLE IF NOT EXISTS sys_file (
    id            BIGSERIAL PRIMARY KEY,
    original_name VARCHAR(255),
    ext           VARCHAR(32),
    content_type  VARCHAR(100),
    size          BIGINT,
    data          BYTEA,
    create_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE  sys_file IS '文件表(图片等,二进制存数据库)';
COMMENT ON COLUMN sys_file.id IS '主键';
COMMENT ON COLUMN sys_file.original_name IS '原始文件名';
COMMENT ON COLUMN sys_file.ext IS '扩展名(小写)';
COMMENT ON COLUMN sys_file.content_type IS 'MIME类型';
COMMENT ON COLUMN sys_file.size IS '文件大小(字节)';
COMMENT ON COLUMN sys_file.data IS '文件二进制数据';
COMMENT ON COLUMN sys_file.create_time IS '上传时间';

-- 神奇海螺预设回答表（管理员上传音频+文本，AI 按问题语义匹配）
CREATE TABLE IF NOT EXISTS conch_answer (
    id                BIGSERIAL PRIMARY KEY,
    answer_text       VARCHAR(200) NOT NULL,
    match_description VARCHAR(500),
    file_id           BIGINT NOT NULL,
    enabled           SMALLINT DEFAULT 1,
    sort_order        INT DEFAULT 0,
    create_time       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT DEFAULT 0
);
COMMENT ON TABLE  conch_answer IS '神奇海螺预设回答表';
COMMENT ON COLUMN conch_answer.id IS '主键';
COMMENT ON COLUMN conch_answer.answer_text IS '回答文本(如 确实如此)';
COMMENT ON COLUMN conch_answer.match_description IS '匹配描述(辅助 LLM 语义匹配)';
COMMENT ON COLUMN conch_answer.file_id IS '音频文件ID(引用 sys_file.id)';
COMMENT ON COLUMN conch_answer.enabled IS '是否启用: 0禁用 1启用';
COMMENT ON COLUMN conch_answer.sort_order IS '排序';
COMMENT ON COLUMN conch_answer.create_time IS '创建时间';
COMMENT ON COLUMN conch_answer.deleted IS '逻辑删除: 0未删 1已删';
CREATE INDEX IF NOT EXISTS idx_conch_answer_enabled ON conch_answer (enabled, sort_order);

-- 神奇海螺提问历史表（记录用户问题与命中的预设，不记 IP）
CREATE TABLE IF NOT EXISTS conch_record (
    id           BIGSERIAL PRIMARY KEY,
    question_text VARCHAR(500) NOT NULL,
    answer_id    BIGINT,
    user_id      BIGINT,
    create_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted      SMALLINT DEFAULT 0
);
COMMENT ON TABLE  conch_record IS '神奇海螺提问历史表';
COMMENT ON COLUMN conch_record.id IS '主键';
COMMENT ON COLUMN conch_record.question_text IS '用户提问文本';
COMMENT ON COLUMN conch_record.answer_id IS '命中的预设回答ID';
COMMENT ON COLUMN conch_record.user_id IS '登录用户ID(可空,游客为NULL)';
COMMENT ON COLUMN conch_record.create_time IS '提问时间';
COMMENT ON COLUMN conch_record.deleted IS '逻辑删除: 0未删 1已删';
CREATE INDEX IF NOT EXISTS idx_conch_record_create_time ON conch_record (create_time DESC);

-- AI 供应商表（多供应商，每组 endpoint+apiKey 对应多个模型）
CREATE TABLE IF NOT EXISTS sys_ai_provider (
    id               BIGSERIAL PRIMARY KEY,
    name             VARCHAR(100) NOT NULL,
    endpoint         VARCHAR(500) NOT NULL DEFAULT '',
    api_key          VARCHAR(500) NOT NULL DEFAULT '',
    available_models TEXT,
    enabled          SMALLINT NOT NULL DEFAULT 1,
    sort_order       INT NOT NULL DEFAULT 0,
    create_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE  sys_ai_provider IS 'AI 供应商表(多供应商,每组 endpoint+apiKey)';
COMMENT ON COLUMN sys_ai_provider.id IS '主键';
COMMENT ON COLUMN sys_ai_provider.name IS '供应商显示名(如 OpenAI/通义)';
COMMENT ON COLUMN sys_ai_provider.endpoint IS 'LLM 接口基础地址';
COMMENT ON COLUMN sys_ai_provider.api_key IS 'API Key(返回前端时脱敏)';
COMMENT ON COLUMN sys_ai_provider.available_models IS '最近拉取到的可用模型列表(逗号分隔),重新拉取时覆盖';
COMMENT ON COLUMN sys_ai_provider.enabled IS '是否启用: 0禁用 1启用';
COMMENT ON COLUMN sys_ai_provider.sort_order IS '排序';
COMMENT ON COLUMN sys_ai_provider.create_time IS '创建时间';
COMMENT ON COLUMN sys_ai_provider.update_time IS '更新时间';

-- AI 模型表（供应商下多个模型，带类型标签，按类型设默认）
CREATE TABLE IF NOT EXISTS sys_ai_model (
    id           BIGSERIAL PRIMARY KEY,
    provider_id  BIGINT NOT NULL,
    model        VARCHAR(200) NOT NULL,
    model_type   VARCHAR(32) NOT NULL DEFAULT 'TEXT',
    enabled      SMALLINT NOT NULL DEFAULT 1,
    is_default   SMALLINT NOT NULL DEFAULT 0,
    sort_order   INT NOT NULL DEFAULT 0,
    create_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE  sys_ai_model IS 'AI 模型表(供应商下多模型,带类型标签)';
COMMENT ON COLUMN sys_ai_model.id IS '主键';
COMMENT ON COLUMN sys_ai_model.provider_id IS '供应商ID(引用 sys_ai_provider.id)';
COMMENT ON COLUMN sys_ai_model.model IS '模型名称';
COMMENT ON COLUMN sys_ai_model.model_type IS '模型类型: TEXT/IMAGE/AUDIO/EMBEDDING/VIDEO(字典 model_type)';
COMMENT ON COLUMN sys_ai_model.enabled IS '是否启用: 0禁用 1启用';
COMMENT ON COLUMN sys_ai_model.is_default IS '该类型下默认: 0否 1是(同类型互斥)';
COMMENT ON COLUMN sys_ai_model.sort_order IS '排序';
COMMENT ON COLUMN sys_ai_model.create_time IS '创建时间';
COMMENT ON COLUMN sys_ai_model.update_time IS '更新时间';
CREATE INDEX IF NOT EXISTS idx_sys_ai_model_provider ON sys_ai_model (provider_id);
CREATE INDEX IF NOT EXISTS idx_sys_ai_model_type_default ON sys_ai_model (model_type, is_default);

-- 字典类型表（管理枚举值集合，如 model_type 的可选值）
CREATE TABLE IF NOT EXISTS sys_dict_type (
    id           BIGSERIAL PRIMARY KEY,
    dict_code    VARCHAR(64) NOT NULL UNIQUE,
    dict_name    VARCHAR(100) NOT NULL,
    enabled      SMALLINT NOT NULL DEFAULT 1,
    sort_order   INT NOT NULL DEFAULT 0,
    create_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE  sys_dict_type IS '字典类型表(管理枚举值集合)';
COMMENT ON COLUMN sys_dict_type.id IS '主键';
COMMENT ON COLUMN sys_dict_type.dict_code IS '字典编码(唯一,如 model_type)';
COMMENT ON COLUMN sys_dict_type.dict_name IS '字典中文名';
COMMENT ON COLUMN sys_dict_type.enabled IS '是否启用: 0禁用 1启用';
COMMENT ON COLUMN sys_dict_type.sort_order IS '排序';
COMMENT ON COLUMN sys_dict_type.create_time IS '创建时间';
COMMENT ON COLUMN sys_dict_type.update_time IS '更新时间';

-- 字典数据表（某字典类型下的具体可选值）
CREATE TABLE IF NOT EXISTS sys_dict_data (
    id           BIGSERIAL PRIMARY KEY,
    dict_code    VARCHAR(64) NOT NULL,
    value        VARCHAR(64) NOT NULL,
    label        VARCHAR(100) NOT NULL,
    sort_order   INT NOT NULL DEFAULT 0,
    enabled      SMALLINT NOT NULL DEFAULT 1,
    create_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE  sys_dict_data IS '字典数据表(某字典类型下的可选值)';
COMMENT ON COLUMN sys_dict_data.id IS '主键';
COMMENT ON COLUMN sys_dict_data.dict_code IS '所属字典编码(引用 sys_dict_type.dict_code)';
COMMENT ON COLUMN sys_dict_data.value IS '字典值(如 TEXT/IMAGE)';
COMMENT ON COLUMN sys_dict_data.label IS '显示标签(如 文本对话/图片生成)';
COMMENT ON COLUMN sys_dict_data.sort_order IS '排序';
COMMENT ON COLUMN sys_dict_data.enabled IS '是否启用: 0禁用 1启用';
COMMENT ON COLUMN sys_dict_data.create_time IS '创建时间';
COMMENT ON COLUMN sys_dict_data.update_time IS '更新时间';
CREATE INDEX IF NOT EXISTS idx_sys_dict_data_code ON sys_dict_data (dict_code, enabled, sort_order);

-- 系统设置表（全局开关/单值配置，如 conch_ai_enabled）
CREATE TABLE IF NOT EXISTS sys_setting (
    id            BIGSERIAL PRIMARY KEY,
    setting_key   VARCHAR(100) NOT NULL UNIQUE,
    setting_value VARCHAR(500),
    description   VARCHAR(255),
    create_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE  sys_setting IS '系统设置表(全局开关/单值配置)';
COMMENT ON COLUMN sys_setting.id IS '主键';
COMMENT ON COLUMN sys_setting.setting_key IS '设置键(唯一,如 conch_ai_enabled)';
COMMENT ON COLUMN sys_setting.setting_value IS '设置值';
COMMENT ON COLUMN sys_setting.description IS '说明';
COMMENT ON COLUMN sys_setting.create_time IS '创建时间';
COMMENT ON COLUMN sys_setting.update_time IS '更新时间';

-- 字典种子：model_type（AI 模型类型，预留多种）
INSERT INTO sys_dict_type (dict_code, dict_name, enabled, sort_order)
SELECT 'model_type', 'AI模型类型', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE dict_code = 'model_type');

INSERT INTO sys_dict_data (dict_code, value, label, sort_order, enabled)
SELECT 'model_type', 'TEXT', '文本对话', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_code = 'model_type' AND value = 'TEXT');
INSERT INTO sys_dict_data (dict_code, value, label, sort_order, enabled)
SELECT 'model_type', 'IMAGE', '图片生成', 2, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_code = 'model_type' AND value = 'IMAGE');
INSERT INTO sys_dict_data (dict_code, value, label, sort_order, enabled)
SELECT 'model_type', 'AUDIO', '语音合成', 3, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_code = 'model_type' AND value = 'AUDIO');
INSERT INTO sys_dict_data (dict_code, value, label, sort_order, enabled)
SELECT 'model_type', 'EMBEDDING', '向量嵌入', 4, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_code = 'model_type' AND value = 'EMBEDDING');
INSERT INTO sys_dict_data (dict_code, value, label, sort_order, enabled)
SELECT 'model_type', 'VIDEO', '视频生成', 5, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_code = 'model_type' AND value = 'VIDEO');

-- 迁移：sys_ai_config id=1 的单条配置 → 一条 sys_ai_provider + 一条 TEXT 默认模型；
-- conch_ai_enabled → sys_setting。幂等（目标表已有数据则跳过）。
-- 用纯 SQL 而非 DO 块，避免 Spring ScriptUtils 按分号截断 PL/pgSQL 的 dollar quote。

-- 供应商迁移：仅当 provider 表为空时，从旧配置取 endpoint/apiKey/available_models
INSERT INTO sys_ai_provider (name, endpoint, api_key, available_models, enabled, sort_order, create_time, update_time)
SELECT '默认供应商', endpoint, api_key, available_models, 1, 0, NOW(), NOW()
FROM sys_ai_config
WHERE id = 1
  AND endpoint IS NOT NULL
  AND endpoint <> ''
  AND NOT EXISTS (SELECT 1 FROM sys_ai_provider);

-- 模型迁移：仅当 model 表为空且 provider 表非空时，从旧配置取 model 名挂到首个供应商
INSERT INTO sys_ai_model (provider_id, model, model_type, enabled, is_default, sort_order, create_time, update_time)
SELECT (SELECT id FROM sys_ai_provider ORDER BY id LIMIT 1),
       model, 'TEXT', 1, 1, 0, NOW(), NOW()
FROM sys_ai_config
WHERE id = 1
  AND model IS NOT NULL
  AND model <> ''
  AND NOT EXISTS (SELECT 1 FROM sys_ai_model)
  AND EXISTS (SELECT 1 FROM sys_ai_provider);

-- conch_ai_enabled → sys_setting（幂等，无旧配置则默认 1）
INSERT INTO sys_setting (setting_key, setting_value, description, create_time, update_time)
SELECT 'conch_ai_enabled',
       COALESCE((SELECT conch_ai_enabled::text FROM sys_ai_config WHERE id = 1 LIMIT 1), '1'),
       '神奇海螺AI匹配开关: 0关闭(纯随机) 1开启(AI语义匹配)', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_setting WHERE setting_key = 'conch_ai_enabled');

-- sys_ai_usage 扩展：关联供应商与模型类型，便于按类型/供应商统计
ALTER TABLE sys_ai_usage ADD COLUMN IF NOT EXISTS provider_id BIGINT;
ALTER TABLE sys_ai_usage ADD COLUMN IF NOT EXISTS model_type VARCHAR(32);
COMMENT ON COLUMN sys_ai_usage.provider_id IS '供应商ID(引用 sys_ai_provider.id,自带key为NULL)';
COMMENT ON COLUMN sys_ai_usage.model_type IS '模型类型: TEXT/IMAGE/...(自带key默认TEXT)';
CREATE INDEX IF NOT EXISTS idx_sys_ai_usage_provider ON sys_ai_usage (provider_id);
CREATE INDEX IF NOT EXISTS idx_sys_ai_usage_model_type ON sys_ai_usage (model_type);

-- AI 图片生成异步任务表（后端异步包同步 API：请求立即返回 taskId，异步线程生成+存库，前端轮询）
CREATE TABLE IF NOT EXISTS sys_ai_image_task (
    id            BIGSERIAL PRIMARY KEY,
    model_id      BIGINT NOT NULL,
    provider_id   BIGINT,
    subject_type  VARCHAR(16),
    subject_id    VARCHAR(64),
    prompt        TEXT NOT NULL,
    size          VARCHAR(32),
    ratio         VARCHAR(32),
    status        VARCHAR(16) NOT NULL DEFAULT 'generating',
    file_id       BIGINT,
    error_msg     TEXT,
    create_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE  sys_ai_image_task IS 'AI 图片生成异步任务表';
COMMENT ON COLUMN sys_ai_image_task.id IS '主键';
COMMENT ON COLUMN sys_ai_image_task.model_id IS '模型ID(引用 sys_ai_model.id)';
COMMENT ON COLUMN sys_ai_image_task.provider_id IS '供应商ID';
COMMENT ON COLUMN sys_ai_image_task.subject_type IS '主体类型: account/ip';
COMMENT ON COLUMN sys_ai_image_task.subject_id IS '主体ID: userId 或 IP';
COMMENT ON COLUMN sys_ai_image_task.prompt IS '提示词';
COMMENT ON COLUMN sys_ai_image_task.size IS '尺寸档位: 1K/2K/...';
COMMENT ON COLUMN sys_ai_image_task.ratio IS '宽高比: 1:1/16:9/...';
COMMENT ON COLUMN sys_ai_image_task.status IS '状态: generating/completed/failed';
COMMENT ON COLUMN sys_ai_image_task.file_id IS '生成图片文件ID(引用 sys_file.id)';
COMMENT ON COLUMN sys_ai_image_task.error_msg IS '失败原因';
COMMENT ON COLUMN sys_ai_image_task.create_time IS '创建时间';
COMMENT ON COLUMN sys_ai_image_task.update_time IS '更新时间';
CREATE INDEX IF NOT EXISTS idx_sys_ai_image_task_subject ON sys_ai_image_task (subject_type, subject_id, create_time DESC);
