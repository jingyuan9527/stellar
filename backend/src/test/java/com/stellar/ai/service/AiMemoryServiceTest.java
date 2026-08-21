package com.stellar.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.ai.entity.AiChatMessage;
import com.stellar.ai.entity.AiChatSession;
import com.stellar.ai.entity.AiMemory;
import com.stellar.ai.mapper.AiChatMessageMapper;
import com.stellar.ai.mapper.AiChatSessionMapper;
import com.stellar.ai.mapper.AiMemoryMapper;
import com.stellar.ai.service.AiChatService;
import com.stellar.ai.service.AiMemoryService;
import com.stellar.common.BusinessException;
import com.stellar.system.entity.SysUser;
import com.stellar.system.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link AiMemoryService} 单测：构造注入 7 个协作者，覆盖分页联查（mapWithUsername）、
 * listByUser、CRUD 校验（create/update/delete）、summarizeSession 的归属/消息校验分支、
 * doSummarize 的 LLM 成功（事实过滤 + 事务插入）与 LLM 失败降级、以及定时任务的空/异常吞没分支。
 * 事务成功路径通过 mock PlatformTransactionManager 让真实 TransactionTemplate 跑通回调。
 */
@ExtendWith(MockitoExtension.class)
class AiMemoryServiceTest {

    @Mock
    AiMemoryMapper memoryMapper;
    @Mock
    AiChatMessageMapper messageMapper;
    @Mock
    AiChatSessionMapper sessionMapper;
    @Mock
    UserService userService;
    @Mock
    AiChatService aiChatService;
    @Mock
    JdbcTemplate jdbcTemplate;
    @Mock
    PlatformTransactionManager transactionManager;

    AiMemoryService service;

    @BeforeEach
    void setup() {
        service = new AiMemoryService(memoryMapper, messageMapper, sessionMapper, userService,
                aiChatService, jdbcTemplate, transactionManager);
    }

    private AiChatSession accountSession(Long id, String subjectId) {
        AiChatSession s = new AiChatSession();
        s.setId(id);
        s.setSubjectType("account");
        s.setSubjectId(subjectId);
        return s;
    }

    private AiChatMessage msg(String role, String content) {
        AiChatMessage m = new AiChatMessage();
        m.setRole(role);
        m.setContent(content);
        return m;
    }

    // ===== 分页 / 列表 =====

    @Test
    void pageAll_正常_联查用户名() {
        AiMemory m = new AiMemory();
        m.setUserId(7L);
        m.setContent("c");
        Page<AiMemory> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(m));
        when(memoryMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);
        when(userService.getUsernameMap(any())).thenReturn(Map.of(7L, "alice"));

        var r = service.pageAll(1, 10);
        assertEquals(1, r.getRecords().size());
        assertEquals("alice", r.getRecords().get(0).get("username"));
    }

    @Test
    void pageByUser_正常() {
        AiMemory m = new AiMemory();
        m.setUserId(7L);
        m.setContent("c");
        Page<AiMemory> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(m));
        when(memoryMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);
        when(userService.getUsernameMap(any())).thenReturn(Map.of());
        var r = service.pageByUser(7L, 1, 10);
        assertEquals(1, r.getRecords().size());
    }

    @Test
    void listByUser_返回内容列表() {
        AiMemory m1 = new AiMemory();
        m1.setContent("fact1");
        AiMemory m2 = new AiMemory();
        m2.setContent("fact2");
        when(memoryMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(m1, m2));
        List<String> r = service.listByUser(7L);
        assertEquals(List.of("fact1", "fact2"), r);
    }

    // ===== CRUD 校验 =====

    @Test
    void update_不存在_抛() {
        when(memoryMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.update(1L, "x"));
    }

    @Test
    void update_正常() {
        AiMemory exist = new AiMemory();
        exist.setId(1L);
        when(memoryMapper.selectById(1L)).thenReturn(exist);
        service.update(1L, "new content");
        verify(memoryMapper).updateById(any(AiMemory.class));
    }

    @Test
    void create_用户不存在_抛() {
        when(userService.getById(9L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.create(9L, "c"));
    }

    @Test
    void create_内容空_抛() {
        when(userService.getById(9L)).thenReturn(new SysUser());
        assertThrows(BusinessException.class, () -> service.create(9L, "   "));
    }

    @Test
    void create_正常_插入() {
        when(userService.getById(9L)).thenReturn(new SysUser());
        service.create(9L, "  fact  ");
        verify(memoryMapper).insert(any(AiMemory.class));
    }

    @Test
    void delete_正常() {
        service.delete(1L);
        verify(memoryMapper).deleteById(1L);
    }

    // ===== summarizeSession 校验分支 =====

    @Test
    void summarizeSession_会话不存在_抛() {
        when(sessionMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.summarizeSession(1L));
    }

    @Test
    void summarizeSession_游客会话_抛() {
        AiChatSession s = new AiChatSession();
        s.setSubjectType("ip");
        s.setSubjectId("1.2.3.4");
        when(sessionMapper.selectById(1L)).thenReturn(s);
        assertThrows(BusinessException.class, () -> service.summarizeSession(1L));
    }

    @Test
    void summarizeSession_用户ID无效_抛() {
        when(sessionMapper.selectById(1L)).thenReturn(accountSession(1L, "abc"));
        assertThrows(BusinessException.class, () -> service.summarizeSession(1L));
    }

    @Test
    void summarizeSession_无消息_抛() {
        when(sessionMapper.selectById(1L)).thenReturn(accountSession(1L, "123"));
        when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        assertThrows(BusinessException.class, () -> service.summarizeSession(1L));
    }

    @Test
    void summarizeSession_正常_LLM返回事实_事务插入() {
        when(sessionMapper.selectById(1L)).thenReturn(accountSession(1L, "123"));
        when(messageMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(msg("user", "hi"), msg("assistant", "hello")));
        when(aiChatService.chatCompletionWithMessages(any(), any())).thenReturn("事实A\n事实B\n无\n空");
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));

        int n = service.summarizeSession(1L);
        assertEquals(2, n); // "无"/"空" 被过滤，仅 2 条事实写入
        verify(memoryMapper, times(2)).insert(any(AiMemory.class));
    }

    @Test
    void doSummarize_LLM失败_抛() {
        when(sessionMapper.selectById(1L)).thenReturn(accountSession(1L, "123"));
        when(messageMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(msg("user", "hi")));
        when(aiChatService.chatCompletionWithMessages(any(), any())).thenThrow(new RuntimeException("llm down"));
        assertThrows(BusinessException.class, () -> service.summarizeSession(1L));
    }

    // ===== 定时任务 =====

    @Test
    void summarizeScheduled_无会话_直接返回() {
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class))).thenReturn(List.of());
        service.summarizeScheduled();
        verify(sessionMapper, never()).selectById(anyLong());
    }

    @Test
    void summarizeScheduled_有会话_异常被吞() {
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class))).thenReturn(List.of(1L, 2L));
        when(sessionMapper.selectById(1L)).thenReturn(null); // 触发 summarizeSession 抛"会话不存在"
        when(sessionMapper.selectById(2L)).thenReturn(null);
        service.summarizeScheduled(); // 异常应在循环内被 catch 吞掉，不向外抛
        verify(sessionMapper, times(2)).selectById(anyLong());
    }
}
