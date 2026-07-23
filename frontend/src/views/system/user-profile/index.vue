<script setup lang="ts">
import { computed } from 'vue'
import { NCard, NDescriptions, NDescriptionsItem, NAvatar, NTag } from 'naive-ui'
import { useAuthStore } from '@/store/auth'
import { useThemeStore } from '@/store/theme'

const authStore = useAuthStore()
const themeStore = useThemeStore()
const user = computed(() => authStore.userInfo)
</script>

<template>
  <div class="profile-page">
    <NCard title="用户资料" :bordered="false">
      <div class="profile-header">
        <NAvatar round size="large" :color="themeStore.primaryColor">
          {{ user?.nickname?.charAt(0) || 'U' }}
        </NAvatar>
        <div class="profile-name">
          <h3>{{ user?.nickname || '用户' }}</h3>
          <NTag size="small" type="success">管理员</NTag>
        </div>
      </div>
    </NCard>

    <NCard title="基本信息" :bordered="false">
      <NDescriptions :column="2" label-placement="left" bordered>
        <NDescriptionsItem label="用户ID">{{ user?.id }}</NDescriptionsItem>
        <NDescriptionsItem label="用户名">{{ user?.username }}</NDescriptionsItem>
        <NDescriptionsItem label="昵称">{{ user?.nickname }}</NDescriptionsItem>
        <NDescriptionsItem label="状态">
          <NTag :type="user?.status === 1 ? 'success' : 'error'">
            {{ user?.status === 1 ? '启用' : '禁用' }}
          </NTag>
        </NDescriptionsItem>
        <NDescriptionsItem label="创建时间">{{ user?.createTime }}</NDescriptionsItem>
        <NDescriptionsItem label="更新时间">{{ user?.updateTime }}</NDescriptionsItem>
      </NDescriptions>
    </NCard>
  </div>
</template>

<style scoped>
.profile-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.profile-header {
  display: flex;
  align-items: center;
  gap: 16px;
}

.profile-name {
  display: flex;
  align-items: center;
  gap: 10px;
}

.profile-name h3 {
  margin: 0;
}
</style>
