package com.stellar.game.controller;

import com.stellar.game.dto.GameScoreSubmitDTO;
import com.stellar.game.service.GameScoreService;
import com.stellar.game.vo.GameScoreVO;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * {@link GameController} 单测：成绩提交的 IP 穿透解析 + 排行榜透传。
 */
@ExtendWith(MockitoExtension.class)
class GameControllerTest {

    @Mock
    GameScoreService gameScoreService;
    @Mock
    HttpServletRequest request;

    GameController controller;

    @BeforeEach
    void setup() {
        controller = new GameController(gameScoreService);
    }

    @Test
    void submit_代理头优先() {
        GameScoreSubmitDTO dto = new GameScoreSubmitDTO();
        dto.setPlayerName("p");
        dto.setScore(20);
        dto.setTotalTime(100);
        dto.setAccuracy(66.6);
        when(request.getHeader("X-Forwarded-For")).thenReturn("9.9.9.9, 10.0.0.1");
        GameScoreVO vo = new GameScoreVO();
        vo.setScore(20);
        when(gameScoreService.submit(dto, "9.9.9.9")).thenReturn(vo);

        assertEquals(20, controller.submit(dto, request).getData().getScore());
        verify(gameScoreService).submit(dto, "9.9.9.9");
    }

    @Test
    void submit_无代理头_回退remoteAddr() {
        GameScoreSubmitDTO dto = new GameScoreSubmitDTO();
        dto.setPlayerName("p");
        dto.setScore(10);
        dto.setTotalTime(50);
        dto.setAccuracy(33.3);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("1.2.3.4");

        controller.submit(dto, request);

        verify(gameScoreService).submit(dto, "1.2.3.4");
    }

    @Test
    void topScores_正常() {
        when(gameScoreService.topScores()).thenReturn(List.of(new GameScoreVO()));
        assertEquals(1, controller.topScores().getData().size());
    }
}
