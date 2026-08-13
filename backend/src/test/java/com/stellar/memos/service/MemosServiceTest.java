package com.stellar.memos.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.ai.service.AiChatService;
import com.stellar.common.BusinessException;
import com.stellar.infra.ExternalCallLogger;
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
import com.stellar.memos.vo.MemosWebhookConfigVO;
import com.stellar.system.service.SysSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@link MemosService} 单测：Mock 掉 MemosApiClient/Mapper/LLM/设置，覆盖
 * 同步合并语义（新插/更新/恢复/标记删除/保留待写回标签）、AI 勾选打标签+自动写回、
 * 标签写回去重与兜底、查询统计、配置保存与校验、静态 helper。
 */
@ExtendWith(MockitoExtension.class)
class MemosServiceTest {

    @Mock
    private MemosNoteMapper memosNoteMapper;
    @Mock
    private MemosApiClient memosApiClient;
    @Mock
    private SysSettingService sysSettingService;
    @Mock
    private AiChatService aiChatService;
    @Mock
    private ExternalCallLogger externalCallLogger;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;
    @Mock
    private MemosRagService memosRagService;

    private MemosService service;

    @BeforeEach
    void setUp() {
        service = new MemosService(memosNoteMapper, memosApiClient, sysSettingService,
                aiChatService, externalCallLogger, new ObjectMapper(), redisTemplate, memosRagService);
    }

    private void mockConfig(String baseUrl, String token) {
        when(sysSettingService.get(MemosService.KEY_BASE_URL, "")).thenReturn(baseUrl);
        when(sysSettingService.get(MemosService.KEY_TOKEN, "")).thenReturn(token);
    }

    private MemosApiClient.MemosRemoteMemo memo(String uid, String content, String tags) {
        return new MemosApiClient.MemosRemoteMemo(uid, content,
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2025, 1, 2, 0, 0),
                tags.isEmpty() ? List.of() : List.of(tags.split(",")));
    }

    static MemosApiClient.MemosRemoteMemo remoteMemo(String uid, String content, String tags) {
        return new MemosApiClient.MemosRemoteMemo(uid, content,
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2025, 1, 2, 0, 0),
                tags.isEmpty() ? List.of() : List.of(tags.split(",")));
    }

    /** 按 Memos 端算法生成签名：whsec_ 密钥 base64 解码后 HMAC-SHA256(id.timestamp.body)。 */
    static String sign(String id, String ts, String body, String secret) {
        try {
            byte[] key = secret.startsWith("whsec_")
                    ? Base64.getDecoder().decode(secret.substring("whsec_".length()))
                    : secret.getBytes(StandardCharsets.UTF_8);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return "v1," + Base64.getEncoder().encodeToString(
                    mac.doFinal((id + "." + ts + "." + body).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String nowTs() {
        return String.valueOf(System.currentTimeMillis() / 1000);
    }

    /** 预置 webhook 签名密钥已配置。 */
    private void mockWebhookSecret(String secret) {
        when(sysSettingService.get(MemosService.KEY_WEBHOOK_SECRET, "")).thenReturn(secret);
    }

    /** 预置 Redis 去重通过（首次投递）。 */
    private void mockRedisOk() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(true);
    }

    // ===== 配置 =====

    @Test
    void getConfig_回显与脱敏() {
        mockConfig("https://memo.booksy.cf", "secret-token");
        when(sysSettingService.get(MemosService.KEY_PROMPT, MemosService.DEFAULT_PROMPT))
                .thenReturn("自定义模板 {{content}}");

        MemosConfigVO vo = service.getConfig();

        assertEquals("https://memo.booksy.cf", vo.getBaseUrl());
        assertTrue(vo.getTokenConfigured());
        assertEquals("自定义模板 {{content}}", vo.getPromptTemplate());
    }

    @Test
    void getConfig_token未配置_置false() {
        mockConfig("https://memo.booksy.cf", "");
        MemosConfigVO vo = service.getConfig();
        assertFalse(vo.getTokenConfigured());
    }

    @Test
    void saveConfig_规范化域名_token空保留_prompt更新() {
        java.net.InetAddress publicIp = null;
        try {
            publicIp = java.net.InetAddress.getByAddress("h", new byte[]{8, 8, 8, 8});
        } catch (Exception e) {
            fail(e);
        }
        java.net.InetAddress finalIp = publicIp;
        try (var inet = mockStatic(java.net.InetAddress.class)) {
            inet.when(() -> java.net.InetAddress.getAllByName(anyString()))
                    .thenReturn(new java.net.InetAddress[]{finalIp});

            MemosConfigDTO dto = new MemosConfigDTO();
            dto.setBaseUrl("https://memo.booksy.cf///");
            dto.setToken("  ");
            dto.setPromptTemplate("新模板 {{content}}");
            service.saveConfig(dto);
        }

        verify(sysSettingService).set(eq(MemosService.KEY_BASE_URL),
                eq("https://memo.booksy.cf"), eq(null));
        verify(sysSettingService, never()).set(eq(MemosService.KEY_TOKEN), any(), any());
        verify(sysSettingService).set(eq(MemosService.KEY_PROMPT), eq("新模板 {{content}}"), eq(null));
    }

    @Test
    void loadConfig_未配置_抛异常() {
        mockConfig("", "");
        assertThrows(BusinessException.class, service::syncPull);
    }

    // ===== 立即同步 =====

    @Test
    void syncPull_新笔记_剥离标签块入库() {
        mockConfig("https://memo.booksy.cf", "tok");
        when(memosApiClient.listAllMemos("https://memo.booksy.cf", "tok"))
                .thenReturn(List.of(memo("u1", "hello\n\n#world #note", "world,note")));
        when(memosNoteMapper.selectList(any())).thenReturn(List.of());

        MemosSyncResultVO result = service.syncPull();

        ArgumentCaptor<MemosNote> captor = ArgumentCaptor.forClass(MemosNote.class);
        verify(memosNoteMapper).insert(captor.capture());
        MemosNote saved = captor.getValue();
        assertEquals("u1", saved.getUid());
        assertEquals("hello", saved.getContent());
        assertEquals("world,note", saved.getTags());
        assertEquals(1, saved.getTagsSynced());
        assertEquals(0, saved.getRemoteDeleted());
        assertEquals(1, result.getFetched());
        assertEquals(1, result.getCreated());
        assertEquals(0, result.getMarkedDeleted());
    }

    @Test
    void syncPull_远端已删标记_已删不重复处理() {
        mockConfig("https://memo.booksy.cf", "tok");
        when(memosApiClient.listAllMemos(anyString(), anyString()))
                .thenReturn(List.of(memo("u1", "still alive", "")));
        MemosNote gone = new MemosNote();
        gone.setId(1L);
        gone.setUid("gone");
        gone.setContent("old");
        gone.setRemoteDeleted(0);
        MemosNote alreadyDeleted = new MemosNote();
        alreadyDeleted.setId(2L);
        alreadyDeleted.setUid("del");
        alreadyDeleted.setRemoteDeleted(1);
        when(memosNoteMapper.selectList(any())).thenReturn(List.of(gone, alreadyDeleted));

        MemosSyncResultVO result = service.syncPull();

        assertEquals(1, result.getMarkedDeleted());
        assertEquals(1, gone.getRemoteDeleted());
        verify(memosNoteMapper, times(1)).updateById(gone);
        verify(memosNoteMapper, never()).updateById(alreadyDeleted);
    }

    @Test
    void syncPull_恢复被标记删除_远端存活() {
        mockConfig("https://memo.booksy.cf", "tok");
        MemosApiClient.MemosRemoteMemo rm = memo("u1", "back", "");
        when(memosApiClient.listAllMemos(anyString(), anyString())).thenReturn(List.of(rm));
        MemosNote local = new MemosNote();
        local.setId(1L);
        local.setUid("u1");
        local.setContent("back");
        local.setTags("");
        local.setTagsSynced(1);
        local.setRemoteDeleted(1);
        local.setRemoteUpdateTime(LocalDateTime.of(2025, 1, 2, 0, 0));
        when(memosNoteMapper.selectList(any())).thenReturn(List.of(local));

        service.syncPull();

        assertEquals(0, local.getRemoteDeleted());
        assertEquals(1, local.getTagsSynced());
        verify(memosNoteMapper).updateById(local);
    }

    @Test
    void syncPull_本地待写回标签_远端已有则同步_远端无则保留pending() {
        mockConfig("https://memo.booksy.cf", "tok");
        // 笔记A：远端已含全部标签 → tags_synced 置 1
        MemosApiClient.MemosRemoteMemo rmA = memo("a", "txt #t1", "t1");
        // 笔记B：远端无本地标签 → 保留 tags_synced=0
        MemosApiClient.MemosRemoteMemo rmB = memo("b", "txt", "t2");
        when(memosApiClient.listAllMemos(anyString(), anyString())).thenReturn(List.of(rmA, rmB));
        MemosNote localA = new MemosNote();
        localA.setId(1L);
        localA.setUid("a");
        localA.setContent("txt");
        localA.setTags("t1");
        localA.setTagsSynced(0);
        localA.setRemoteDeleted(0);
        localA.setRemoteUpdateTime(LocalDateTime.of(2025, 1, 2, 0, 0));
        MemosNote localB = new MemosNote();
        localB.setId(2L);
        localB.setUid("b");
        localB.setContent("txt");
        localB.setTags("t_local");
        localB.setTagsSynced(0);
        localB.setRemoteDeleted(0);
        localB.setRemoteUpdateTime(LocalDateTime.of(2025, 1, 2, 0, 0));
        when(memosNoteMapper.selectList(any())).thenReturn(List.of(localA, localB));

        service.syncPull();

        // 笔记A 无变化：不触发更新，tagsSynced 保持原值（真实流程此前已置 1）
        verify(memosNoteMapper, never()).updateById(localA);
        assertEquals("t1", localA.getTags());
        assertEquals(0, localB.getTagsSynced());
        assertEquals("t2,t_local", localB.getTags());
    }

    @Test
    void syncPull_远端拉取失败_异常上抛() {
        mockConfig("https://memo.booksy.cf", "tok");
        when(memosApiClient.listAllMemos(anyString(), anyString()))
                .thenThrow(new BusinessException("HTTP 401"));
        assertThrows(BusinessException.class, service::syncPull);
    }

    // ===== AI 打标签（勾选 + 自动写回） =====

    @Test
    void aiTag_勾选成功_合并标签_自动写回() {
        mockConfig("https://memo.booksy.cf", "tok");
        when(sysSettingService.get(MemosService.KEY_PROMPT, "")).thenReturn("");
        MemosNote n1 = new MemosNote();
        n1.setId(1L);
        n1.setUid("u1");
        n1.setContent("关于 spring boot 的文章");
        n1.setRemoteDeleted(0);
        MemosNote n2 = new MemosNote();
        n2.setId(2L);
        n2.setUid("u2");
        n2.setContent("读书笔记");
        n2.setRemoteDeleted(0);
        when(memosNoteMapper.selectBatchIds(any())).thenReturn(List.of(n1, n2));
        when(aiChatService.chatCompletionWithMessages(any(), eq(5L)))
                .thenReturn("编程、后端")
                .thenReturn("读书");

        MemosJobResultVO result = service.aiTag(List.of(1L, 2L), 5L);

        assertEquals(2, result.getProcessed());
        assertEquals(2, result.getSuccess());
        assertEquals(0, result.getFailed());
        assertEquals(2, result.getPushSuccess());
        assertEquals(0, result.getPushFailed());
        assertEquals("编程,后端", n1.getTags());
        assertEquals(1, n1.getTagsSynced());
        verify(aiChatService, times(2)).chatCompletionWithMessages(any(), eq(5L));
        verify(memosApiClient).updateContent("https://memo.booksy.cf", "tok", "u1",
                "关于 spring boot 的文章\n\n#编程 #后端");
        verify(memosApiClient).updateContent("https://memo.booksy.cf", "tok", "u2", "读书笔记\n\n#读书");
        // 每条笔记打标一次 + 写回一次
        verify(memosNoteMapper, times(4)).updateById(any(MemosNote.class));
        verify(externalCallLogger, times(2)).success(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void aiTag_已打标笔记重新打标_标签合并去重() {
        mockConfig("https://memo.booksy.cf", "tok");
        when(sysSettingService.get(MemosService.KEY_PROMPT, "")).thenReturn("");
        MemosNote n = new MemosNote();
        n.setId(1L);
        n.setUid("u1");
        n.setContent("内容 #old #dup");
        n.setRemoteDeleted(0);
        n.setTags("old,dup");
        when(memosNoteMapper.selectBatchIds(any())).thenReturn(List.of(n));
        when(aiChatService.chatCompletionWithMessages(any(), eq(null))).thenReturn("new,dup");

        MemosJobResultVO result = service.aiTag(List.of(1L), null);

        assertEquals("old,dup,new", n.getTags());
        assertEquals(1, result.getSuccess());
        assertEquals(1, result.getPushSuccess());
        verify(memosApiClient).updateContent("https://memo.booksy.cf", "tok", "u1", "内容 #old #dup\n\n#new");
    }

    @Test
    void aiTag_自动写回失败_保持待写回() {
        mockConfig("https://memo.booksy.cf", "tok");
        when(sysSettingService.get(MemosService.KEY_PROMPT, "")).thenReturn("");
        MemosNote n = new MemosNote();
        n.setId(1L);
        n.setUid("u1");
        n.setContent("内容");
        n.setRemoteDeleted(0);
        when(memosNoteMapper.selectBatchIds(any())).thenReturn(List.of(n));
        when(aiChatService.chatCompletionWithMessages(any(), eq(null))).thenReturn("标签a");
        doThrow(new BusinessException("HTTP 500")).when(memosApiClient)
                .updateContent(anyString(), anyString(), anyString(), anyString());

        MemosJobResultVO result = service.aiTag(List.of(1L), null);

        assertEquals(1, result.getSuccess());
        assertEquals(1, result.getPushFailed());
        assertEquals(0, result.getPushSuccess());
        assertEquals(0, n.getTagsSynced());
    }

    @Test
    void aiTag_远端已删笔记_跳过不打标() {
        mockConfig("https://memo.booksy.cf", "tok");
        when(sysSettingService.get(MemosService.KEY_PROMPT, "")).thenReturn("");
        MemosNote n = new MemosNote();
        n.setId(1L);
        n.setUid("u1");
        n.setContent("内容");
        n.setRemoteDeleted(1);
        when(memosNoteMapper.selectBatchIds(any())).thenReturn(List.of(n));

        MemosJobResultVO result = service.aiTag(List.of(1L), null);

        assertEquals(1, result.getSkipped());
        assertEquals(0, result.getSuccess());
        verify(aiChatService, never()).chatCompletionWithMessages(anyList(), any());
    }

    @Test
    void aiTag_LLM输出为空_记失败_不更新() {
        mockConfig("https://memo.booksy.cf", "tok");
        when(sysSettingService.get(MemosService.KEY_PROMPT, "")).thenReturn("");
        MemosNote n = new MemosNote();
        n.setId(1L);
        n.setUid("u1");
        n.setContent("内容");
        n.setRemoteDeleted(0);
        when(memosNoteMapper.selectBatchIds(any())).thenReturn(List.of(n));
        when(aiChatService.chatCompletionWithMessages(any(), eq(null))).thenReturn("  ");

        MemosJobResultVO result = service.aiTag(List.of(1L), null);

        assertEquals(1, result.getFailed());
        verify(memosNoteMapper, never()).updateById(any(MemosNote.class));
        verify(memosApiClient, never()).updateContent(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void aiTag_LLM异常_记失败() {
        mockConfig("https://memo.booksy.cf", "tok");
        when(sysSettingService.get(MemosService.KEY_PROMPT, "")).thenReturn("");
        MemosNote n = new MemosNote();
        n.setId(1L);
        n.setUid("u1");
        n.setContent("内容");
        n.setRemoteDeleted(0);
        when(memosNoteMapper.selectBatchIds(any())).thenReturn(List.of(n));
        when(aiChatService.chatCompletionWithMessages(any(), eq(null)))
                .thenThrow(new RuntimeException("llm down"));

        MemosJobResultVO result = service.aiTag(List.of(1L), null);

        assertEquals(1, result.getFailed());
    }

    // ===== 标签写回 =====

    @Test
    void pushTags_写回成功_置已同步() {
        mockConfig("https://memo.booksy.cf", "tok");
        MemosNote n = new MemosNote();
        n.setId(1L);
        n.setUid("u1");
        n.setContent("正文");
        n.setTags("a,b");
        n.setTagsSynced(0);
        when(memosNoteMapper.selectList(any())).thenReturn(List.of(n));

        MemosJobResultVO result = service.pushTags();

        verify(memosApiClient).updateContent("https://memo.booksy.cf", "tok", "u1", "正文\n\n#a #b");
        assertEquals(1, result.getSuccess());
        assertEquals(1, n.getTagsSynced());
    }

    @Test
    void pushTags_标签已在content_跳过并视为同步() {
        mockConfig("https://memo.booksy.cf", "tok");
        MemosNote n = new MemosNote();
        n.setId(1L);
        n.setUid("u1");
        n.setContent("正文 #a");
        n.setTags("a");
        n.setTagsSynced(0);
        when(memosNoteMapper.selectList(any())).thenReturn(List.of(n));

        MemosJobResultVO result = service.pushTags();

        verify(memosApiClient, never()).updateContent(anyString(), anyString(), anyString(), anyString());
        assertEquals(1, result.getSkipped());
        assertEquals(1, n.getTagsSynced());
    }

    @Test
    void pushTags_写回失败_保持待同步() {
        mockConfig("https://memo.booksy.cf", "tok");
        MemosNote n = new MemosNote();
        n.setId(1L);
        n.setUid("u1");
        n.setContent("正文");
        n.setTags("a");
        n.setTagsSynced(0);
        when(memosNoteMapper.selectList(any())).thenReturn(List.of(n));
        doThrow(new BusinessException("HTTP 500")).when(memosApiClient)
                .updateContent(anyString(), anyString(), anyString(), anyString());

        MemosJobResultVO result = service.pushTags();

        assertEquals(1, result.getFailed());
        assertEquals(0, n.getTagsSynced());
    }

    @Test
    void pushTags_无标签笔记_跳过() {
        mockConfig("https://memo.booksy.cf", "tok");
        MemosNote n = new MemosNote();
        n.setId(1L);
        n.setUid("u1");
        n.setContent("正文");
        n.setTags("");
        n.setTagsSynced(0);
        when(memosNoteMapper.selectList(any())).thenReturn(List.of(n));

        MemosJobResultVO result = service.pushTags();

        assertEquals(1, result.getSkipped());
        verify(memosApiClient, never()).updateContent(anyString(), anyString(), anyString(), anyString());
    }

    // ===== 查询 =====

    @Test
    void page_映射VO_标签拆list() {
        MemosNote n = new MemosNote();
        n.setId(1L);
        n.setUid("u1");
        n.setContent("c");
        n.setTags("a,b");
        n.setTagsSynced(0);
        n.setRemoteDeleted(0);
        Page<MemosNote> p = new Page<>(1, 10, 1);
        p.setRecords(List.of(n));
        when(memosNoteMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(p);

        MemosQueryDTO q = new MemosQueryDTO();
        q.setPageNum(1);
        q.setPageSize(10);
        q.setKeyword("c");
        q.setRemoteDeleted(0);
        Page<MemosNoteVO> vo = service.page(q);

        assertEquals(1, vo.getRecords().size());
        assertEquals(List.of("a", "b"), vo.getRecords().get(0).getTags());
        assertEquals(1, vo.getTotal());
    }

    @Test
    void stats_汇总计数() {
        when(memosNoteMapper.selectCount(null)).thenReturn(10L);
        when(memosNoteMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(6L, 3L, 2L, 1L);

        MemosStatsVO vo = service.stats();

        assertEquals(10L, vo.getTotal());
        assertEquals(6L, vo.getActive());
        assertEquals(3L, vo.getDeleted());
        assertEquals(2L, vo.getUntagged());
        assertEquals(1L, vo.getPendingPush());
    }

    // ===== Webhook =====

    @Test
    void getWebhookConfig_密钥未配置_置false() {
        when(sysSettingService.get(MemosService.KEY_WEBHOOK_SECRET, "")).thenReturn("");
        MemosWebhookConfigVO vo = service.getWebhookConfig();
        assertFalse(vo.getSecretConfigured());
    }

    @Test
    void getWebhookConfig_密钥已配置_置true() {
        when(sysSettingService.get(MemosService.KEY_WEBHOOK_SECRET, "")).thenReturn("whsec_abc");
        MemosWebhookConfigVO vo = service.getWebhookConfig();
        assertTrue(vo.getSecretConfigured());
    }

    @Test
    void saveWebhookSecret_保存_空不保存() {
        service.saveWebhookSecret("whsec_abc");
        verify(sysSettingService).set(MemosService.KEY_WEBHOOK_SECRET, "whsec_abc", null);
        service.saveWebhookSecret("  ");
        verify(sysSettingService, never()).set(MemosService.KEY_WEBHOOK_SECRET, "  ", null);
    }

    @Test
    void handleWebhook_签名密钥未配置_抛异常() {
        when(sysSettingService.get(MemosService.KEY_WEBHOOK_SECRET, "")).thenReturn("");
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        assertThrows(BusinessException.class, () -> service.handleWebhook(body, "msg_1", nowTs(), "v1,x"));
    }

    @Test
    void handleWebhook_签名不匹配_抛异常() {
        String secret = "whsec_" + Base64.getEncoder().encodeToString("key".getBytes(StandardCharsets.UTF_8));
        mockWebhookSecret(secret);
        byte[] body = "{\"activityType\":\"memos.memo.created\"}".getBytes(StandardCharsets.UTF_8);
        assertThrows(BusinessException.class,
                () -> service.handleWebhook(body, "msg_1", nowTs(), "v1,forged"));
    }

    @Test
    void handleWebhook_时间戳过期_抛异常() {
        String secret = "whsec_" + Base64.getEncoder().encodeToString("key".getBytes(StandardCharsets.UTF_8));
        mockWebhookSecret(secret);
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        String oldTs = String.valueOf(System.currentTimeMillis() / 1000 - 600);
        String sig = sign("msg_1", oldTs, "{}", secret);
        assertThrows(BusinessException.class,
                () -> service.handleWebhook(body, "msg_1", oldTs, sig));
    }

    @Test
    void handleWebhook_重复id_忽略() {
        String secret = "whsec_" + Base64.getEncoder().encodeToString("key".getBytes(StandardCharsets.UTF_8));
        mockWebhookSecret(secret);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(false);
        byte[] body = "{\"activityType\":\"memos.memo.created\"}".getBytes(StandardCharsets.UTF_8);
        String ts = nowTs();
        String sig = sign("msg_dup", ts, new String(body, StandardCharsets.UTF_8), secret);

        Map<String, Object> result = service.handleWebhook(body, "msg_dup", ts, sig);

        assertEquals("duplicate", result.get("status"));
        verify(memosNoteMapper, never()).insert(any(MemosNote.class));
    }

    @Test
    void handleWebhook_created_新笔记入库() {
        String secret = "whsec_" + Base64.getEncoder().encodeToString("key".getBytes(StandardCharsets.UTF_8));
        mockWebhookSecret(secret);
        mockRedisOk();
        String payload = "{\"activityType\":\"memos.memo.created\",\"creator\":\"users/1\","
                + "\"memo\":{\"name\":\"memos/u1\",\"uid\":\"u1\",\"content\":\"hello\","
                + "\"createTime\":\"2025-01-01T00:00:00Z\",\"updateTime\":\"2025-01-02T00:00:00Z\","
                + "\"property\":{\"tags\":[\"a\"]}}}";
        when(memosApiClient.parseMemo(any())).thenReturn(remoteMemo("u1", "hello", "a"));
        when(memosNoteMapper.selectOne(any())).thenReturn(null);
        String ts = nowTs();
        String sig = sign("msg_c", ts, payload, secret);

        Map<String, Object> result = service.handleWebhook(payload.getBytes(StandardCharsets.UTF_8), "msg_c", ts, sig);

        assertEquals("created", result.get("status"));
        ArgumentCaptor<MemosNote> captor = ArgumentCaptor.forClass(MemosNote.class);
        verify(memosNoteMapper).insert(captor.capture());
        assertEquals("u1", captor.getValue().getUid());
        assertEquals("hello", captor.getValue().getContent());
        assertEquals("a", captor.getValue().getTags());
        assertEquals(1, captor.getValue().getTagsSynced());
        assertEquals(0, captor.getValue().getRemoteDeleted());
    }

    @Test
    void handleWebhook_updated_合并本地待写回标签() {
        String secret = "whsec_" + Base64.getEncoder().encodeToString("key".getBytes(StandardCharsets.UTF_8));
        mockWebhookSecret(secret);
        mockRedisOk();
        String payload = "{\"activityType\":\"memos.memo.updated\",\"creator\":\"users/1\","
                + "\"memo\":{\"name\":\"memos/u1\",\"uid\":\"u1\",\"content\":\"new content\","
                + "\"createTime\":\"2025-01-01T00:00:00Z\",\"updateTime\":\"2025-01-03T00:00:00Z\","
                + "\"property\":{\"tags\":[\"remote\"]}}}";
        when(memosApiClient.parseMemo(any())).thenReturn(remoteMemo("u1", "new content", "remote"));
        MemosNote local = new MemosNote();
        local.setId(1L);
        local.setUid("u1");
        local.setContent("old content");
        local.setTags("ai_local");
        local.setTagsSynced(0);
        local.setRemoteDeleted(0);
        local.setRemoteUpdateTime(LocalDateTime.of(2025, 1, 2, 0, 0));
        when(memosNoteMapper.selectOne(any())).thenReturn(local);
        String ts = nowTs();
        String sig = sign("msg_u", ts, payload, secret);

        Map<String, Object> result = service.handleWebhook(payload.getBytes(StandardCharsets.UTF_8), "msg_u", ts, sig);

        assertEquals("updated", result.get("status"));
        assertEquals("new content", local.getContent());
        assertEquals("remote,ai_local", local.getTags());
        assertEquals(0, local.getTagsSynced());
        verify(memosNoteMapper).updateById(local);
    }

    @Test
    void handleWebhook_deleted_标记远端删除() {
        String secret = "whsec_" + Base64.getEncoder().encodeToString("key".getBytes(StandardCharsets.UTF_8));
        mockWebhookSecret(secret);
        mockRedisOk();
        String payload = "{\"activityType\":\"memos.memo.deleted\",\"creator\":\"users/1\","
                + "\"memo\":{\"name\":\"memos/u1\",\"uid\":\"u1\",\"content\":\"gone\"}}";
        when(memosApiClient.parseMemo(any())).thenReturn(remoteMemo("u1", "gone", ""));
        MemosNote local = new MemosNote();
        local.setId(1L);
        local.setUid("u1");
        local.setContent("gone");
        local.setRemoteDeleted(0);
        when(memosNoteMapper.selectOne(any())).thenReturn(local);
        String ts = nowTs();
        String sig = sign("msg_d", ts, payload, secret);

        Map<String, Object> result = service.handleWebhook(payload.getBytes(StandardCharsets.UTF_8), "msg_d", ts, sig);

        assertEquals("marked", result.get("status"));
        assertEquals(1, local.getRemoteDeleted());
        verify(memosNoteMapper).updateById(local);
    }

    @Test
    void handleWebhook_deleted_本地无记录_不改动() {
        String secret = "whsec_" + Base64.getEncoder().encodeToString("key".getBytes(StandardCharsets.UTF_8));
        mockWebhookSecret(secret);
        mockRedisOk();
        String payload = "{\"activityType\":\"memos.memo.deleted\",\"creator\":\"users/1\","
                + "\"memo\":{\"name\":\"memos/u1\",\"uid\":\"u1\"}}";
        when(memosApiClient.parseMemo(any())).thenReturn(remoteMemo("u1", "", ""));
        when(memosNoteMapper.selectOne(any())).thenReturn(null);
        String ts = nowTs();
        String sig = sign("msg_d2", ts, payload, secret);

        Map<String, Object> result = service.handleWebhook(payload.getBytes(StandardCharsets.UTF_8), "msg_d2", ts, sig);

        assertEquals("unchanged", result.get("status"));
        verify(memosNoteMapper, never()).updateById(any(MemosNote.class));
    }

    @Test
    void handleWebhook_comment事件_忽略() {
        String secret = "whsec_" + Base64.getEncoder().encodeToString("key".getBytes(StandardCharsets.UTF_8));
        mockWebhookSecret(secret);
        mockRedisOk();
        String payload = "{\"activityType\":\"memos.memo.comment.created\",\"creator\":\"users/1\","
                + "\"memo\":{\"name\":\"memos/c1\",\"uid\":\"c1\"}}";
        String ts = nowTs();
        String sig = sign("msg_cm", ts, payload, secret);

        Map<String, Object> result = service.handleWebhook(payload.getBytes(StandardCharsets.UTF_8), "msg_cm", ts, sig);

        assertEquals("ignored", result.get("status"));
        verify(memosNoteMapper, never()).insert(any(MemosNote.class));
        verify(memosNoteMapper, never()).updateById(any(MemosNote.class));
    }

    @Test
    void handleWebhook_未知类型_忽略() {
        String secret = "whsec_" + Base64.getEncoder().encodeToString("key".getBytes(StandardCharsets.UTF_8));
        mockWebhookSecret(secret);
        mockRedisOk();
        String payload = "{\"activityType\":\"memos.memo.weird\",\"creator\":\"users/1\"}";
        String ts = nowTs();
        String sig = sign("msg_x", ts, payload, secret);

        Map<String, Object> result = service.handleWebhook(payload.getBytes(StandardCharsets.UTF_8), "msg_x", ts, sig);

        assertEquals("ignored", result.get("status"));
    }

    @Test
    void handleWebhook_payload非法JSON_抛异常() {
        String secret = "whsec_" + Base64.getEncoder().encodeToString("key".getBytes(StandardCharsets.UTF_8));
        mockWebhookSecret(secret);
        mockRedisOk();
        byte[] body = "{not json".getBytes(StandardCharsets.UTF_8);
        String ts = nowTs();
        String sig = sign("msg_bad", ts, "{not json", secret);
        assertThrows(BusinessException.class,
                () -> service.handleWebhook(body, "msg_bad", ts, sig));
    }

    // ===== 静态 helper =====

    @Test
    void stripTrailingTagBlock_剥离尾部标签块_无标签原样() {
        assertEquals("hello", MemosService.stripTrailingTagBlock("hello\n\n#a #b"));
        assertEquals("hello", MemosService.stripTrailingTagBlock("hello #x"));
        assertEquals("正文", MemosService.stripTrailingTagBlock("正文"));
        assertEquals("", MemosService.stripTrailingTagBlock("#onlytag"));
        assertEquals("", MemosService.stripTrailingTagBlock(null));
    }

    @Test
    void sanitizeTag_去井号去空白截断() {
        assertEquals("Foo_Bar", MemosService.sanitizeTag("#Foo Bar"));
        assertEquals("a_b", MemosService.sanitizeTag("a\tb"));
        assertEquals("", MemosService.sanitizeTag("  ##  "));
        assertEquals("", MemosService.sanitizeTag(null));
    }

    @Test
    void splitTags_joinTags_往返去重() {
        assertEquals(Set.of("a", "b"), MemosService.splitTags(" a, b ,,a"));
        assertEquals("x,y", MemosService.joinTags(List.of("x", "#y", "x")));
    }

    @Test
    void buildContentWithTags_追加块() {
        assertEquals("正文\n\n#a #b", MemosService.buildContentWithTags("正文", List.of("a", "b")));
        assertEquals("#a", MemosService.buildContentWithTags("", List.of("a")));
        assertEquals("正文", MemosService.buildContentWithTags("正文", List.of()));
    }

    @Test
    void parseTagsFromText_分隔符与JSON数组() {
        assertEquals(List.of("编程", "后端"), MemosServiceTest.parseTagsVia(service, "编程、后端"));
        assertEquals(List.of("a", "b"), MemosServiceTest.parseTagsVia(service, "[\"a\",\"b\"]"));
        assertEquals(List.of(), MemosServiceTest.parseTagsVia(service, "   "));
        assertEquals(List.of("a", "b"), MemosServiceTest.parseTagsVia(service, "a,b,a"));
    }

    static List<String> parseTagsVia(MemosService s, String text) {
        return s.parseTagsFromText(text);
    }
}
