package com.stellar.ai.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.ai.entity.AiChatMessage;
import com.stellar.ai.entity.AiChatSession;
import com.stellar.ai.mapper.AiChatMessageMapper;
import com.stellar.ai.mapper.AiChatSessionMapper;
import com.stellar.ai.mapper.AiPersonaMapper;
import com.stellar.ai.service.AiChatService;
import com.stellar.ai.service.AiChatSessionService;
import com.stellar.ai.service.AiChatToolService;
import com.stellar.ai.service.AiKnowledgeService;
import com.stellar.ai.service.AiMemoryService;
import com.stellar.common.BusinessException;
import com.stellar.system.entity.SysUser;
import com.stellar.system.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link AiChatSessionService} 单测：构造注入 8 个协作者，覆盖会话/消息 CRUD 的归属校验
 * （checkOwnership：不存在/无权）、列表与分页联查用户名、批量删除、以及 streamChat 的消息空
 * 前置校验与登录分支（SseEmitter 返回 + user 消息落库）。currentSubject 用 MockedStatic 模拟登录态。
 */
@ExtendWith(MockitoExtension.class)
class AiChatSessionServiceTest {

    @Mock
    AiChatSessionMapper sessionMapper;
    @Mock
    AiChatMessageMapper messageMapper;
    @Mock
    AiPersonaMapper personaMapper;
    @Mock
    SysUserMapper userMapper;
    @Mock
    AiKnowledgeService knowledgeService;
    @Mock
    AiMemoryService memoryService;
    @Mock
    AiChatService aiChatService;
    @Mock
    AiChatToolService aiChatToolService;

    AiChatSessionService service;

    @BeforeEach
    void setup() {
        service = new AiChatSessionService(sessionMapper, messageMapper, personaMapper, userMapper,
                knowledgeService, memoryService, aiChatService, aiChatToolService);
    }

    private AiChatSession accountSession(Long id, String subjectId) {
        AiChatSession s = new AiChatSession();
        s.setId(id);
        s.setSubjectType("account");
        s.setSubjectId(subjectId);
        return s;
    }

    private MockedStatic<StpUtil> login(String uid) {
        MockedStatic<StpUtil> stp = mockStatic(StpUtil.class);
        stp.when(StpUtil::isLogin).thenReturn(true);
        stp.when(StpUtil::getLoginIdAsString).thenReturn(uid);
        return stp;
    }

    // ===== createSession =====

    @Test
    void createSession_登录_插入并归属账号() {
        try (MockedStatic<StpUtil> stp = login("u1")) {
            AiChatSession s = service.createSession(1L, 2L, "我的对话");
            assertNotNull(s);
            verify(sessionMapper).insert(any(AiChatSession.class));
        }
    }

    @Test
    void createSession_游客_强制无知识库() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(false);
            AiChatSession s = service.createSession(1L, 2L, "x");
            assertNotNull(s);
            // 游客 kbId 被强制置空
            ArgumentCaptor<AiChatSession> cap = ArgumentCaptor.forClass(AiChatSession.class);
            verify(sessionMapper).insert(cap.capture());
            assertNull(cap.getValue().getKbId());
        }
    }

    // ===== listMySessions / pageAllSessions =====

    @Test
    void listMySessions_正常() {
        try (MockedStatic<StpUtil> stp = login("u1")) {
            when(sessionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(accountSession(1L, "u1")));
            List<AiChatSession> r = service.listMySessions();
            assertEquals(1, r.size());
        }
    }

    @Test
    void pageAllSessions_联查用户名() {
        AiChatSession s = accountSession(1L, "7");
        s.setTitle("t");
        Page<AiChatSession> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(s));
        when(sessionMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);
        SysUser u = new SysUser();
        u.setId(7L);
        u.setUsername("alice");
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(u));

        Page<Map<String, Object>> r = service.pageAllSessions(1, 10);
        assertEquals(1, r.getRecords().size());
        assertEquals("alice", r.getRecords().get(0).get("username"));
    }

    // ===== getMessages / checkOwnership =====

    @Test
    void getMessages_会话不存在_抛() {
        try (MockedStatic<StpUtil> stp = login("u1")) {
            when(sessionMapper.selectById(1L)).thenReturn(null);
            assertThrows(BusinessException.class, () -> service.getMessages(1L));
        }
    }

    @Test
    void getMessages_无权_抛() {
        try (MockedStatic<StpUtil> stp = login("u1")) {
            AiChatSession s = new AiChatSession();
            s.setSubjectType("ip");
            s.setSubjectId("1.2.3.4");
            when(sessionMapper.selectById(1L)).thenReturn(s);
            assertThrows(BusinessException.class, () -> service.getMessages(1L));
        }
    }

    @Test
    void getMessages_正常() {
        try (MockedStatic<StpUtil> stp = login("u1")) {
            when(sessionMapper.selectById(1L)).thenReturn(accountSession(1L, "u1"));
            when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(new AiChatMessage()));
            List<AiChatMessage> r = service.getMessages(1L);
            assertEquals(1, r.size());
        }
    }

    @Test
    void getMessagesAdmin_无归属校验() {
        when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(new AiChatMessage()));
        List<AiChatMessage> r = service.getMessagesAdmin(1L);
        assertEquals(1, r.size());
    }

    // ===== updateSession / deleteSession =====

    @Test
    void updateSession_正常_更新标题() {
        try (MockedStatic<StpUtil> stp = login("u1")) {
            AiChatSession s = accountSession(1L, "u1");
            s.setTitle("旧");
            when(sessionMapper.selectById(1L)).thenReturn(s);
            service.updateSession(1L, "新标题");
            verify(sessionMapper).updateById((AiChatSession) any());
        }
    }

    @Test
    void deleteSession_正常_级联删消息() {
        try (MockedStatic<StpUtil> stp = login("u1")) {
            when(sessionMapper.selectById(1L)).thenReturn(accountSession(1L, "u1"));
            service.deleteSession(1L);
            verify(messageMapper).delete(any(LambdaQueryWrapper.class));
            verify(sessionMapper).deleteById(1L);
        }
    }

    @Test
    void deleteSessionAdmin_无归属校验() {
        service.deleteSessionAdmin(1L);
        verify(messageMapper).delete(any(LambdaQueryWrapper.class));
        verify(sessionMapper).deleteById(1L);
    }

    // ===== deleteMySessions =====

    @Test
    void deleteMySessions_空_返回0() {
        try (MockedStatic<StpUtil> stp = login("u1")) {
            when(sessionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
            assertEquals(0, service.deleteMySessions());
        }
    }

    @Test
    void deleteMySessions_非空_批量删() {
        try (MockedStatic<StpUtil> stp = login("u1")) {
            when(sessionMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(accountSession(1L, "u1"), accountSession(2L, "u1")));
            assertEquals(2, service.deleteMySessions());
            verify(sessionMapper).deleteBatchIds(List.of(1L, 2L));
        }
    }

    // ===== streamChat =====

    @Test
    void streamChat_消息空_抛() {
        try (MockedStatic<StpUtil> stp = login("u1")) {
            when(sessionMapper.selectById(1L)).thenReturn(accountSession(1L, "u1"));
            assertThrows(BusinessException.class, () -> service.streamChat(1L, "  ", 1L, null));
        }
    }

    @Test
    void streamChat_登录正常_返回Emitter并落user消息() {
        // 用数字型登录 id 以便 buildSystemText 的「长期记忆注入」分支（parseLong 成功）被覆盖
        try (MockedStatic<StpUtil> stp = login("1")) {
            when(sessionMapper.selectById(1L)).thenReturn(accountSession(1L, "1"));
            when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
            when(memoryService.listByUser(anyLong())).thenReturn(List.of());
            SseEmitter emitter = mock(SseEmitter.class);
            when(aiChatService.streamMultiChatWithTools(any(), any(), any(), any(), any())).thenReturn(emitter);

            SseEmitter r = service.streamChat(1L, "你好", 1L, null);
            assertNotNull(r);
            ArgumentCaptor<AiChatMessage> mcap = ArgumentCaptor.forClass(AiChatMessage.class);
            verify(messageMapper).insert(mcap.capture());
            assertEquals("user", mcap.getValue().getRole());
            verify(aiChatService).streamMultiChatWithTools(any(), any(), any(), any(), any());
        }
    }
}
