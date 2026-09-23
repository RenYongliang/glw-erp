<script setup lang="ts">
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ArrowDown, User, SwitchButton } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const profileOpen = ref(false)
async function action(command: string) {
  if (command === 'profile') {
    profileOpen.value = true
    return
  }
  const redirect = route.fullPath
  if (document.fullscreenElement) {
    try {
      await document.exitFullscreen()
    } catch {
      /* Logout still continues. */
    }
  }
  auth.logout()
  await router.replace({ name: 'login', query: { redirect } })
  ElMessage.success('已退出登录')
}
</script>
<template>
  <div class="user-area" v-if="auth.user">
    <span class="demo-indicator">演示数据</span>
    <el-dropdown trigger="click" @command="action"
      ><button class="user-menu-trigger" aria-label="用户菜单">
        <span class="avatar">{{ auth.user.name.slice(0, 1) }}</span
        ><span class="user-menu-name">{{ auth.user.name }}</span
        ><el-icon><ArrowDown /></el-icon></button
      ><template #dropdown
        ><el-dropdown-menu
          ><el-dropdown-item command="profile" :icon="User">账号信息</el-dropdown-item
          ><el-dropdown-item command="logout" :icon="SwitchButton" divided
            >退出登录</el-dropdown-item
          ></el-dropdown-menu
        ></template
      ></el-dropdown
    >
    <el-dialog v-model="profileOpen" title="账号信息" width="min(460px, 92vw)"
      ><el-descriptions :column="1" border
        ><el-descriptions-item label="姓名">{{ auth.user.name }}</el-descriptions-item
        ><el-descriptions-item label="账号">{{ auth.user.username }}</el-descriptions-item
        ><el-descriptions-item label="角色">{{ auth.user.role }}</el-descriptions-item
        ><el-descriptions-item label="部门">{{
          auth.user.department
        }}</el-descriptions-item></el-descriptions
      >
      <p class="profile-demo-note">当前为演示账号，可体验所有已开放页面。</p>
      <template #footer
        ><el-button type="primary" @click="profileOpen = false">知道了</el-button></template
      ></el-dialog
    >
  </div>
</template>
<style scoped>
.user-menu-trigger {
  display: flex;
  align-items: center;
  gap: 9px;
  background: transparent;
  border: 0;
  padding: 5px 8px;
  border-radius: 6px;
  color: #334155;
  font-size: 14px;
}
.user-menu-trigger:hover {
  background: #f0f6ff;
}
.profile-demo-note {
  margin-top: 16px;
  color: #8592a5;
  line-height: 1.7;
}
@media (max-width: 700px) {
  .user-menu-name {
    display: none;
  }
}
</style>
