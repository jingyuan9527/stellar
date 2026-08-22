package com.stellar.memos.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.ai.service.AiAgentLoopService;
import com.stellar.ai.service.AiChatService;
import com.stellar.ai.tool.WebPageFetchTool;
import com.stellar.common.BusinessException;
import com.stellar.infra.ExternalCallLogger;
import com.stellar.infra.RedisMutex;
import com.stellar.infra.SafeUrlValidator;
import com.stellar.memos.client.MemosApiClient;
import com.stellar.memos.dto.MemosConfigDTO;
import com.stellar.memos.dto.MemosQueryDTO;
import com.stellar.memos.entity.MemosNote;
import com.stellar.memos.entity.MemosSyncLog;
import com.stellar.memos.mapper.MemosNoteMapper;
import com.stellar.memos.vo.MemosConfigVO;
import com.stellar.memos.vo.MemosJobResultVO;
import com.stellar.memos.vo.MemosNoteVO;
import com.stellar.memos.vo.MemosStatsVO;
import com.stellar.memos.vo.MemosSyncLogVO;
import com.stellar.memos.vo.MemosSyncResultVO;
import com.stellar.memos.vo.MemosWebhookConfigVO;
import com.stellar.system.service.SysSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 备忘同步编排：拉取 memo.booksy.cf 笔记备份到本地（远端删除 → 本地标记删除）、
 * 勾选笔记 AI 打标签并自动写回远端（content 末尾追加 #标签）、手动标签写回兜底、webhook 实时接收。
 * <p>动作互不影响、各自触发：{@link #syncPull} / {@link #aiTag} / {@link #pushTags}。
 * AI 打标签为勾选制，成功后自动写回，失败置待写回可手动重试。
 * <p>关注点拆分：签名校验与去重在 {@link MemosWebhookGuard}，同步互斥在 infra {@link RedisMutex}，
 * 状态记录存取在 {@link MemosSyncLogStore}，标签文本处理在 {@link MemosTagCodec}；本类只做流程编排与合并语义。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemosService {

    /** 设置键（sys_setting） */
    static final String KEY_BASE_URL = "memos_base_url";
    static final String KEY_TOKEN = "memos_token";
    static final String KEY_PROMPT = "memo_tag_prompt";
    static final String KEY_WEBHOOK_SECRET = "memos_webhook_secret";

    /** 定时/手动同步互斥锁 key（Redis SETNX，防定时与手动同时拉取） */
    static final String SYNC_LOCK_KEY = "stellar:memos:sync:lock";
    /** 互斥锁兜底 TTL：正常同步完成后主动释放；异常未释放时此处兜底防死锁 */
    private static final Duration SYNC_LOCK_TTL = Duration.ofMinutes(30);

    /** 同步触发方式 / 状态 */
    static final String SYNC_TRIGGER_SCHEDULED = "scheduled";
    static final String SYNC_TRIGGER_MANUAL = "manual";
    static final String SYNC_STATUS_SUCCESS = "success";
    static final String SYNC_STATUS_PARTIAL = "partial";
    static final String SYNC_STATUS_FAILED = "failed";
    static final String SYNC_STATUS_SKIPPED = "skipped";

    /** Memos 支持的活动类型 */
    private static final String TYPE_MEMO_CREATED = "memos.memo.created";
    private static final String TYPE_MEMO_UPDATED = "memos.memo.updated";
    private static final String TYPE_MEMO_DELETED = "memos.memo.deleted";
    private static final String TYPE_MEMO_COMMENT_CREATED = "memos.memo.comment.created";

    /** 默认提示词模板（sys_setting 为空时的兜底） */
    static final String DEFAULT_PROMPT =
            "你是笔记标签生成助手。为下面的笔记内容生成 2-5 个简洁准确的中文标签。\n"
                    + "要求：\n- 只输出标签本身，用顿号或逗号分隔，放在一行\n"
                    + "- 不要输出编号、解释或多余文字\n- 标签要精准概括笔记主题\n\n笔记内容：\n{{content}}";

    /**
     * 打标签 system 消息：告知 LLM 有 fetch_url 工具可用。
     * 用户自定义提示词模板（memo_tag_prompt）保持不动，避免破坏既有配置语义。
     */
    private static final String TAG_SYSTEM_PROMPT =
            "若笔记内容包含网页链接且链接内容影响标签判断，先调用 fetch_url 工具获取网页内容再打标签；"
                    + "无法获取的链接直接跳过，基于笔记已有内容打标签。";

    private final MemosNoteMapper memosNoteMapper;
    private final MemosApiClient memosApiClient;
    private final SysSettingService sysSettingService;
    private final AiChatService aiChatService;
    private final AiAgentLoopService agentLoopService;
    private final WebPageFetchTool webPageFetchTool;
    private final ExternalCallLogger externalCallLogger;
    private final MemosRagService memosRagService;
    private final MemosWebhookGuard webhookGuard;
    private final RedisMutex redisMutex;
    private final MemosSyncLogStore syncLogStore;
    private final MemosTagCodec tagCodec;
    private final ObjectMapper objectMapper;

    // ===== 配置 =====

    public MemosConfigVO getConfig() {
        MemosConfigVO vo = new MemosConfigVO();
        vo.setBaseUrl(sysSettingService.get(KEY_BASE_URL, ""));
        vo.setTokenConfigured(StringUtils.hasText(sysSettingService.get(KEY_TOKEN, "")));
        vo.setPromptTemplate(sysSettingService.get(KEY_PROMPT, DEFAULT_PROMPT));
        return vo;
    }

    public void saveConfig(MemosConfigDTO dto) {
        if (StringUtils.hasText(dto.getBaseUrl())) {
            // SSRF 防护 + 规范化（去尾部斜杠）
            String normalized = SafeUrlValidator.normalizePublicBaseUrl(dto.getBaseUrl(), "Memos 域名");
            sysSettingService.set(KEY_BASE_URL, normalized, null);
        }
        if (StringUtils.hasText(dto.getToken())) {
            sysSettingService.set(KEY_TOKEN, dto.getToken().trim(), null);
        }
        if (dto.getPromptTemplate() != null) {
            sysSettingService.set(KEY_PROMPT, dto.getPromptTemplate(), null);
        }
        log.info("[备忘同步] 配置已保存 operator={}", operator());
    }

    // ===== Webhook 配置 =====

    public MemosWebhookConfigVO getWebhookConfig() {
        MemosWebhookConfigVO vo = new MemosWebhookConfigVO();
        vo.setSecretConfigured(StringUtils.hasText(sysSettingService.get(KEY_WEBHOOK_SECRET, "")));
        return vo;
    }

    public void saveWebhookSecret(String secret) {
        if (StringUtils.hasText(secret)) {
            sysSettingService.set(KEY_WEBHOOK_SECRET, secret.trim(), null);
            log.info("[备忘同步] Webhook 签名密钥已保存");
        }
    }

    // ===== Webhook 接收（Memos 服务器主动推送，与主动拉取并行）=====

    /**
     * 处理 Memos webhook 投递：签名验证 → 去重 → 按活动类型分发。
     * <p>签名验证失败抛 {@link BusinessException}（由 Controller 转 4xx 拒绝投递）；
     * 成功返回处理结果 Map（status: created/updated/unchanged/marked/skipped/ignored/duplicate）。
     * 与主动拉取共用 {@link #mergeRemote}/{@link #insertMemo} 落库逻辑，保留本地待写回标签。
     */
    public Map<String, Object> handleWebhook(byte[] rawBody, String webhookId, String timestamp, String signature) {
        webhookGuard.verifySignature(rawBody, webhookId, timestamp, signature);
        if (!webhookGuard.dedupeWebhookId(webhookId)) {
            log.info("[备忘同步] Webhook 重复投递已忽略 id={}", webhookId);
            return Map.of("status", "duplicate");
        }
        try {
            JsonNode body = objectMapper.readTree(rawBody);
            String activityType = body.path("activityType").asText("");
            JsonNode memoNode = body.path("memo");
            LocalDateTime now = LocalDateTime.now();
            switch (activityType) {
                case TYPE_MEMO_CREATED:
                case TYPE_MEMO_UPDATED:
                    return upsertFromWebhook(memoNode, now);
                case TYPE_MEMO_DELETED:
                    return markDeletedFromWebhook(memoNode, now);
                case TYPE_MEMO_COMMENT_CREATED:
                    log.info("[备忘同步] Webhook 忽略评论事件 id={}", webhookId);
                    return Map.of("status", "ignored", "type", activityType);
                default:
                    log.warn("[备忘同步] Webhook 未知活动类型 type={} id={}", activityType, webhookId);
                    return Map.of("status", "ignored", "type", activityType);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[备忘同步] Webhook payload 解析失败 id={}: {}", webhookId, e.getMessage(), e);
            throw new BusinessException("Webhook payload 解析失败");
        }
    }

    /** created/updated 事件：payload 完整 memo 直接 upsert（与 pull 同源逻辑）。 */
    private Map<String, Object> upsertFromWebhook(JsonNode memoNode, LocalDateTime now) {
        MemosApiClient.MemosRemoteMemo rm = memosApiClient.parseMemo(memoNode);
        if (rm == null) {
            log.warn("[备忘同步] Webhook memo 缺少 uid，跳过");
            return Map.of("status", "skipped");
        }
        MemosNote local = memosNoteMapper.selectOne(
                new LambdaQueryWrapper<MemosNote>().eq(MemosNote::getUid, rm.uid()));
        String status;
        if (local == null) {
            insertMemo(rm, now);
            status = "created";
        } else {
            status = mergeRemote(local, rm, now) ? "updated" : "unchanged";
        }
        log.info("[备忘同步] Webhook 事件处理完成 uid={} status={}", rm.uid(), status);
        return Map.of("status", status, "uid", rm.uid());
    }

    /** deleted 事件：本地标记远端删除（保留备份，与拉取语义一致）。 */
    private Map<String, Object> markDeletedFromWebhook(JsonNode memoNode, LocalDateTime now) {
        MemosApiClient.MemosRemoteMemo rm = memosApiClient.parseMemo(memoNode);
        if (rm == null) {
            log.warn("[备忘同步] Webhook 删除事件缺少 memo uid，跳过");
            return Map.of("status", "skipped");
        }
        MemosNote local = memosNoteMapper.selectOne(
                new LambdaQueryWrapper<MemosNote>().eq(MemosNote::getUid, rm.uid()));
        if (local != null && (local.getRemoteDeleted() == null || local.getRemoteDeleted() == 0)) {
            local.setRemoteDeleted(1);
            local.setUpdateTime(now);
            memosNoteMapper.updateById(local);
            // 标记删除后应从检索剔除，清检索缓存
            memosRagService.invalidateCache();
            log.info("[备忘同步] Webhook 标记远端已删 uid={} id={}", rm.uid(), local.getId());
            return Map.of("status", "marked", "uid", rm.uid());
        }
        return Map.of("status", "unchanged", "uid", rm.uid());
    }

    // ===== 立即同步（拉取备份 + 标记远端已删）=====

    public MemosSyncResultVO syncPull() {
        CheckConfig cfg = loadConfig();
        List<MemosApiClient.MemosRemoteMemo> remote = memosApiClient.listAllMemos(cfg.baseUrl(), cfg.token());

        Map<String, MemosNote> existing = memosNoteMapper.selectList(new LambdaQueryWrapper<MemosNote>())
                .stream().collect(Collectors.toMap(MemosNote::getUid, n -> n, (a, b) -> a));

        MemosSyncResultVO result = new MemosSyncResultVO();
        result.setFetched(remote.size());
        Set<String> seen = new HashSet<>();

        LocalDateTime now = LocalDateTime.now();
        for (MemosApiClient.MemosRemoteMemo rm : remote) {
            seen.add(rm.uid());
            MemosNote local = existing.get(rm.uid());
            try {
                if (local == null) {
                    insertMemo(rm, now);
                    result.setCreated(result.getCreated() + 1);
                } else if (isWebhookStub(local)) {
                    // 实时 webhook 先插入的空壳（payload 无远端时间戳）：本次为首次全量同步，
                    // 只补全远端时间戳并按新增计，避免把新 memo 误判成更新
                    backfillStub(local, rm, now);
                    result.setCreated(result.getCreated() + 1);
                } else {
                    boolean changed = mergeRemote(local, rm, now);
                    if (changed) {
                        result.setUpdated(result.getUpdated() + 1);
                    }
                }
            } catch (Exception e) {
                result.setErrors(result.getErrors() + 1);
                log.error("[备忘同步] upsert 失败 uid={}: {}", rm.uid(), e.getMessage(), e);
            }
        }

        // 远端不再存在的本地记录 → 标记删除（保留备份）
        int marked = 0;
        for (MemosNote local : existing.values()) {
            if (local.getRemoteDeleted() == null || local.getRemoteDeleted() == 0) {
                if (!seen.contains(local.getUid())) {
                    local.setRemoteDeleted(1);
                    local.setUpdateTime(now);
                    memosNoteMapper.updateById(local);
                    marked++;
                    // 标记删除后应从检索剔除，清检索缓存
                    memosRagService.invalidateCache();
                    log.info("[备忘同步] 标记远端已删 uid={} id={}", local.getUid(), local.getId());
                }
            }
        }
        result.setMarkedDeleted(marked);
        log.info("[备忘同步] 同步完成 fetched={} created={} updated={} markedDeleted={} errors={}",
                result.getFetched(), result.getCreated(), result.getUpdated(), result.getMarkedDeleted(), result.getErrors());
        return result;
    }

    // ===== 记录式同步（手动/定时共用）：状态落库 + Redis 互斥 + 清理 =====

    /** 是否已配置 Memos 域名与 Token（用于未配置时记 skipped 而非 failed）。 */
    public boolean isPullConfigured() {
        return StringUtils.hasText(sysSettingService.get(KEY_BASE_URL, ""))
                && StringUtils.hasText(sysSettingService.get(KEY_TOKEN, ""));
    }

    /** 手动「立即同步」入口：调用方为 {@link com.stellar.memos.controller.MemosController#pull()}，
     *  锁被占用/同步失败均抛 {@link BusinessException} 由前端提示。 */
    public MemosSyncResultVO syncPullManual() {
        return doRecordedPull(SYNC_TRIGGER_MANUAL, false);
    }

    /** 定时同步入口：未配置/失败均落状态记录后内部吞掉异常，不阻断调度线程。 */
    public MemosSyncResultVO scheduledSyncPull() {
        try {
            return doRecordedPull(SYNC_TRIGGER_SCHEDULED, true);
        } catch (Exception e) {
            log.error("[备忘同步] 定时同步异常: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 记录式同步核心：Redis SETNX 互斥（防定时与手动重叠）→ 校验配置 → 调 {@link #syncPull} →
     * 状态落 {@code memos_sync_log}（success/partial/failed/skipped）→ 顺带清理保留期外旧记录。
     * <p>{@code scheduled=true} 时异常吞掉返回 null（仅日志+落 failed 记录）；
     * 手动则原样上抛，保证前端拿到明确提示。
     */
    private MemosSyncResultVO doRecordedPull(String triggerType, boolean scheduled) {
        if (!redisMutex.tryAcquire(SYNC_LOCK_KEY, SYNC_LOCK_TTL)) {
            if (scheduled) {
                log.warn("[备忘同步] 上一轮同步仍在进行，跳过本次定时同步");
                return null;
            }
            throw new BusinessException("同步正在进行中，请稍后再试");
        }
        long start = System.currentTimeMillis();
        MemosSyncLog record = new MemosSyncLog();
        record.setTriggerType(triggerType);
        try {
            // 未配置：记 skipped；定时静默返回，手动抛提示（避免再被下面的 catch 记成 failed）
            if (!isPullConfigured()) {
                record.setStatus(SYNC_STATUS_SKIPPED);
                syncLogStore.persist(record, start);
                log.warn("[备忘同步] {}同步跳过：未配置 Memos 域名/Token", triggerType);
                if (scheduled) {
                    return null;
                }
                throw new BusinessException("请先在「备忘同步」页配置 Memos 域名与 Token");
            }
            try {
                MemosSyncResultVO result = syncPull();
                record.setStatus(result.getErrors() > 0 ? SYNC_STATUS_PARTIAL : SYNC_STATUS_SUCCESS);
                record.setFetched(result.getFetched());
                record.setCreated(result.getCreated());
                record.setUpdated(result.getUpdated());
                record.setMarkedDeleted(result.getMarkedDeleted());
                record.setErrors(result.getErrors());
                syncLogStore.persist(record, start);
                return result;
            } catch (Exception e) {
                record.setStatus(SYNC_STATUS_FAILED);
                record.setErrorMessage(e.getMessage());
                syncLogStore.persist(record, start);
                if (scheduled) {
                    log.error("[备忘同步] 定时同步失败: {}", e.getMessage(), e);
                    return null;
                }
                if (e instanceof BusinessException be) {
                    throw be;
                }
                throw new BusinessException("同步失败: " + e.getMessage());
            }
        } finally {
            redisMutex.release(SYNC_LOCK_KEY);
        }
    }

    /** 新建一条本地笔记（pull 与 webhook 共用同源落库逻辑）。 */
    private void insertMemo(MemosApiClient.MemosRemoteMemo rm, LocalDateTime now) {
        MemosNote note = new MemosNote();
        note.setUid(rm.uid());
        note.setContent(MemosTagCodec.stripTrailingTagBlock(rm.content()));
        note.setTags(MemosTagCodec.joinTags(rm.tags()));
        note.setTagsSynced(1);
        note.setRemoteDeleted(0);
        note.setRemoteCreateTime(rm.createTime());
        note.setRemoteUpdateTime(rm.updateTime());
        note.setCreateTime(now);
        note.setUpdateTime(now);
        memosNoteMapper.insert(note);
        // 新笔记异步向量化（失败不阻断同步，rebuild 兜底）
        memosRagService.embedNoteAsync(note.getId(), note.getContent());
    }

    /** webhook 先插入的空壳：远端时间戳缺失即视为待补全元数据的占位行。 */
    private boolean isWebhookStub(MemosNote local) {
        return local.getRemoteCreateTime() == null && local.getRemoteUpdateTime() == null;
    }

    /** 全量拉取补全 webhook 空壳缺失的远端时间戳（内容/标签 webhook 已落，仅填元数据）。 */
    private void backfillStub(MemosNote local, MemosApiClient.MemosRemoteMemo rm, LocalDateTime now) {
        local.setRemoteCreateTime(rm.createTime());
        local.setRemoteUpdateTime(rm.updateTime());
        local.setRemoteDeleted(0);
        local.setUpdateTime(now);
        memosNoteMapper.updateById(local);
        log.info("[备忘同步] 补全 webhook 空壳远端时间戳 uid={} id={} create={} update={}",
                rm.uid(), local.getId(), rm.createTime(), rm.updateTime());
    }

    /**
     * 合并远端到本地（内容/时间/标签以远端为源，但保留本地未写回的 AI 标签）。
     * 返回是否有变化（需更新）。
     */
    private boolean mergeRemote(MemosNote local, MemosApiClient.MemosRemoteMemo rm, LocalDateTime now) {
        String cleanContent = MemosTagCodec.stripTrailingTagBlock(rm.content());
        Set<String> remoteTags = new LinkedHashSet<>(rm.tags());
        // 本地已有标签（可能是 AI 生成未写回）与远端并集，避免同步丢失待写回标签
        Set<String> localTags = MemosTagCodec.splitTags(local.getTags());
        Set<String> merged = new LinkedHashSet<>(remoteTags);
        merged.addAll(localTags);
        // 若本地存在远端没有的标签 → 仍未写回，tags_synced=0；否则远端已含全部 → 1
        boolean hasPending = localTags.stream().anyMatch(t -> !remoteTags.contains(t));

        boolean changed = local.getRemoteDeleted() != null && local.getRemoteDeleted() == 1
                || !cleanContent.equals(local.getContent())
                || !MemosTagCodec.joinTags(merged).equals(local.getTags())
                || (local.getRemoteUpdateTime() == null ? rm.updateTime() != null
                    : !local.getRemoteUpdateTime().equals(rm.updateTime()));
        if (!changed) {
            return false;
        }
        local.setContent(cleanContent);
        local.setTags(MemosTagCodec.joinTags(merged));
        // 远端内容与本地一致、且远端已含全部标签时仍按远端状态（避免残留 pending）
        local.setTagsSynced(hasPending ? 0 : 1);
        local.setRemoteDeleted(0);
        local.setRemoteCreateTime(rm.createTime());
        local.setRemoteUpdateTime(rm.updateTime());
        local.setUpdateTime(now);
        memosNoteMapper.updateById(local);
        // 内容变更后异步重新向量化（失败不阻断合并，rebuild 兜底）
        memosRagService.embedNoteAsync(local.getId(), cleanContent);
        return true;
    }

    // ===== AI 打标签（勾选笔记）+ 自动写回 =====

    /**
     * 对勾选的笔记用 AI 打标签：新标签与现有标签合并落本地，随后自动写回远端
     * （content 末尾追加 #标签，已含的跳过）。写回失败置 tags_synced=0，可手动重试。
     *
     * @param modelId AI 模型 id（TEXT 类型），空则用后端 TEXT 默认模型
     */
    public MemosJobResultVO aiTag(List<Long> ids, Long modelId) {
        CheckConfig cfg = loadConfig();
        String template = StringUtils.hasText(sysSettingService.get(KEY_PROMPT, ""))
                ? sysSettingService.get(KEY_PROMPT, "")
                : DEFAULT_PROMPT;

        List<MemosNote> notes = memosNoteMapper.selectBatchIds(ids);
        log.info("[备忘同步] AI 打标签开始 selected={} found={} modelId={}", ids.size(), notes.size(), modelId);

        MemosJobResultVO result = new MemosJobResultVO();
        result.setProcessed(notes.size());
        for (MemosNote note : notes) {
            if (note.getRemoteDeleted() != null && note.getRemoteDeleted() == 1) {
                result.setSkipped(result.getSkipped() + 1);
                log.debug("[备忘同步] 跳过远端已删笔记 uid={} id={}", note.getUid(), note.getId());
                continue;
            }
            try {
                List<String> newTags = tagWithAi(template, note, cfg, modelId);
                if (newTags.isEmpty()) {
                    result.setFailed(result.getFailed() + 1);
                    log.warn("[备忘同步] AI 标签为空 uid={} id={}", note.getUid(), note.getId());
                    continue;
                }
                Set<String> merged = MemosTagCodec.splitTags(note.getTags());
                merged.addAll(newTags);
                note.setTags(MemosTagCodec.joinTags(merged));
                note.setTagsSynced(0);
                note.setUpdateTime(LocalDateTime.now());
                memosNoteMapper.updateById(note);
                result.setSuccess(result.getSuccess() + 1);
                log.info("[备忘同步] AI 打标签成功 uid={} tags={}", note.getUid(), note.getTags());
                try {
                    pushTagsForNote(cfg, note);
                    result.setPushSuccess(result.getPushSuccess() + 1);
                } catch (Exception e) {
                    result.setPushFailed(result.getPushFailed() + 1);
                    log.error("[备忘同步] 标签自动写回失败 uid={} id={}: {}",
                            note.getUid(), note.getId(), e.getMessage(), e);
                }
            } catch (Exception e) {
                result.setFailed(result.getFailed() + 1);
                log.error("[备忘同步] AI 打标签失败 uid={} id={}: {}", note.getUid(), note.getId(), e.getMessage(), e);
            }
        }
        log.info("[备忘同步] AI 打标签完成 processed={} success={} skipped={} failed={} pushSuccess={} pushFailed={}",
                result.getProcessed(), result.getSuccess(), result.getSkipped(), result.getFailed(),
                result.getPushSuccess(), result.getPushFailed());
        return result;
    }

    /**
     * 单条笔记 AI 打标签：模板填充 → agent 循环（LLM 可调 fetch_url 抓取笔记内链接
     * 的网页内容后再打标，模型不支持 tools 自动降级纯文本）→ 解析标签列表。
     */
    private List<String> tagWithAi(String template, MemosNote note, CheckConfig cfg, Long modelId) {
        String prompt = template.contains("{{content}}")
                ? template.replace("{{content}}", note.getContent() == null ? "" : note.getContent())
                : template + (template.endsWith("\n") ? "" : "\n") + (note.getContent() == null ? "" : note.getContent());
        String content = note.getContent() == null ? "" : note.getContent();
        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", TAG_SYSTEM_PROMPT),
                Map.of("role", "user", "content", prompt));
        long start = System.currentTimeMillis();
        String llmResult = agentLoopService.runWithDegrade(messages,
                List.of(webPageFetchTool.definition()), webPageFetchTool::execute, modelId);
        externalCallLogger.success("备忘同步AI打标签", cfg.baseUrl(),
                "uid=" + note.getUid() + ", contentLen=" + content.length() + ", modelId=" + modelId,
                System.currentTimeMillis() - start);
        return tagCodec.parseTagsFromText(llmResult);
    }

    // ===== 同步标签到 Memos（写回）=====

    /**
     * 把本地待写回（tags_synced=0）且远端存活的标签写回远端（content 末尾追加 #标签）。
     */
    public MemosJobResultVO pushTags() {
        CheckConfig cfg = loadConfig();
        List<MemosNote> notes = memosNoteMapper.selectList(new LambdaQueryWrapper<MemosNote>()
                .eq(MemosNote::getRemoteDeleted, 0)
                .eq(MemosNote::getTagsSynced, 0));
        log.info("[备忘同步] 标签写回开始 count={}", notes.size());

        MemosJobResultVO result = new MemosJobResultVO();
        result.setProcessed(notes.size());
        for (MemosNote note : notes) {
            try {
                boolean wroteContent = pushTagsForNote(cfg, note);
                if (wroteContent) {
                    result.setSuccess(result.getSuccess() + 1);
                } else {
                    result.setSkipped(result.getSkipped() + 1);
                }
            } catch (Exception e) {
                result.setFailed(result.getFailed() + 1);
                log.error("[备忘同步] 标签写回失败 uid={} id={}: {}", note.getUid(), note.getId(), e.getMessage(), e);
            }
        }
        log.info("[备忘同步] 标签写回完成 processed={} success={} skipped={} failed={}",
                result.getProcessed(), result.getSuccess(), result.getSkipped(), result.getFailed());
        return result;
    }

    /**
     * 单条标签写回远端：content 已含全部 #标签 → 置已同步返回 false；否则在 content 末尾
     * 追加缺失标签并调 UpdateMemo，成功返回 true。失败抛异常（由调用方统计并保留待写回）。
     */
    private boolean pushTagsForNote(CheckConfig cfg, MemosNote note) {
        List<String> tags = MemosTagCodec.splitTags(note.getTags()).stream().toList();
        // 已在 content 中的 #标签不重复追加
        Set<String> presentInContent = MemosTagCodec.collectTagsInContent(note.getContent());
        List<String> toPush = tags.stream().filter(t -> !presentInContent.contains(t)).toList();
        if (toPush.isEmpty()) {
            // 无新增内容标签：远端已含，视为已同步
            note.setTagsSynced(1);
            note.setUpdateTime(LocalDateTime.now());
            memosNoteMapper.updateById(note);
            return false;
        }
        String newContent = MemosTagCodec.buildContentWithTags(note.getContent(), toPush);
        memosApiClient.updateContent(cfg.baseUrl(), cfg.token(), note.getUid(), newContent);
        note.setTagsSynced(1);
        note.setUpdateTime(LocalDateTime.now());
        memosNoteMapper.updateById(note);
        log.info("[备忘同步] 标签写回成功 uid={} tags={}", note.getUid(), toPush);
        return true;
    }

    // ===== 查询 =====

    public Page<MemosNoteVO> page(MemosQueryDTO query) {
        LambdaQueryWrapper<MemosNote> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            // 全文搜索：支持空格/中英文逗号/顿号/分号分隔多词，词间 AND；
            // 每个词命中 content/uid/tags 任一即可（如 "git ai" = 同时含 git 与 ai）
            String[] words = query.getKeyword().trim().split("[\\s,，、;；]+");
            wrapper.and(w -> {
                for (String word : words) {
                    if (word.isBlank()) {
                        continue;
                    }
                    w.and(inner -> inner.like(MemosNote::getContent, word)
                            .or().like(MemosNote::getUid, word)
                            .or().like(MemosNote::getTags, word));
                }
            });
        }
        if (query.getRemoteDeleted() != null) {
            wrapper.eq(MemosNote::getRemoteDeleted, query.getRemoteDeleted());
        }
        wrapper.orderByAsc(MemosNote::getRemoteDeleted)
                .orderByDesc(MemosNote::getRemoteUpdateTime);
        Page<MemosNote> page = memosNoteMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        Page<MemosNoteVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return voPage;
    }

    public MemosStatsVO stats() {
        MemosStatsVO vo = new MemosStatsVO();
        vo.setTotal(memosNoteMapper.selectCount(null));
        vo.setActive(memosNoteMapper.selectCount(new LambdaQueryWrapper<MemosNote>()
                .eq(MemosNote::getRemoteDeleted, 0)));
        vo.setDeleted(memosNoteMapper.selectCount(new LambdaQueryWrapper<MemosNote>()
                .eq(MemosNote::getRemoteDeleted, 1)));
        vo.setUntagged(countActiveWithNoTags());
        vo.setPendingPush(countActivePendingPush());
        return vo;
    }

    /** 同步状态记录分页（委托 {@link MemosSyncLogStore}）。 */
    public Page<MemosSyncLogVO> pageSyncLog(MemosQueryDTO query) {
        return syncLogStore.page(query);
    }

    /** 最近一次同步状态（空记录返回 null）。 */
    public MemosSyncLogVO latestSyncLog() {
        return syncLogStore.latest();
    }

    // ===== 私有 helper =====

    private MemosNoteVO toVO(MemosNote n) {
        MemosNoteVO vo = new MemosNoteVO();
        vo.setId(n.getId());
        vo.setUid(n.getUid());
        vo.setContent(n.getContent());
        vo.setTags(MemosTagCodec.splitTags(n.getTags()).stream().toList());
        vo.setTagsSynced(n.getTagsSynced());
        vo.setRemoteDeleted(n.getRemoteDeleted());
        vo.setRemoteCreateTime(n.getRemoteCreateTime());
        vo.setRemoteUpdateTime(n.getRemoteUpdateTime());
        vo.setCreateTime(n.getCreateTime());
        vo.setUpdateTime(n.getUpdateTime());
        return vo;
    }

    /** 读取配置并校验：域名与 Token 必须已配置，域名经安全校验。 */
    private CheckConfig loadConfig() {
        String baseUrl = sysSettingService.get(KEY_BASE_URL, "");
        String token = sysSettingService.get(KEY_TOKEN, "");
        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(token)) {
            throw new BusinessException("请先在「备忘同步」页配置 Memos 域名与 Token");
        }
        String normalized = SafeUrlValidator.normalizePublicBaseUrl(baseUrl, "Memos 域名");
        return new CheckConfig(normalized, token.trim());
    }

    private record CheckConfig(String baseUrl, String token) {
    }

    private long countActiveWithNoTags() {
        return memosNoteMapper.selectCount(new LambdaQueryWrapper<MemosNote>()
                .eq(MemosNote::getRemoteDeleted, 0)
                .and(w -> w.isNull(MemosNote::getTags).or().eq(MemosNote::getTags, "")));
    }

    private long countActivePendingPush() {
        return memosNoteMapper.selectCount(new LambdaQueryWrapper<MemosNote>()
                .eq(MemosNote::getRemoteDeleted, 0)
                .eq(MemosNote::getTagsSynced, 0));
    }

    private String operator() {
        try {
            if (StpUtil.isLogin()) {
                return "account:" + StpUtil.getLoginIdAsString();
            }
        } catch (Exception ignored) {
        }
        return "anonymous";
    }
}