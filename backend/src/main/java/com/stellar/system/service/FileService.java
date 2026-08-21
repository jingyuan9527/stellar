package com.stellar.system.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.common.BusinessException;
import com.stellar.common.FileConstants;
import com.stellar.system.dto.SysFileQueryDTO;
import com.stellar.system.entity.SysFile;
import com.stellar.system.entity.SysUser;
import com.stellar.system.mapper.SysFileMapper;
import com.stellar.system.mapper.SysUserMapper;
import com.stellar.system.vo.SysFileVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 文件服务：上传、分页（联查上传者用户名）、硬删除（单条/批量）。
 * <p>上传记录登录者 user_id；分页不加载 data 二进制（@TableField(select=false)）；
 * 删除为硬删除，引用方（头像/海螺音频/AI 产物）需自行承担悬空风险。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private final SysFileMapper fileMapper;
    private final SysUserMapper sysUserMapper;

    /**
     * 上传文件（图片/音频），二进制存库，返回相对 URL 如 /file/123。
     *
     * @param isPublic 游客可见（头像/海螺预设等落地页素材传 true；默认私有仅本人可读）
     */
    public String upload(MultipartFile file, boolean isPublic) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件为空");
        }
        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf('.') + 1).toLowerCase();
        }
        if (!FileConstants.ALLOWED_EXT.contains(ext)) {
            throw new BusinessException("不支持的文件类型，仅允许图片或音频");
        }
        SysFile entity = new SysFile();
        entity.setOriginalName(original);
        entity.setExt(ext);
        entity.setContentType(file.getContentType());
        entity.setSize(file.getSize());
        try {
            entity.setData(file.getBytes());
        } catch (IOException e) {
            log.error("[文件上传] 读取字节失败 orig={} err={}", original, e.getMessage(), e);
            throw new BusinessException("文件读取失败");
        }
        entity.setUserId(StpUtil.getLoginIdAsLong());
        entity.setIsPublic(isPublic);
        entity.setCreateTime(LocalDateTime.now());
        fileMapper.insert(entity);
        String url = "/file/" + entity.getId();
        log.info("[文件上传] 成功 orig={} size={} user={} public={} -> {}",
                original, file.getSize(), entity.getUserId(), isPublic, url);
        return url;
    }

    /**
     * 按主键查全字段（含 data，供二进制读取）。
     */
    public SysFile getFull(Long id) {
        return fileMapper.selectFullById(id);
    }

    /**
     * 跨模块落文件入口：调用方自建实体（含二进制）直接入库，返回后 id 已回填。
     */
    public SysFile create(SysFile file) {
        fileMapper.insert(file);
        return file;
    }

    /**
     * 文件是否存在（供引用方挂载前校验）。
     */
    public boolean exists(Long id) {
        return id != null && fileMapper.selectById(id) != null;
    }

    /**
     * 静默硬删除（引用方清理产物用）：不存在时不动、不报错，区别于 remove 的强校验语义。
     */
    public void deleteById(Long id) {
        if (id == null || fileMapper.selectById(id) == null) {
            return;
        }
        fileMapper.deleteById(id);
    }

    /**
     * 分页查询（不含 data），联查上传者用户名。
     */
    public Page<SysFileVO> page(SysFileQueryDTO query) {
        Page<SysFile> page = new Page<>(query.getPageNum(), query.getPageSize());
        String fileType = query.getFileType();
        LambdaQueryWrapper<SysFile> wrapper = new LambdaQueryWrapper<SysFile>()
                .like(StringUtils.hasText(query.getOriginalName()),
                        SysFile::getOriginalName, query.getOriginalName())
                .eq(query.getUserId() != null, SysFile::getUserId, query.getUserId())
                .ge(query.getStartTime() != null, SysFile::getCreateTime, query.getStartTime())
                .le(query.getEndTime() != null, SysFile::getCreateTime, query.getEndTime())
                .in("image".equals(fileType), SysFile::getExt, FileConstants.IMAGE_EXT)
                .in("audio".equals(fileType), SysFile::getExt, FileConstants.AUDIO_EXT)
                .orderByDesc(SysFile::getCreateTime);
        Page<SysFile> result = fileMapper.selectPage(page, wrapper);

        Set<Long> userIds = result.getRecords().stream()
                .map(SysFile::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> nameMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<SysUser> users = sysUserMapper.selectBatchIds(userIds);
            for (SysUser u : users) {
                nameMap.put(u.getId(), u.getUsername());
            }
        }

        Page<SysFileVO> voPage = new Page<>();
        voPage.setTotal(result.getTotal());
        voPage.setSize(result.getSize());
        voPage.setCurrent(result.getCurrent());
        voPage.setPages(result.getPages());
        voPage.setRecords(result.getRecords().stream().map(f -> {
            SysFileVO vo = new SysFileVO();
            vo.setId(f.getId());
            vo.setOriginalName(f.getOriginalName());
            vo.setExt(f.getExt());
            vo.setContentType(f.getContentType());
            vo.setSize(f.getSize());
            vo.setUserId(f.getUserId());
            vo.setUploaderName(f.getUserId() != null ? nameMap.get(f.getUserId()) : null);
            vo.setIsPublic(Boolean.TRUE.equals(f.getIsPublic()));
            vo.setCreateTime(f.getCreateTime());
            return vo;
        }).collect(Collectors.toList()));
        return voPage;
    }

    /**
     * 硬删除单条。
     */
    public void remove(Long id) {
        SysFile file = fileMapper.selectById(id);
        if (file == null) {
            throw new BusinessException("文件不存在");
        }
        fileMapper.deleteById(id);
        log.info("[文件管理] 删除 id={} orig={} size={}", id, file.getOriginalName(), file.getSize());
    }

    /**
     * 硬删除批量。
     */
    public void removeBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("未选择要删除的文件");
        }
        List<SysFile> files = fileMapper.selectBatchIds(ids);
        fileMapper.deleteBatchIds(ids);
        String names = files.stream()
                .map(f -> f.getId() + ":" + f.getOriginalName())
                .collect(Collectors.joining(","));
        log.info("[文件管理] 批量删除 count={} ids={} names={}", ids.size(), ids, names);
    }
}
