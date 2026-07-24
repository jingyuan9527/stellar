import type { PromptTemplate } from '../types'

const now = Date.now()

export const builtinTemplates: PromptTemplate[] = [
  {
    id: 'bilibili',
    name: 'B站通用',
    platform: 'bilibili',
    builtIn: true,
    updatedAt: now,
    prompt:
      '你是B站视频文案专家。根据主题「{{topic}}」，生成5条适合B站的视频标题（偏干货/科普/盘点，可带【】或数字，20字以内），一段100字以内简介（说明视频价值），8个相关话题标签（不带#号，适合B站分区）。\n只返回JSON:{"titles":["..."],"description":"...","tags":["..."]}',
  },
  {
    id: 'douyin',
    name: '抖音爆款',
    platform: 'douyin',
    builtIn: true,
    updatedAt: now,
    prompt:
      '你是抖音短视频文案专家。根据主题「{{topic}}」，生成5条适合抖音的爆款标题（短促有力、情绪强、带钩子，15字以内），一段80字以内简介（口语化、有悬念），8个相关话题标签（不带#号）。\n只返回JSON:{"titles":["..."],"description":"...","tags":["..."]}',
  },
  {
    id: 'xiaohongshu',
    name: '小红书种草',
    platform: 'xiaohongshu',
    builtIn: true,
    updatedAt: now,
    prompt:
      '你是小红书笔记文案专家。根据主题「{{topic}}」，生成5条适合小红书的标题（带emoji、生活化、种草感，20字以内），一段120字以内简介（口语、有"姐妹们"等亲切感），8个相关话题标签（不带#号，小红书风格）。\n只返回JSON:{"titles":["..."],"description":"...","tags":["..."]}',
  },
]
