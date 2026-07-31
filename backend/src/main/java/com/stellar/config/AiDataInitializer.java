package com.stellar.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stellar.ai.AiBuiltinTemplates;
import com.stellar.ai.entity.SysAiTemplate;
import com.stellar.ai.mapper.SysAiTemplateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * AI 模块数据初始化：播种 3 套内置提示词模板。
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class AiDataInitializer implements CommandLineRunner {

    private final SysAiTemplateMapper templateMapper;

    @Override
    public void run(String... args) {
        Long count = templateMapper.selectCount(
                new LambdaQueryWrapper<SysAiTemplate>().eq(SysAiTemplate::getBuiltIn, 1));
        if (count != null && count > 0) {
            return;
        }

        for (AiBuiltinTemplates.Seed seed : AiBuiltinTemplates.SEEDS) {
            SysAiTemplate template = new SysAiTemplate();
            template.setName(seed.name());
            template.setPlatform(seed.platform());
            template.setPrompt(seed.prompt());
            template.setBuiltIn(1);
            template.setCreatorId(null);
            template.setCreateTime(LocalDateTime.now());
            template.setUpdateTime(LocalDateTime.now());
            templateMapper.insert(template);
        }
        log.info("已初始化 {} 套 AI 内置提示词模板", AiBuiltinTemplates.SEEDS.size());
    }
}
