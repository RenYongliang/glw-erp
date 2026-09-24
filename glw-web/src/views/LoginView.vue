<script setup lang="ts">
import { reactive, ref, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  User,
  Lock,
  Right,
  Box,
  ShoppingCart,
  TrendCharts,
  CircleCheckFilled,
} from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import { mockAccounts, safeRedirect } from '../domain/mockAuth'
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const formRef = ref<FormInstance>()
const form = reactive({ username: '', password: '', remember: false })
const loading = ref(false)
const error = ref('')
let disposed = false
onBeforeUnmount(() => {
  disposed = true
})
const rules: FormRules = {
  username: [{ required: true, whitespace: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}
function fillAccount(index: number) {
  const account = mockAccounts[index]!
  form.username = account.username
  form.password = account.password
  error.value = ''
  formRef.value?.clearValidate()
}
async function submit() {
  if (loading.value) return
  error.value = ''
  if (!(await formRef.value?.validate().catch(() => false))) return
  loading.value = true
  try {
    await new Promise((resolve) => setTimeout(resolve, 350))
    if (disposed) return
    await auth.login(form.username, form.password, form.remember)
    await router.replace(safeRedirect(route.query.redirect))
    ElMessage.success(`欢迎回来，${auth.user?.name}`)
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '登录失败，请重试'
  } finally {
    loading.value = false
  }
}
</script>
<template>
  <main class="login-page">
    <header class="login-header">
      <a class="login-brand" href="/login" aria-label="GLW 进销存登录"
        ><span class="brand-mark">G</span><strong>GLW 进销存</strong></a
      ><span class="login-edition">业务管理工作台</span
      ><span class="login-environment">演示环境</span>
    </header>
    <div class="login-layout">
      <section class="login-intro">
        <span class="intro-eyebrow">GLW · BUSINESS WORKSPACE</span>
        <h1>业务有序，<br />每一步都清晰。</h1>
        <p class="intro-description">
          从订单到库存，从采购到销售，<br />在一个工作台，掌握业务的每一次流转。
        </p>
        <div class="login-illustration" aria-hidden="true">
          <div class="illustration-window">
            <div class="illustration-title">
              <span class="tiny-brand">G</span>经营概览<span class="window-dots">•••</span>
            </div>
            <div class="illustration-body">
              <div class="illustration-side"><i /><i /><i /><i /></div>
              <div class="illustration-main">
                <div class="illustration-metrics">
                  <div>
                    <small>销售订单</small><strong>128 <span>笔</span></strong>
                  </div>
                  <div>
                    <small>业务完成率</small><strong>98.6<span>%</span></strong>
                  </div>
                </div>
                <div class="illustration-chart">
                  <small>业务增长趋势</small
                  ><svg viewBox="0 0 380 120" preserveAspectRatio="none">
                    <defs>
                      <linearGradient id="login-chart-fill" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="0" stop-color="#1687ff" stop-opacity=".28" />
                        <stop offset="1" stop-color="#1687ff" stop-opacity="0" />
                      </linearGradient>
                    </defs>
                    <path
                      d="M0 100H380M0 60H380M0 20H380"
                      stroke="#edf2fb"
                      fill="none"
                      stroke-dasharray="4 4"
                    />
                    <path
                      d="M0 95 C40 95 40 75 76 80 S130 42 160 53 S230 50 250 31 S315 40 340 12 L380 5 L380 120 L0 120Z"
                      fill="url(#login-chart-fill)"
                    />
                    <path
                      d="M0 95 C40 95 40 75 76 80 S130 42 160 53 S230 50 250 31 S315 40 340 12 L380 5"
                      stroke="#1687ff"
                      stroke-width="3"
                      fill="none"
                    />
                  </svg>
                </div>
              </div>
            </div>
          </div>
          <div class="illustration-status">
            <el-icon><CircleCheckFilled /></el-icon>
            <div><strong>协同更高效</strong><span>订单 · 仓库 · 财务</span></div>
          </div>
        </div>
        <div class="intro-features">
          <span
            ><el-icon><ShoppingCart /></el-icon>订单协同</span
          ><span
            ><el-icon><Box /></el-icon>库存管理</span
          ><span
            ><el-icon><TrendCharts /></el-icon>经营分析</span
          >
        </div>
      </section>
      <section class="login-card" aria-labelledby="login-title">
        <div class="login-card-header">
          <span class="login-welcome">欢迎回来</span>
          <h2 id="login-title">登录工作台</h2>
          <p>登录 GLW，开始今天的业务管理</p>
        </div>
        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          class="login-form"
          @submit.prevent="submit"
        >
          <el-form-item label="账号" prop="username"
            ><el-input
              v-model="form.username"
              size="large"
              :prefix-icon="User"
              placeholder="请输入账号"
              autocomplete="username"
              aria-label="账号"
              :disabled="loading"
              clearable
              @input="error = ''"
          /></el-form-item>
          <el-form-item label="密码" prop="password"
            ><el-input
              v-model="form.password"
              size="large"
              :prefix-icon="Lock"
              placeholder="请输入密码"
              type="password"
              show-password
              autocomplete="current-password"
              aria-label="密码"
              :disabled="loading"
              @input="error = ''"
          /></el-form-item>
          <div class="login-options">
            <el-checkbox v-model="form.remember" :disabled="loading">保持登录 7 天</el-checkbox
            ><span>未勾选仅当前会话有效</span>
          </div>
          <el-alert
            v-if="error"
            :title="error"
            type="error"
            show-icon
            :closable="false"
            class="login-error"
            role="alert"
          />
          <el-button
            class="login-submit"
            type="primary"
            size="large"
            native-type="submit"
            :loading="loading"
            >{{ loading ? '正在登录' : '登 录' }}<el-icon v-if="!loading"><Right /></el-icon
          ></el-button>
        </el-form>
        <div class="demo-accounts">
          <div class="demo-accounts-heading">
            <strong>测试账号</strong><span>点击填入，即可体验</span>
          </div>
          <button
            v-for="(account, index) in mockAccounts"
            :key="account.id"
            :disabled="loading"
            class="demo-account"
            :aria-label="`填入${account.name}账号`"
            @click="fillAccount(index)"
          >
            <div>
              <strong>{{ account.name }}</strong
              ><span>{{ account.username }} / {{ account.password }}</span>
            </div>
            <el-icon><Right /></el-icon>
          </button>
        </div>
        <p class="login-note">当前为前端演示，使用上方测试账号登录。</p>
      </section>
    </div>
    <footer class="login-footer">GLW 进销存 · 让业务协同更简单</footer>
  </main>
</template>
<style scoped>
.login-page {
  min-height: 100dvh;
  background: radial-gradient(ellipse at 15% 35%, #e8f2ff 0, transparent 52%), #f5f7fc;
  color: #25354b;
  display: flex;
  flex-direction: column;
}
.login-header {
  height: 88px;
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 0 5.5%;
  flex-shrink: 0;
}
.login-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  text-decoration: none;
  color: #22354d;
  font-size: 18px;
}
.login-brand .brand-mark {
  width: 32px;
  height: 32px;
  line-height: 32px;
}
.login-edition {
  border-left: 1px solid #dce3ee;
  padding-left: 18px;
  color: #8190a5;
  font-size: 13px;
}
.login-environment {
  margin-left: auto;
  color: #387bc9;
  background: #e9f2ff;
  border: 1px solid #d7e7fc;
  padding: 5px 12px;
  border-radius: 20px;
  font-size: 12px;
}
.login-layout {
  flex: 1;
  width: min(1150px, 90%);
  margin: 30px auto;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 420px;
  gap: 90px;
  align-items: center;
}
.login-intro {
  padding: 0 10px;
}
.intro-eyebrow {
  color: #5a8fd2;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 2px;
}
.login-intro h1 {
  font-size: 44px;
  line-height: 1.4;
  letter-spacing: 2px;
  font-weight: 600;
  margin: 18px 0;
  color: #1d3b65;
}
.intro-description {
  font-size: 15px;
  line-height: 1.9;
  color: #74869f;
}
.login-illustration {
  position: relative;
  margin-top: 32px;
  padding-right: 30px;
  padding-bottom: 16px;
  max-width: 490px;
}
.illustration-window {
  border: 1px solid #fff;
  border-radius: 12px;
  background: #ffffffcf;
  box-shadow: 0 18px 50px #356cc518;
  transform: perspective(1000px) rotateY(-5deg);
  overflow: hidden;
}
.illustration-title {
  padding: 13px 16px;
  border-bottom: 1px solid #edf1f8;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
  color: #445b7c;
}
.tiny-brand {
  display: grid;
  place-items: center;
  background: #1687ff;
  color: white;
  border-radius: 4px;
  width: 18px;
  height: 18px;
}
.window-dots {
  margin-left: auto;
  color: #c1d2eb;
  letter-spacing: 4px;
}
.illustration-body {
  display: flex;
  padding: 16px 16px 10px;
  gap: 15px;
}
.illustration-side {
  width: 55px;
  padding-top: 8px;
}
.illustration-side i {
  display: block;
  height: 6px;
  margin-bottom: 20px;
  background: #e6edf8;
  border-radius: 6px;
}
.illustration-side i:first-child {
  background: #80b8ff;
  width: 80%;
}
.illustration-main {
  flex: 1;
  min-width: 0;
}
.illustration-metrics {
  display: flex;
  gap: 12px;
}
.illustration-metrics > div {
  flex: 1;
  padding: 10px 12px;
  background: #f4f8ff;
  border-radius: 6px;
}
.illustration-metrics small,
.illustration-chart small {
  font-size: 10px;
  color: #8b9bb0;
}
.illustration-metrics strong {
  display: block;
  color: #3387ee;
  font-size: 23px;
  font-weight: 500;
  margin-top: 7px;
}
.illustration-metrics strong span {
  font-size: 10px;
}
.illustration-chart {
  padding: 16px 0 0;
}
.illustration-chart svg {
  display: block;
  width: 100%;
  height: 100px;
  margin-top: 8px;
}
.illustration-status {
  position: absolute;
  right: 0;
  bottom: 0;
  display: flex;
  gap: 10px;
  align-items: center;
  background: white;
  padding: 14px 20px;
  border-radius: 9px;
  box-shadow: 0 10px 35px #3671c320;
}
.illustration-status > .el-icon {
  font-size: 26px;
  color: #26b994;
}
.illustration-status strong {
  font-size: 13px;
  font-weight: 500;
  display: block;
}
.illustration-status span {
  display: block;
  font-size: 11px;
  color: #8b98aa;
  margin-top: 4px;
}
.intro-features {
  display: flex;
  gap: 28px;
  margin-top: 26px;
  color: #70849f;
  font-size: 13px;
}
.intro-features > span {
  display: flex;
  align-items: center;
  gap: 7px;
}
.intro-features .el-icon {
  color: #498edb;
  font-size: 17px;
}
.login-card {
  background: white;
  border: 1px solid #e9edf5;
  border-radius: 14px;
  padding: 36px;
  box-shadow: 0 14px 60px #2547710a;
}
.login-welcome {
  font-size: 12px;
  color: #1687ff;
}
.login-card h2 {
  font-size: 27px;
  font-weight: 600;
  margin: 9px 0 10px;
  color: #213651;
}
.login-card-header p {
  font-size: 13px;
  color: #8c99ad;
}
.login-form {
  margin-top: 30px;
}
.login-form :deep(.el-form-item) {
  margin-bottom: 23px;
}
.login-form :deep(.el-form-item__label) {
  font-size: 14px;
  color: #42546e;
  padding-bottom: 7px;
}
.login-form :deep(.el-input__wrapper) {
  border-radius: 5px;
  padding: 4px 12px;
}
.login-form :deep(.el-input__prefix) {
  margin-right: 5px;
  color: #98a8bf;
}
.login-options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: -9px;
  margin-bottom: 16px;
}
.login-options :deep(.el-checkbox__label) {
  font-size: 12px;
}
.login-options > span {
  font-size: 11px;
  color: #9ba6b6;
}
.login-submit {
  width: 100%;
  height: 44px;
  font-size: 15px;
  border-radius: 5px;
  display: flex;
  justify-content: center;
  gap: 12px;
  box-shadow: 0 5px 13px #1687ff1c;
}
.login-error {
  margin-bottom: 14px;
}
.demo-accounts {
  border-top: 1px solid #edf0f6;
  margin-top: 26px;
  padding-top: 20px;
}
.demo-accounts-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  margin-bottom: 10px;
}
.demo-accounts-heading strong {
  font-weight: 500;
  color: #6a7b94;
}
.demo-accounts-heading > span {
  color: #9aa7b9;
  font-size: 11px;
}
.demo-account {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  text-align: left;
  background: #f7f9fd;
  border: 1px solid #edf1f8;
  border-radius: 6px;
  padding: 10px 12px;
  margin-top: 8px;
  color: #879cb7;
}
.demo-account:hover:not(:disabled) {
  background: #eef6ff;
  border-color: #bddbff;
}
.demo-account:disabled {
  cursor: default;
  opacity: 0.6;
}
.demo-account strong {
  font-size: 12px;
  font-weight: 500;
  color: #506780;
  display: block;
}
.demo-account span {
  font-size: 12px;
  color: #8699b3;
  display: block;
  margin-top: 4px;
  font-family: Consolas, monospace;
}
.login-note {
  font-size: 11px;
  color: #9aa7b9;
  text-align: center;
  line-height: 1.7;
  margin-top: 18px;
}
.login-footer {
  text-align: center;
  color: #9caabd;
  font-size: 12px;
  letter-spacing: 1px;
  padding: 24px 15px;
}
.login-form :deep(.el-alert__title) {
  font-size: 12px;
}
@media (min-width: 1500px) {
  .login-layout {
    gap: 130px;
    width: 1200px;
    grid-template-columns: minmax(0, 1fr) 440px;
  }
  .login-card {
    padding: 40px;
  }
  .login-intro h1 {
    font-size: 48px;
  }
}
@media (max-width: 1050px) {
  .login-layout {
    gap: 35px;
    grid-template-columns: minmax(0, 1fr) 390px;
  }
  .login-intro h1 {
    font-size: 36px;
  }
  .intro-features {
    gap: 13px;
    font-size: 12px;
  }
  .illustration-side {
    display: none;
  }
  .login-card {
    padding: 28px;
  }
  .illustration-status {
    padding: 10px 14px;
  }
  .intro-description {
    font-size: 14px;
  }
}
@media (max-width: 780px) {
  .login-header {
    height: 72px;
    padding: 0 22px;
  }
  .login-edition {
    display: none;
  }
  .login-layout {
    display: block;
    width: min(420px, calc(100% - 32px));
    margin: 24px auto;
  }
  .login-intro {
    display: none;
  }
  .login-card {
    padding: 30px 26px;
  }
  .login-card h2 {
    font-size: 26px;
  }
  .login-footer {
    font-size: 11px;
  }
  .login-brand {
    font-size: 16px;
  }
}
@media (max-width: 370px) {
  .login-card {
    padding: 25px 20px;
  }
  .login-options {
    align-items: flex-start;
    flex-direction: column;
    gap: 0;
  }
  .login-options > span {
    margin-bottom: 10px;
  }
}
</style>
