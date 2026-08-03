package com.stellar.system.controller;

import com.stellar.system.service.DashboardService;
import com.stellar.system.vo.DashboardStatsVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

/**
 * {@link DashboardController} 单测：stats 透传。
 */
@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @Mock
    DashboardService dashboardService;

    DashboardController controller;

    @BeforeEach
    void setup() {
        controller = new DashboardController(dashboardService);
    }

    @Test
    void stats_正常() {
        DashboardStatsVO vo = new DashboardStatsVO();
        when(dashboardService.stats()).thenReturn(vo);
        assertSame(vo, controller.stats().getData());
    }
}
