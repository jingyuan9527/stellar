package com.stellar.memos.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.ai.service.AiChatService;
import com.stellar.common.BusinessException;
import com.stellar.infra.ExternalCallLogger;
import com.stellar.infra.SafeUrlValidator;
import com.stellar.memos.client.MemosApiClient;
import com.stellar.memos.dto.MemosConfigDTO;
import com.stellar.memos.dto.MemosQueryDTO;
import com.stellar.memos.entity.MemosNote;
import com.stellar.memos.mapper.MemosNoteMapper;
import com.stellar.memos.vo.MemosConfigVO;
import com.stellar.memos.vo.MemosJobResultVO;
import com.stellar.memos.vo.MemosNoteVO;
import com.stellar.memos.vo.MemosStatsVO;
import com.stellar.memos.vo.MemosSyncResultVO;
import com.stellar.system.service.SysSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 备忘同步服务：拉取 memo.booksy.cf 笔记备份到本地（远端删除 → 本地标记删除）、
 * 勾选笔记 AI 打标签并自动写回远端（content 末尾追加 #标签）、手动标签写回兜底。
 * <p>动作互不影响、各自触发：{@link #syncPull} / {@link #aiTag} / {@link #pushTags}。
 * AI 打标签为勾选制，成功后自动写回，失败置待写回可手动重试。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemosService {

    /** 设置键（sys_setting） */
    static final String KEY_BASE_URL = "memos_base_url";
    static final String KEY_TOKEN = "memos_token";
    static final String KEY_PROMPT = "memo_tag_prompt";

    /** 默认提示词模板（sys_setting 为空时的兜底） */
    static final String DEFAULT_PROMPT =
            "你是笔记标签生成助手。为下面的笔记内容生成 2-5 个简洁准确的中文标签。\n"
                    + "要求：\n- 只输出标签本身，用顿号或逗号分隔，放在一行\n"
                    + "- 不要输出编号、解释或多余文字\n- 标签要精准概括笔记主题\n\n笔记内容：\n{{content}}";

    /** 匹配 content 末尾的 #标签 块（标签写回后远端回读会带上，入库时剥离保持原文纯净） */
    private static final Pattern TRAILING_TAG_BLOCK = Pattern.compile("(?s)(?:\\s*#[^\\s#]+)+$");

    private final MemosNoteMapper memosNoteMapper;
    private final MemosApiClient memosApiClient;
    private final SysSettingService sysSettingService;
    private final AiChatService aiChatService;
    private final ExternalCallLogger externalCallLogger;
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
                    MemosNote note = new MemosNote();
                    note.setUid(rm.uid());
                    note.setContent(stripTrailingTagBlock(rm.content()));
                    note.setTags(joinTags(rm.tags()));
                    note.setTagsSynced(1);
                    note.setRemoteDeleted(0);
                    note.setRemoteCreateTime(rm.createTime());
                    note.setRemoteUpdateTime(rm.updateTime());
                    note.setCreateTime(now);
                    note.setUpdateTime(now);
                    memosNoteMapper.insert(note);
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
                    log.info("[备忘同步] 标记远端已删 uid={} id={}", local.getUid(), local.getId());
                }
            }
        }
        result.setMarkedDeleted(marked);
        log.info("[备忘同步] 同步完成 fetched={} created={} updated={} markedDeleted={} errors={}",
                result.getFetched(), result.getCreated(), result.getUpdated(), result.getMarkedDeleted(), result.getErrors());
        return result;
    }

    /**
     * 合并远端到本地（内容/时间/标签以远端为源，但保留本地未写回的 AI 标签）。
     * 返回是否有变化（需更新）。
     */
    private boolean mergeRemote(MemosNote local, MemosApiClient.MemosRemoteMemo rm, LocalDateTime now) {
        String cleanContent = stripTrailingTagBlock(rm.content());
        Set<String> remoteTags = new LinkedHashSet<>(rm.tags());
        // 本地已有标签（可能是 AI 生成未写回）与远端并集，避免同步丢失待写回标签
        Set<String> localTags = splitTags(local.getTags());
        Set<String> merged = new LinkedHashSet<>(remoteTags);
        merged.addAll(localTags);
        // 若本地存在远端没有的标签 → 仍未写回，tags_synced=0；否则远端已含全部 → 1
        boolean hasPending = localTags.stream().anyMatch(t -> !remoteTags.contains(t));

        boolean changed = local.getRemoteDeleted() != null && local.getRemoteDeleted() == 1
                || !cleanContent.equals(local.getContent())
                || !joinTags(merged).equals(local.getTags())
                || (local.getRemoteUpdateTime() == null ? rm.updateTime() != null
                    : !local.getRemoteUpdateTime().equals(rm.updateTime()));
        if (!changed) {
            return false;
        }
        local.setContent(cleanContent);
        local.setTags(joinTags(merged));
        // 远端内容与本地一致、且远端已含全部标签时仍按远端状态（避免残留 pending）
        local.setTagsSynced(hasPending ? 0 : 1);
        local.setRemoteDeleted(0);
        local.setRemoteCreateTime(rm.createTime());
        local.setRemoteUpdateTime(rm.updateTime());
        local.setUpdateTime(now);
        memosNoteMapper.updateById(local);
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
                Set<String> merged = splitTags(note.getTags());
                merged.addAll(newTags);
                note.setTags(joinTags(merged));
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

    /** 单条笔记 AI 打标签：模板填充 → LLM 非流式（指定模型或 TEXT 默认）→ 解析标签列表。 */
    private List<String> tagWithAi(String template, MemosNote note, CheckConfig cfg, Long modelId) {
        String prompt = template.contains("{{content}}")
                ? template.replace("{{content}}", note.getContent() == null ? "" : note.getContent())
                : template + (template.endsWith("\n") ? "" : "\n") + (note.getContent() == null ? "" : note.getContent());
        String content = note.getContent() == null ? "" : note.getContent();
        Map<String, String> msg = Map.of("role", "user", "content", prompt);
        long start = System.currentTimeMillis();
        String llmResult = aiChatService.chatCompletionWithMessages(List.of(msg), modelId);
        externalCallLogger.success("备忘同步AI打标签", cfg.baseUrl(),
                "uid=" + note.getUid() + ", contentLen=" + content.length() + ", modelId=" + modelId,
                System.currentTimeMillis() - start);
        return parseTagsFromText(llmResult);
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
        List<String> tags = splitTags(note.getTags()).stream().toList();
        // 已在 content 中的 #标签不重复追加
        Set<String> presentInContent = collectTagsInContent(note.getContent());
        List<String> toPush = tags.stream().filter(t -> !presentInContent.contains(t)).toList();
        if (toPush.isEmpty()) {
            // 无新增内容标签：远端已含，视为已同步
            note.setTagsSynced(1);
            note.setUpdateTime(LocalDateTime.now());
            memosNoteMapper.updateById(note);
            return false;
        }
        String newContent = buildContentWithTags(note.getContent(), toPush);
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
            String kw = query.getKeyword().trim();
            wrapper.and(w -> w.like(MemosNote::getContent, kw)
                    .or().like(MemosNote::getUid, kw)
                    .or().like(MemosNote::getTags, kw));
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

    // ===== 私有 helper =====

    private MemosNoteVO toVO(MemosNote n) {
        MemosNoteVO vo = new MemosNoteVO();
        vo.setId(n.getId());
        vo.setUid(n.getUid());
        vo.setContent(n.getContent());
        vo.setTags(splitTags(n.getTags()).stream().toList());
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

    /** 剥离 content 末尾的 #标签 块，保持备份原文纯净。 */
    static String stripTrailingTagBlock(String content) {
        if (content == null || content.isBlank()) {
            return content == null ? "" : content;
        }
        Matcher m = TRAILING_TAG_BLOCK.matcher(content);
        if (m.find()) {
            String stripped = m.replaceFirst("");
            return stripped.isBlank() ? "" : stripped;
        }
        return content;
    }

    /** 解析 LLM 输出为标签列表（按标点/空白分隔，去 #、去空、去重）。 */
    List<String> parseTagsFromText(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        // 兼容纯 JSON 数组输出（如 ["a","b"]）
        String cleaned = text.trim();
        if (cleaned.startsWith("[") && cleaned.endsWith("]")) {
            try {
                List<String> arr = objectMapper.readValue(cleaned,
                        new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {
                        });
                return arr.stream().map(MemosService::sanitizeTag).filter(StringUtils::hasText)
                        .distinct().limit(8).toList();
            } catch (Exception ignored) {
                // 非 JSON 按分隔符解析
            }
        }
        return Arrays.stream(cleaned.split("[,，、;；\\n\\r\\t ]+"))
                .map(MemosService::sanitizeTag)
                .filter(StringUtils::hasText)
                .distinct()
                .limit(8)
                .toList();
    }

    /** 标签规范化：去 # 前缀、内部空白转下划线、截断。 */
    static String sanitizeTag(String tag) {
        if (tag == null) {
            return "";
        }
        String t = tag.trim().replaceAll("^#+", "").trim();
        if (t.isEmpty()) {
            return "";
        }
        t = t.replaceAll("[\\s]+", "_");
        return t.length() > 30 ? t.substring(0, 30) : t;
    }

    /** tags 逗号串 → 有序去重集合。 */
    static Set<String> splitTags(String tags) {
        if (!StringUtils.hasText(tags)) {
            return new LinkedHashSet<>();
        }
        return Arrays.stream(tags.split(","))
                .map(MemosService::sanitizeTag)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    static String joinTags(java.util.Collection<String> tags) {
        return tags.stream().map(MemosService::sanitizeTag).filter(StringUtils::hasText)
                .distinct().collect(Collectors.joining(","));
    }

    /** 收集 content 中已有的 #标签（去 # 规范化。）。 */
    private Set<String> collectTagsInContent(String content) {
        Set<String> tags = new HashSet<>();
        if (!StringUtils.hasText(content)) {
            return tags;
        }
        Matcher m = Pattern.compile("#([^\\s#]+)").matcher(content);
        while (m.find()) {
            String t = sanitizeTag(m.group(1));
            if (!t.isEmpty()) {
                tags.add(t);
            }
        }
        return tags;
    }

    /** 在原文末尾追加 #标签（不重复列上已存在的）。 */
    static String buildContentWithTags(String content, List<String> tags) {
        StringBuilder sb = new StringBuilder();
        for (String t : tags) {
            if (t.startsWith("#")) {
                sb.append(t).append(' ');
            } else {
                sb.append('#').append(t).append(' ');
            }
        }
        String block = sb.toString().trim();
        if (block.isEmpty()) {
            return content == null ? "" : content;
        }
        if (content == null || content.isBlank()) {
            return block;
        }
        return content + (content.endsWith("\n") ? "" : "\n\n") + block;
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
