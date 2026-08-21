package com.stellar.game.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stellar.game.dto.GameScoreSubmitDTO;
import com.stellar.game.entity.SysGameScore;
import com.stellar.system.entity.SysUser;
import com.stellar.game.mapper.SysGameScoreMapper;
import com.stellar.system.service.UserService;
import com.stellar.game.vo.GameScoreVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 游戏成绩排行榜服务：提交成绩、查询排行榜。
 * <p>游客与登录用户均可提交；登录用户记录 userId，便于跨设备关联。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameScoreService {

    private final SysGameScoreMapper scoreMapper;
    private final UserService userService;

    /**
     * 提交一局成绩。
     *
     * @param dto 成绩参数
     * @param ip  客户端 IP（反作弊/统计）
     */
    public GameScoreVO submit(GameScoreSubmitDTO dto, String ip) {
        SysGameScore score = new SysGameScore();
        score.setPlayerName(dto.getPlayerName());
        score.setScore(dto.getScore());
        score.setTotalTime(dto.getTotalTime());
        score.setAccuracy(dto.getAccuracy());
        score.setIp(ip);
        score.setCreateTime(LocalDateTime.now());

        // 登录用户记录 userId（游客无登录态，跳过）
        try {
            if (StpUtil.isLogin()) {
                Long userId = StpUtil.getLoginIdAsLong();
                score.setUserId(userId);
                // 登录用户若未填名称，兜底用昵称
                SysUser user = userService.getById(userId);
                if (user != null && (score.getPlayerName() == null || score.getPlayerName().isBlank())) {
                    score.setPlayerName(user.getNickname() != null ? user.getNickname() : user.getUsername());
                }
            }
        } catch (Exception e) {
            log.debug("[游戏成绩] 登录态解析失败，按游客处理: {}", e.getMessage());
        }

        scoreMapper.insert(score);
        log.info("[游戏成绩] 玩家={} 得分={}/30 用时={}s 正确率={}{} IP={}",
                score.getPlayerName(), score.getScore(), score.getTotalTime(),
                score.getAccuracy(), score.getUserId() != null ? " userId=" + score.getUserId() : "", ip);
        return toVO(score);
    }

    /**
     * 排行榜前 100：分数降序 → 用时升序 → 提交时间升序。
     */
    public List<GameScoreVO> topScores() {
        List<SysGameScore> list = scoreMapper.selectList(new LambdaQueryWrapper<SysGameScore>()
                .orderByDesc(SysGameScore::getScore)
                .orderByAsc(SysGameScore::getTotalTime)
                .orderByAsc(SysGameScore::getCreateTime)
                .last("LIMIT 100"));
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    private GameScoreVO toVO(SysGameScore s) {
        GameScoreVO vo = new GameScoreVO();
        vo.setId(s.getId());
        vo.setPlayerName(s.getPlayerName());
        vo.setScore(s.getScore());
        vo.setTotalTime(s.getTotalTime());
        vo.setAccuracy(s.getAccuracy());
        vo.setCreateTime(s.getCreateTime());
        return vo;
    }
}
