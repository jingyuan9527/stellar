<script setup lang="ts">
import { computed, h } from 'vue'
import { NCard, NGrid, NGridItem, NIcon, NStatistic, NSpace, NButton } from 'naive-ui'
import { useAuthStore } from '@/store/auth'
import { iconMap } from '@/utils/icons'

const authStore = useAuthStore()

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '凌晨好'
  if (hour < 12) return '上午好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const stats = [
  { label: '用户数', value: 1280, icon: 'person', color: '#18a058' },
  { label: '访问量', value: 8846, icon: 'grid', color: '#2080f0' },
  { label: '订单数', value: 326, icon: 'list', color: '#f0a020' },
  { label: '消息数', value: 18, icon: 'info', color: '#d03050' },
]

function renderStatIcon(name: string, color: string) {
  const Icon = iconMap[name]
  if (!Icon) return null
  return h(NIcon, { size: 28, color }, { default: () => h(Icon) })
}
</script>

<template>
  <div class="dashboard">
    <NCard class="welcome-card" :bordered="false">
      <div class="welcome">
        <div class="welcome-text">
          <h2>{{ greeting }}，{{ authStore.userInfo?.nickname || '用户' }} 👋</h2>
          <p>欢迎使用 Stellar Admin，一个清新优雅的后台管理框架。</p>
        </div>
        <div class="welcome-icon">
          <NIcon size="64" color="#18a058">
            <component :is="iconMap.grid" />
          </NIcon>
        </div>
      </div>
    </NCard>

    <NGrid :x-gap="16" :y-gap="16" :cols="4" responsive="screen" item-responsive>
      <NGridItem
        v-for="item in stats"
        :key="item.label"
        span="4 m:2 l:1"
      >
        <NCard :bordered="false" class="stat-card">
          <div class="stat">
            <div class="stat-icon">
              <component :is="renderStatIcon(item.icon, item.color)" />
            </div>
            <NStatistic :label="item.label" :value="item.value" />
          </div>
        </NCard>
      </NGridItem>
    </NGrid>

    <NCard title="项目说明" :bordered="false">
      <NSpace vertical :size="12">
        <p>本框架为纯脚手架壳，已集成以下能力：</p>
        <ul class="feature-list">
          <li>左侧多级菜单 + 右侧内容区布局</li>
          <li>登录鉴权（Sa-Token + PostgreSQL）</li>
          <li>多标签页 + 面包屑导航</li>
          <li>暗黑模式 + 主题色配置</li>
          <li>可折叠侧栏</li>
        </ul>
        <NSpace>
          <NButton type="primary" @click="$router.push('/system/user-profile')">
            查看用户资料
          </NButton>
        </NSpace>
      </NSpace>
    </NCard>
  </div>
</template>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.welcome {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.welcome-text h2 {
  margin: 0 0 8px;
  font-size: 22px;
}

.welcome-text p {
  margin: 0;
  color: #888;
}

.stat {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 12px;
  background: rgba(127, 127, 127, 0.1);
}

.feature-list {
  margin: 0;
  padding-left: 20px;
  line-height: 1.9;
}
</style>
