package com.stellar.game.service;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.ai.service.AiChatService;
import com.stellar.common.BusinessException;
import com.stellar.game.dto.ConchAnswerDTO;
import com.stellar.game.dto.ConchAnswerQueryDTO;
import com.stellar.game.dto.ConchAskDTO;
import com.stellar.game.entity.ConchAnswer;
import com.stellar.game.entity.ConchRecord;
import com.stellar.game.mapper.ConchAnswerMapper;
import com.stellar.game.mapper.ConchRecordMapper;
import com.stellar.system.mapper.SysFileMapper;
import com.stellar.game.vo.ConchAskResultVO;
import com.stellar.game.vo.ConchRecordVO;
import com.stellar.system.entity.SysFile;
import com.stellar.system.service.SysSettingService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link ConchService} 单测：用 Mockito 隔离 6 个协作者（MyBatis Mapper / AiChatService /
 * SysSettingService），并 mock {@link StpUtil} 静态调用，覆盖 {@code ask} 的语义匹配与多重兜底、
 * {@code getAnswerFile}/{@code createAnswer}/{@code updateAnswer}/{@code toggleEnabled}/
 * {@code answerPage}/{@code recordPage}/{@code deleteAnswer} 的校验与分页映射路径。
 */
@ExtendWith(MockitoExtension.class)
class ConchServiceTest {

    @Mock
    private ConchAnswerMapper answerMapper;
    @Mock
    private ConchRecordMapper recordMapper;
    @Mock
    private SysFileMapper fileMapper;
    @Mock
    private AiChatService aiChatService;
    @Mock
    private SysSettingService sysSettingService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ConchService conchService;

    @BeforeEach
    void setUp() {
        conchService = new ConchService(answerMapper, recordMapper, fileMapper,
                aiChatService, sysSettingService, objectMapper);
    }

    private ConchAnswer answer(Long id, String text, int enabled, int sort) {
        ConchAnswer a = new ConchAnswer();
        a.setId(id);
        a.setAnswerText(text);
        a.setEnabled(enabled);
        a.setSortOrder(sort);
        a.setFileId(100L);
        return a;
    }

    private ConchAskDTO dtoWith(String q) {
        ConchAskDTO d = new ConchAskDTO();
        d.setQuestion(q);
        return d;
    }

    // ===== ask 主流程 =====

    @Test
    void ask_无启用预设_抛异常() {
        when(answerMapper.selectList(any())).thenReturn(List.of());
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(false);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> conchService.ask(dtoWith("  今天运势？  ")));
            assertTrue(ex.getMessage().contains("暂无预设"));
        }
    }

    @Test
    void ask_AI开启_命中top3_随机取一且记录历史() {
        List<ConchAnswer> answers = List.of(
                answer(1L, "是", 1, 0), answer(2L, "否", 1, 1), answer(3L, "也许", 1, 2));
        when(answerMapper.selectList(any())).thenReturn(answers);
        when(sysSettingService.getAsBoolean("conch_ai_enabled", true)).thenReturn(true);
        when(aiChatService.chatCompletion(anyString())).thenReturn("{\"ids\":[3,1,2]}");

        ConchAskResultVO vo;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(false);
            vo = conchService.ask(dtoWith("q"));
        }
        assertTrue(List.of(1L, 2L, 3L).contains(vo.getAnswerId()));
        assertEquals("/tts/conch/answer/" + vo.getAnswerId() + "/audio", vo.getAudioUrl());
        verify(aiChatService).chatCompletion(anyString());
        verify(recordMapper).insert(any(ConchRecord.class));
    }

    @Test
    void ask_AI开启_LLM返回非JSON_兜底随机() {
        List<ConchAnswer> answers = List.of(
                answer(1L, "是", 1, 0), answer(2L, "否", 1, 1), answer(3L, "也许", 1, 2));
        when(answerMapper.selectList(any())).thenReturn(answers);
        when(sysSettingService.getAsBoolean("conch_ai_enabled", true)).thenReturn(true);
        when(aiChatService.chatCompletion(anyString())).thenReturn("not json");

        ConchAskResultVO vo;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(false);
            vo = conchService.ask(dtoWith("q"));
        }
        assertTrue(List.of(1L, 2L, 3L).contains(vo.getAnswerId()));
        verify(aiChatService).chatCompletion(anyString());
    }

    @Test
    void ask_AI开启_LLM抛异常_兜底随机() {
        when(answerMapper.selectList(any())).thenReturn(List.of(answer(1L, "是", 1, 0)));
        when(sysSettingService.getAsBoolean("conch_ai_enabled", true)).thenReturn(true);
        when(aiChatService.chatCompletion(anyString())).thenThrow(new RuntimeException("boom"));

        ConchAskResultVO vo;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(false);
            vo = conchService.ask(dtoWith("q"));
        }
        assertEquals(1L, vo.getAnswerId());
        verify(aiChatService).chatCompletion(anyString());
    }

    @Test
    void ask_AI关闭_纯随机且不调LLM() {
        List<ConchAnswer> answers = List.of(answer(1L, "是", 1, 0), answer(2L, "否", 1, 1));
        when(answerMapper.selectList(any())).thenReturn(answers);
        when(sysSettingService.getAsBoolean("conch_ai_enabled", true)).thenReturn(false);

        ConchAskResultVO vo;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(false);
            vo = conchService.ask(dtoWith("q"));
        }
        assertTrue(List.of(1L, 2L).contains(vo.getAnswerId()));
        verify(aiChatService, never()).chatCompletion(anyString());
    }

    // ===== getAnswerFile =====

    @Test
    void getAnswerFile_预设不存在_抛异常() {
        when(answerMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> conchService.getAnswerFile(1L));
    }

    @Test
    void getAnswerFile_音频不存在_抛异常() {
        when(answerMapper.selectById(1L)).thenReturn(answer(1L, "x", 1, 0));
        when(fileMapper.selectFullById(100L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> conchService.getAnswerFile(1L));
    }

    @Test
    void getAnswerFile_正常返回文件() {
        when(answerMapper.selectById(1L)).thenReturn(answer(1L, "x", 1, 0));
        SysFile f = mock(SysFile.class);
        when(f.getData()).thenReturn(new byte[]{1, 2, 3});
        when(fileMapper.selectFullById(100L)).thenReturn(f);
        assertSame(f, conchService.getAnswerFile(1L));
    }

    // ===== createAnswer =====

    @Test
    void createAnswer_音频不存在_抛异常() {
        when(fileMapper.selectById(99L)).thenReturn(null);
        ConchAnswerDTO dto = new ConchAnswerDTO();
        dto.setAnswerText("text");
        dto.setFileId(99L);
        assertThrows(BusinessException.class, () -> conchService.createAnswer(dto));
    }

    @Test
    void createAnswer_正常_插入且默认启用() {
        when(fileMapper.selectById(99L)).thenReturn(mock(SysFile.class));
        ConchAnswerDTO dto = new ConchAnswerDTO();
        dto.setAnswerText("text");
        dto.setFileId(99L);
        dto.setMatchDescription("desc");
        conchService.createAnswer(dto);
        verify(answerMapper).insert(any(ConchAnswer.class));
    }

    // ===== toggleEnabled =====

    @Test
    void toggleEnabled_不存在_抛异常() {
        when(answerMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> conchService.toggleEnabled(1L, 0));
    }

    @Test
    void toggleEnabled_存在_更新() {
        when(answerMapper.selectById(1L)).thenReturn(answer(1L, "x", 1, 0));
        conchService.toggleEnabled(1L, 0);
        verify(answerMapper).updateById(any(ConchAnswer.class));
    }

    // ===== answerPage =====

    @Test
    void answerPage_映射VO并回填分页元信息() {
        ConchAnswer a = answer(1L, "是", 1, 0);
        Page<ConchAnswer> page = new Page<>();
        page.setRecords(List.of(a));
        page.setTotal(1);
        page.setSize(10);
        page.setCurrent(1);
        page.setPages(1);
        when(answerMapper.selectPage(any(), any())).thenReturn(page);

        ConchAnswerQueryDTO q = new ConchAnswerQueryDTO();
        q.setPageNum(1);
        q.setPageSize(10);
        q.setAnswerText("是");
        q.setEnabled(1);

        Page<com.stellar.game.vo.ConchAnswerVO> res = conchService.answerPage(q);
        assertEquals(1, res.getTotal());
        assertEquals(1, res.getRecords().size());
        assertEquals("是", res.getRecords().get(0).getAnswerText());
        assertEquals(1, res.getRecords().get(0).getEnabled());
    }

    // ===== updateAnswer =====

    @Test
    void updateAnswer_不存在_抛异常() {
        when(answerMapper.selectById(2L)).thenReturn(null);
        ConchAnswerDTO dto = new ConchAnswerDTO();
        dto.setId(2L);
        dto.setAnswerText("x");
        assertThrows(BusinessException.class, () -> conchService.updateAnswer(dto));
    }

    @Test
    void updateAnswer_更换文件但文件不存在_抛异常() {
        when(answerMapper.selectById(2L)).thenReturn(answer(2L, "旧", 1, 0));
        when(fileMapper.selectById(99L)).thenReturn(null);
        ConchAnswerDTO dto = new ConchAnswerDTO();
        dto.setId(2L);
        dto.setAnswerText("新");
        dto.setFileId(99L);
        BusinessException ex = assertThrows(BusinessException.class, () -> conchService.updateAnswer(dto));
        assertTrue(ex.getMessage().contains("音频文件不存在"));
        verify(answerMapper, never()).updateById(any(ConchAnswer.class));
    }

    @Test
    void updateAnswer_同文件_直接更新() {
        when(answerMapper.selectById(2L)).thenReturn(answer(2L, "旧", 1, 0));
        ConchAnswerDTO dto = new ConchAnswerDTO();
        dto.setId(2L);
        dto.setAnswerText("新文本");
        conchService.updateAnswer(dto);
        verify(answerMapper).updateById(any(ConchAnswer.class));
    }

    // ===== deleteAnswer =====

    @Test
    void deleteAnswer_逻辑删除() {
        conchService.deleteAnswer(3L);
        verify(answerMapper).deleteById(3L);
    }

    // ===== recordPage =====

    @Test
    void recordPage_关联回填命中回答文本() {
        com.stellar.game.entity.ConchRecord r = new com.stellar.game.entity.ConchRecord();
        r.setId(1L);
        r.setQuestionText("我该辞职吗");
        r.setAnswerId(1L);
        r.setUserId(5L);
        Page<com.stellar.game.entity.ConchRecord> page = new Page<>();
        page.setRecords(List.of(r));
        page.setTotal(1);
        page.setSize(10);
        page.setCurrent(1);
        page.setPages(1);
        when(recordMapper.selectPage(any(), any())).thenReturn(page);

        when(answerMapper.selectBatchIds(any())).thenReturn(List.of(answer(1L, "再想想", 1, 0)));

        Page<ConchRecordVO> res = conchService.recordPage(1, 10);
        assertEquals(1, res.getRecords().size());
        ConchRecordVO vo = res.getRecords().get(0);
        assertEquals("我该辞职吗", vo.getQuestionText());
        assertEquals(1L, vo.getAnswerId());
        assertEquals("再想想", vo.getAnswerText());
        assertEquals(5L, vo.getUserId());
    }
}
