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

-- 个人项目展示表（about 页公开展示，多条）
CREATE TABLE IF NOT EXISTS sys_profile_project (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,
    site_url     VARCHAR(500),
    source_url   VARCHAR(500),
    description  VARCHAR(500),
    create_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE  sys_profile_project IS '个人项目展示(about 页公开)';
COMMENT ON COLUMN sys_profile_project.id IS '主键';
COMMENT ON COLUMN sys_profile_project.name IS '项目名';
COMMENT ON COLUMN sys_profile_project.site_url IS '线上地址(可空,为空则不展示访问按钮)';
COMMENT ON COLUMN sys_profile_project.source_url IS '源码地址(如 GitHub,可空)';
COMMENT ON COLUMN sys_profile_project.description IS '简介(1-2 句)';
COMMENT ON COLUMN sys_profile_project.create_time IS '创建时间';
COMMENT ON COLUMN sys_profile_project.update_time IS '更新时间';

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

-- sys_file 扩展：记录上传者（历史数据/系统生成为 NULL）
ALTER TABLE sys_file ADD COLUMN IF NOT EXISTS user_id BIGINT;
COMMENT ON COLUMN sys_file.user_id IS '上传者用户ID(可空,历史数据/系统生成为NULL)';
CREATE INDEX IF NOT EXISTS idx_sys_file_user_id ON sys_file (user_id);
CREATE INDEX IF NOT EXISTS idx_sys_file_create_time ON sys_file (create_time DESC);

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

-- chat_tts_engine → sys_setting（聊天 TTS 引擎开关，用户未选音色时兜底；ai=优先AI失败降级Edge，edge=直接Edge）
INSERT INTO sys_setting (setting_key, setting_value, description, create_time, update_time)
SELECT 'chat_tts_engine', 'ai',
       '聊天TTS引擎: ai(优先AI失败降级Edge) / edge(直接Edge); 用户在聊天页选了具体音色则覆盖此开关', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_setting WHERE setting_key = 'chat_tts_engine');

-- 备忘同步（Memos）配置 → sys_setting（上线后由用户在「备忘同步」页配置）
INSERT INTO sys_setting (setting_key, setting_value, description, create_time, update_time)
SELECT 'memos_base_url', '',
       'Memos 实例域名(如 https://memo.booksy.cf, 末尾不带/)', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_setting WHERE setting_key = 'memos_base_url');

INSERT INTO sys_setting (setting_key, setting_value, description, create_time, update_time)
SELECT 'memos_token', '',
       'Memos API Token(个人 Access Token)', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_setting WHERE setting_key = 'memos_token');

INSERT INTO sys_setting (setting_key, setting_value, description, create_time, update_time)
SELECT 'memo_tag_prompt',
       '你是笔记标签生成助手。为下面的笔记内容生成 2-5 个简洁准确的中文标签。\n要求：\n- 只输出标签本身，用顿号或逗号分隔，放在一行\n- 不要输出编号、解释或多余文字\n- 标签要精准概括笔记主题\n\n笔记内容：\n{{content}}',
       'AI 打标签提示词模板(含 {{content}} 占位符)', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_setting WHERE setting_key = 'memo_tag_prompt');

-- ===== 备忘同步（Memos 笔记备份 + AI 打标签）=====
-- 全量拉取 memo.booksy.cf 笔记备份到本地；远端删除 → 本地标记删除；AI 标签写回远端(content 追加 #标签)。
CREATE TABLE IF NOT EXISTS memos_note (
    id                 BIGSERIAL PRIMARY KEY,
    uid                VARCHAR(64) NOT NULL UNIQUE,
    content            TEXT NOT NULL,
    tags               VARCHAR(1000),
    tags_synced        SMALLINT DEFAULT 1,
    remote_deleted     SMALLINT DEFAULT 0,
    remote_create_time TIMESTAMP,
    remote_update_time TIMESTAMP,
    create_time        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time        TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE  memos_note IS '备忘同步笔记备份表(Memos全量拉取)';
COMMENT ON COLUMN memos_note.id IS '主键';
COMMENT ON COLUMN memos_note.uid IS 'Memos 笔记 UID(远端唯一标识)';
COMMENT ON COLUMN memos_note.content IS '笔记原文(Markdown, 去除尾部 #标签 块)';
COMMENT ON COLUMN memos_note.tags IS '当前有效标签(逗号分隔, 远端解析+AI新增)';
COMMENT ON COLUMN memos_note.tags_synced IS '标签是否已写回远端: 0待写回 1已同步(含无标签)';
COMMENT ON COLUMN memos_note.remote_deleted IS '远端是否已删除: 0存活 1标记删除';
COMMENT ON COLUMN memos_note.remote_create_time IS '远端创建时间';
COMMENT ON COLUMN memos_note.remote_update_time IS '远端更新时间';
COMMENT ON COLUMN memos_note.create_time IS '本地入库时间';
COMMENT ON COLUMN memos_note.update_time IS '本地更新时间';
CREATE INDEX IF NOT EXISTS idx_memos_note_uid ON memos_note (uid);
CREATE INDEX IF NOT EXISTS idx_memos_note_remote_deleted ON memos_note (remote_deleted, tags_synced);

-- sys_ai_usage 扩展：关联供应商与模型类型，便于按类型/供应商统计
ALTER TABLE sys_ai_usage ADD COLUMN IF NOT EXISTS provider_id BIGINT;
ALTER TABLE sys_ai_usage ADD COLUMN IF NOT EXISTS model_type VARCHAR(32);
COMMENT ON COLUMN sys_ai_usage.provider_id IS '供应商ID(引用 sys_ai_provider.id,自带key为NULL)';
COMMENT ON COLUMN sys_ai_usage.model_type IS '模型类型: TEXT/IMAGE/...(自带key默认TEXT)';
CREATE INDEX IF NOT EXISTS idx_sys_ai_usage_provider ON sys_ai_usage (provider_id);
CREATE INDEX IF NOT EXISTS idx_sys_ai_usage_model_type ON sys_ai_usage (model_type);


-- ===== AI 聊天模块 =====
-- 向量存 ai_knowledge_chunk.embedding(TEXT, JSON 数组文本 [v1,v2,...])，纯 Java 内存余弦检索，无 pgvector 依赖。

-- AI 人设表（预设 system prompt，聊天时快捷选择）
CREATE TABLE IF NOT EXISTS ai_persona (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    system_prompt TEXT NOT NULL,
    description   VARCHAR(500),
    enabled       SMALLINT NOT NULL DEFAULT 1,
    sort_order    INT NOT NULL DEFAULT 0,
    built_in      SMALLINT NOT NULL DEFAULT 0,
    create_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted       SMALLINT DEFAULT 0
);
COMMENT ON TABLE  ai_persona IS 'AI 人设表(预设 system prompt)';
COMMENT ON COLUMN ai_persona.id IS '主键';
COMMENT ON COLUMN ai_persona.name IS '人设名称(如 通用助手/程序员)';
COMMENT ON COLUMN ai_persona.system_prompt IS '系统提示词(注入 LLM messages[0].role=system)';
COMMENT ON COLUMN ai_persona.description IS '描述说明';
COMMENT ON COLUMN ai_persona.enabled IS '是否启用: 0禁用 1启用';
COMMENT ON COLUMN ai_persona.sort_order IS '排序';
COMMENT ON COLUMN ai_persona.built_in IS '是否内置: 0否 1是(不可删,可恢复默认)';
COMMENT ON COLUMN ai_persona.create_time IS '创建时间';
COMMENT ON COLUMN ai_persona.update_time IS '更新时间';
COMMENT ON COLUMN ai_persona.deleted IS '逻辑删除: 0未删 1已删';
CREATE INDEX IF NOT EXISTS idx_ai_persona_enabled ON ai_persona (enabled, sort_order);

-- AI 聊天会话表（一个会话=多轮消息，主体按账号/IP区分）
CREATE TABLE IF NOT EXISTS ai_chat_session (
    id           BIGSERIAL PRIMARY KEY,
    title         VARCHAR(200),
    persona_id    BIGINT,
    kb_id         BIGINT,
    subject_type  VARCHAR(16) NOT NULL,
    subject_id    VARCHAR(64) NOT NULL,
    create_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted       SMALLINT DEFAULT 0
);
COMMENT ON TABLE  ai_chat_session IS 'AI 聊天会话表(多轮对话容器)';
COMMENT ON COLUMN ai_chat_session.id IS '主键';
COMMENT ON COLUMN ai_chat_session.title IS '会话标题(取首条用户消息截断或LLM生成)';
COMMENT ON COLUMN ai_chat_session.persona_id IS '关联人设ID(引用 ai_persona.id,可空)';
COMMENT ON COLUMN ai_chat_session.kb_id IS '关联知识库ID(引用 ai_knowledge_base.id,可空,启用RAG)';
COMMENT ON COLUMN ai_chat_session.subject_type IS '主体类型: account/ip';
COMMENT ON COLUMN ai_chat_session.subject_id IS '主体ID: userId 或 IP';
COMMENT ON COLUMN ai_chat_session.create_time IS '创建时间';
COMMENT ON COLUMN ai_chat_session.update_time IS '更新时间(最近一轮消息时间)';
COMMENT ON COLUMN ai_chat_session.deleted IS '逻辑删除: 0未删 1已删';
CREATE INDEX IF NOT EXISTS idx_ai_chat_session_subject ON ai_chat_session (subject_type, subject_id, update_time DESC);

-- AI 聊天消息表（会话内逐条 user/assistant 消息）
CREATE TABLE IF NOT EXISTS ai_chat_message (
    id           BIGSERIAL PRIMARY KEY,
    session_id   BIGINT NOT NULL,
    role          VARCHAR(16) NOT NULL,
    content       TEXT NOT NULL,
    tokens        INT DEFAULT 0,
    create_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE  ai_chat_message IS 'AI 聊天消息表(会话内逐条消息)';
COMMENT ON COLUMN ai_chat_message.id IS '主键';
COMMENT ON COLUMN ai_chat_message.session_id IS '会话ID(引用 ai_chat_session.id)';
COMMENT ON COLUMN ai_chat_message.role IS '角色: system/user/assistant';
COMMENT ON COLUMN ai_chat_message.content IS '消息内容';
COMMENT ON COLUMN ai_chat_message.tokens IS '该条消息token数(可空)';
COMMENT ON COLUMN ai_chat_message.create_time IS '创建时间';
CREATE INDEX IF NOT EXISTS idx_ai_chat_message_session ON ai_chat_message (session_id, create_time ASC);

-- ai_chat_message 扩展：工具调用产物（图片/音频）附件，聊天 function calling 生成后挂在 assistant 消息上
ALTER TABLE ai_chat_message ADD COLUMN IF NOT EXISTS attachment_type VARCHAR(16);
ALTER TABLE ai_chat_message ADD COLUMN IF NOT EXISTS attachment_file_id BIGINT;
COMMENT ON COLUMN ai_chat_message.attachment_type IS '附件类型: image/audio (NULL=纯文本消息)';
COMMENT ON COLUMN ai_chat_message.attachment_file_id IS '附件文件ID(引用 sys_file.id)';

-- AI 长期记忆表（定期整理会话为事实陈述，按账号，对话时注入 system prompt）
CREATE TABLE IF NOT EXISTS ai_memory (
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT NOT NULL,
    content            TEXT NOT NULL,
    source_session_id  BIGINT,
    create_time        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted            SMALLINT DEFAULT 0
);
COMMENT ON TABLE  ai_memory IS 'AI 长期记忆表(会话摘要事实陈述,按账号)';
COMMENT ON COLUMN ai_memory.id IS '主键';
COMMENT ON COLUMN ai_memory.user_id IS '用户ID(仅登录用户有长期记忆)';
COMMENT ON COLUMN ai_memory.content IS '记忆内容(LLM整理的事实陈述)';
COMMENT ON COLUMN ai_memory.source_session_id IS '来源会话ID(可空)';
COMMENT ON COLUMN ai_memory.create_time IS '创建时间';
COMMENT ON COLUMN ai_memory.deleted IS '逻辑删除: 0未删 1已删';
CREATE INDEX IF NOT EXISTS idx_ai_memory_user ON ai_memory (user_id, create_time DESC);

-- AI 知识库表（RAG 文档集合，每个 KB 含多个分块）
CREATE TABLE IF NOT EXISTS ai_knowledge_base (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(100) NOT NULL,
    description         VARCHAR(500),
    embedding_model_id  BIGINT,
    chunk_count         INT DEFAULT 0,
    create_time         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT DEFAULT 0
);
COMMENT ON TABLE  ai_knowledge_base IS 'AI 知识库表(RAG文档集合)';
COMMENT ON COLUMN ai_knowledge_base.id IS '主键';
COMMENT ON COLUMN ai_knowledge_base.name IS '知识库名称';
COMMENT ON COLUMN ai_knowledge_base.description IS '描述';
COMMENT ON COLUMN ai_knowledge_base.embedding_model_id IS '向量化模型ID(引用 sys_ai_model.id,EMBEDDING类型)';
COMMENT ON COLUMN ai_knowledge_base.chunk_count IS '分块数(冗余计数)';
COMMENT ON COLUMN ai_knowledge_base.create_time IS '创建时间';
COMMENT ON COLUMN ai_knowledge_base.update_time IS '更新时间';
COMMENT ON COLUMN ai_knowledge_base.deleted IS '逻辑删除: 0未删 1已删';

-- AI 知识库分块表（文档分块+向量，embedding 存 JSON 数组文本，纯 Java 内存余弦检索）
CREATE TABLE IF NOT EXISTS ai_knowledge_chunk (
    id           BIGSERIAL PRIMARY KEY,
    kb_id        BIGINT NOT NULL,
    chunk_text   TEXT NOT NULL,
    chunk_index  INT NOT NULL DEFAULT 0,
    token_count  INT DEFAULT 0,
    source_name  VARCHAR(255),
    embedding    TEXT,
    create_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE  ai_knowledge_chunk IS 'AI 知识库分块表(文档分块+向量)';
COMMENT ON COLUMN ai_knowledge_chunk.id IS '主键';
COMMENT ON COLUMN ai_knowledge_chunk.kb_id IS '知识库ID(引用 ai_knowledge_base.id)';
COMMENT ON COLUMN ai_knowledge_chunk.chunk_text IS '分块文本';
COMMENT ON COLUMN ai_knowledge_chunk.chunk_index IS '分块序号(同KB内从0)';
COMMENT ON COLUMN ai_knowledge_chunk.token_count IS '分块token估算';
COMMENT ON COLUMN ai_knowledge_chunk.source_name IS '来源文档名(可空)';
COMMENT ON COLUMN ai_knowledge_chunk.embedding IS '向量(JSON数组文本 [v1,v2,...]),纯Java内存余弦检索,默认查询不加载';
COMMENT ON COLUMN ai_knowledge_chunk.create_time IS '创建时间';
CREATE INDEX IF NOT EXISTS idx_ai_knowledge_chunk_kb ON ai_knowledge_chunk (kb_id, chunk_index);
-- 幂等：已存在的表补 embedding 列
ALTER TABLE ai_knowledge_chunk ADD COLUMN IF NOT EXISTS embedding TEXT;

-- 内置人设种子（幂等）
INSERT INTO ai_persona (name, system_prompt, description, enabled, sort_order, built_in, create_time, update_time)
SELECT '通用助手', '你是一个友好、博学的助手。请用简洁清晰的中文回答用户问题。', '默认通用对话助手', 1, 0, 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM ai_persona WHERE built_in = 1);
INSERT INTO ai_persona (name, system_prompt, description, enabled, sort_order, built_in, create_time, update_time)
SELECT '程序员', '你是一位资深全栈工程师，精通 Java、Vue、TypeScript、数据库与系统设计。回答技术问题时给出准确、可落地的方案与代码示例，指出潜在坑点。', '技术问答助手', 1, 1, 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM ai_persona WHERE name = '程序员' AND built_in = 1);
INSERT INTO ai_persona (name, system_prompt, description, enabled, sort_order, built_in, create_time, update_time)
SELECT '写作助手', '你是一位优秀的写作搭档，擅长润色、改写、构思大纲与生成多平台文案。根据用户需求提供多种风格的文本，并简要说明取舍。', '写作与文案助手', 1, 2, 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM ai_persona WHERE name = '写作助手' AND built_in = 1);

-- ============================================================
-- 统一 AI 任务历史表（合并 sys_ai_chat_record / sys_ai_image_task / sys_ai_video_task / tts_record 四张表的查询视图）
-- 旧表保留不删，新写入走此表，旧数据通过下方 DO 块幂等迁移
-- ============================================================
CREATE TABLE IF NOT EXISTS ai_task (
    id            BIGSERIAL PRIMARY KEY,
    task_type     VARCHAR(16) NOT NULL,
    subject_type  VARCHAR(16),
    subject_id    VARCHAR(64),
    provider_id   BIGINT,
    model         VARCHAR(100),
    prompt        TEXT NOT NULL,
    result        TEXT,
    status        VARCHAR(16) NOT NULL DEFAULT 'success',
    error_msg     TEXT,
    file_id       BIGINT,
    file_data     BYTEA,
    file_size     BIGINT,
    audio_format  VARCHAR(16),
    extra         JSONB DEFAULT '{}',
    request_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    response_time TIMESTAMP,
    duration_ms   BIGINT,
    deleted       SMALLINT DEFAULT 0,
    create_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE  ai_task IS '统一AI任务历史(文案/图片/视频/TTS)';
COMMENT ON COLUMN ai_task.task_type IS '任务类型: text/image/video/tts';
COMMENT ON COLUMN ai_task.subject_type IS '主体类型: account/ip';
COMMENT ON COLUMN ai_task.subject_id IS '主体ID: userId或IP或operator';
COMMENT ON COLUMN ai_task.provider_id IS '供应商ID';
COMMENT ON COLUMN ai_task.model IS '模型名';
COMMENT ON COLUMN ai_task.prompt IS '输入提示词/文本';
COMMENT ON COLUMN ai_task.result IS '文本输出(text类型)';
COMMENT ON COLUMN ai_task.status IS '状态: generating/success/completed/failed';
COMMENT ON COLUMN ai_task.error_msg IS '失败原因';
COMMENT ON COLUMN ai_task.file_id IS '产物文件ID(引用sys_file, image/video)';
COMMENT ON COLUMN ai_task.file_data IS '内联二进制(tts音频, 兼容旧逻辑)';
COMMENT ON COLUMN ai_task.file_size IS '产物文件大小(字节)';
COMMENT ON COLUMN ai_task.audio_format IS '音频格式: mp3/wav (tts类型)';
COMMENT ON COLUMN ai_task.extra IS '类型专属参数JSON: voice/rate/pitch/volume/size/ratio/duration/width/height/video_id等';
COMMENT ON COLUMN ai_task.request_time IS '请求时间';
COMMENT ON COLUMN ai_task.response_time IS '返回时间';
COMMENT ON COLUMN ai_task.duration_ms IS '耗时(毫秒)';
COMMENT ON COLUMN ai_task.deleted IS '逻辑删除: 0未删 1已删';
CREATE INDEX IF NOT EXISTS idx_ai_task_type_subject ON ai_task (task_type, subject_type, subject_id, request_time DESC);
CREATE INDEX IF NOT EXISTS idx_ai_task_request_time ON ai_task (request_time DESC);
