package com.stellar.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.common.BusinessException;
import com.stellar.dto.ShowcaseDTO;
import com.stellar.dto.ShowcaseQueryDTO;
import com.stellar.entity.SysShowcase;
import com.stellar.mapper.SysShowcaseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 作品橱窗服务：管理 CRUD + 游客公开列表。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShowcaseService {

    private final SysShowcaseMapper showcaseMapper;

    /** 游客公开列表（visible=1，按 sort_order 后 createTime 倒序） */
    public List<SysShowcase> listPublic() {
        return showcaseMapper.selectList(new LambdaQueryWrapper<SysShowcase>()
                .eq(SysShowcase::getVisible, 1)
                .orderByAsc(SysShowcase::getSortOrder)
                .orderByDesc(SysShowcase::getCreateTime));
    }

    public Page<SysShowcase> page(ShowcaseQueryDTO query) {
        Page<SysShowcase> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<SysShowcase> w = new LambdaQueryWrapper<SysShowcase>()
                .eq(StringUtils.hasText(query.getType()), SysShowcase::getType, query.getType())
                .like(StringUtils.hasText(query.getTitle()), SysShowcase::getTitle, query.getTitle())
                .orderByDesc(SysShowcase::getUpdateTime);
        return showcaseMapper.selectPage(page, w);
    }

    public void create(ShowcaseDTO dto) {
        SysShowcase s = new SysShowcase();
        applyDto(s, dto);
        s.setCreateTime(LocalDateTime.now());
        s.setUpdateTime(LocalDateTime.now());
        showcaseMapper.insert(s);
        log.info("[橱窗] 新增 id={} title={}", s.getId(), s.getTitle());
    }

    public void update(Long id, ShowcaseDTO dto) {
        SysShowcase s = showcaseMapper.selectById(id);
        if (s == null) {
            throw new BusinessException("作品不存在");
        }
        applyDto(s, dto);
        s.setUpdateTime(LocalDateTime.now());
        showcaseMapper.updateById(s);
    }

    public void delete(Long id) {
        SysShowcase s = showcaseMapper.selectById(id);
        if (s == null) {
            throw new BusinessException("作品不存在");
        }
        showcaseMapper.deleteById(id);
    }

    private void applyDto(SysShowcase s, ShowcaseDTO dto) {
        s.setType(dto.getType());
        s.setTitle(dto.getTitle());
        s.setSummary(dto.getSummary());
        s.setCoverUrl(dto.getCoverUrl());
        s.setContent(dto.getContent());
        s.setMediaUrl(dto.getMediaUrl());
        s.setLink(dto.getLink());
        s.setTags(dto.getTags());
        s.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        s.setVisible(dto.getVisible() == null ? 1 : dto.getVisible());
    }
}
