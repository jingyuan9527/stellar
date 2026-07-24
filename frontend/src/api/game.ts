import { request } from './request'
import type { GameScore, GameScoreSubmit } from '@/types/api'

/** 提交一局游戏成绩（游客可调，受 IP 限流保护） */
export function submitGameScore(data: GameScoreSubmit) {
  return request<GameScore>({ url: '/game/scores', method: 'post', data })
}

/** 排行榜前 100（公共墙，游客可读） */
export function getGameTopScores() {
  return request<GameScore[]>({ url: '/game/scores/top', method: 'get' })
}
