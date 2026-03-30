<template>
  <div class="login-page">
    <div class="login-bg" aria-hidden="true">
      <div class="login-bg__mesh" />
      <div class="login-bg__orb login-bg__orb--a" />
      <div class="login-bg__orb login-bg__orb--b" />
    </div>

    <div class="login-shell">
      <div class="login-card" role="main">
        <header class="login-brand">
          <div class="login-brand__icon" aria-hidden="true">
            <svg viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path
                d="M24 6L8 16v16l16 10 16-10V16L24 6z"
                stroke="currentColor"
                stroke-width="2"
                stroke-linejoin="round"
              />
              <path
                d="M24 22v10M18 19l6 3 6-3M16 26l8 4.5 8-4.5"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
            </svg>
          </div>
          <div class="login-brand__text">
            <h1 class="login-brand__title">规则引擎</h1>
            <p class="login-brand__subtitle">可视化编排 · 管理控制台</p>
          </div>
        </header>

        <el-form
          ref="form"
          class="login-form"
          :model="form"
          :rules="rules"
          label-position="top"
          @submit.native.prevent="submit"
        >
          <el-form-item label="用户名" prop="username">
            <el-input
              v-model="form.username"
              autocomplete="username"
              clearable
              placeholder="请输入用户名"
              @keyup.enter.native="submit"
            />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              autocomplete="current-password"
              show-password
              placeholder="请输入密码"
              @keyup.enter.native="submit"
            />
          </el-form-item>
          <el-form-item class="login-form__actions">
            <el-button
              type="primary"
              class="login-btn"
              :loading="loading"
              native-type="submit"
            >
              {{ loading ? '登录中…' : '登录' }}
            </el-button>
          </el-form-item>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script>
import { consoleLogin } from '@/api/auth'

export default {
  name: 'Login',
  data() {
    return {
      loading: false,
      form: { username: '', password: '' },
      rules: {
        username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
        password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
      }
    }
  },
  methods: {
    /**
     * 校验表单并调用登录接口，成功后跳回 redirect 或首页。
     */
    submit() {
      this.$refs.form.validate(async valid => {
        if (!valid) return
        this.loading = true
        try {
          await consoleLogin({
            username: this.form.username,
            password: this.form.password
          })
          const redirect = this.$route.query.redirect || '/project'
          this.$router.replace(redirect)
        } finally {
          this.loading = false
        }
      })
    }
  }
}
</script>

<style lang="scss" scoped>
@import '@/styles/variables.scss';

/* 设计 tokens：与 ui-ux-pro-max 建议一致的高对比中性色 + 项目主色 CTA */
$login-text: #0f172a;
$login-muted: #475569;
$login-border: #e2e8f0;
$login-primary: $--color-primary;
$login-primary-soft: rgba(179, 0, 0, 0.12);

.login-page {
  position: relative;
  min-height: 100vh;
  min-height: 100dvh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px 16px;
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  overflow-x: hidden;
}

/**
 * 背景层：网格 + 光晕，轻量动画（尊重 prefers-reduced-motion）。
 */
.login-bg {
  position: fixed;
  inset: 0;
  z-index: 0;
  background: linear-gradient(145deg, #f1f5f9 0%, #e2e8f0 45%, #cbd5e1 100%);
}

.login-bg__mesh {
  position: absolute;
  inset: 0;
  opacity: 0.45;
  background-image:
    radial-gradient(circle at 20% 30%, rgba(179, 0, 0, 0.07) 0%, transparent 45%),
    radial-gradient(circle at 80% 70%, rgba(15, 23, 42, 0.06) 0%, transparent 40%),
    linear-gradient(rgba(15, 23, 42, 0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(15, 23, 42, 0.03) 1px, transparent 1px);
  background-size: 100% 100%, 100% 100%, 48px 48px, 48px 48px;
  animation: mesh-drift 28s ease-in-out infinite alternate;
}

.login-bg__orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(64px);
  opacity: 0.55;
  animation: orb-float 18s ease-in-out infinite;
}

.login-bg__orb--a {
  width: min(42vw, 360px);
  height: min(42vw, 360px);
  top: -8%;
  left: -5%;
  background: rgba(179, 0, 0, 0.15);
  animation-delay: 0s;
}

.login-bg__orb--b {
  width: min(50vw, 420px);
  height: min(50vw, 420px);
  bottom: -12%;
  right: -8%;
  background: rgba(15, 23, 42, 0.12);
  animation-delay: -6s;
}

@keyframes mesh-drift {
  from {
    transform: scale(1) translate(0, 0);
  }
  to {
    transform: scale(1.04) translate(-1%, 1%);
  }
}

@keyframes orb-float {
  0%,
  100% {
    transform: translate(0, 0) scale(1);
  }
  50% {
    transform: translate(2%, 3%) scale(1.05);
  }
}

@media (prefers-reduced-motion: reduce) {
  .login-bg__mesh,
  .login-bg__orb {
    animation: none !important;
  }

  .login-card {
    animation: none !important;
  }
}

.login-shell {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 420px;
}

/**
 * 玻璃拟态卡片：浅色高不透明度，保证可读性与对比度。
 */
.login-card {
  padding: 36px 32px 28px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(255, 255, 255, 0.65);
  box-shadow:
    0 4px 24px rgba(15, 23, 42, 0.06),
    0 24px 48px rgba(15, 23, 42, 0.08),
    inset 0 1px 0 rgba(255, 255, 255, 0.9);
  -webkit-backdrop-filter: blur(18px);
  backdrop-filter: blur(18px);
  animation: card-enter 0.5s cubic-bezier(0.22, 1, 0.36, 1) both;
}

@keyframes card-enter {
  from {
    opacity: 0;
    transform: translateY(16px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.login-brand {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 28px;
  padding-bottom: 24px;
  border-bottom: 1px solid $login-border;
}

.login-brand__icon {
  flex-shrink: 0;
  width: 52px;
  height: 52px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 14px;
  color: $login-primary;
  background: linear-gradient(135deg, rgba(179, 0, 0, 0.1) 0%, rgba(179, 0, 0, 0.04) 100%);
  border: 1px solid rgba(179, 0, 0, 0.12);
  transition: transform 0.25s ease, box-shadow 0.25s ease;
}

.login-brand__icon svg {
  width: 28px;
  height: 28px;
}

.login-card:hover .login-brand__icon {
  box-shadow: 0 8px 20px rgba(179, 0, 0, 0.12);
}

.login-brand__title {
  margin: 0;
  font-size: 1.375rem;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: $login-text;
  line-height: 1.25;
}

.login-brand__subtitle {
  margin: 6px 0 0;
  font-size: 0.8125rem;
  font-weight: 500;
  color: $login-muted;
  line-height: 1.4;
}

.login-form {
  margin-top: 4px;
}

.login-form__actions {
  margin-bottom: 0;
  margin-top: 8px;
}

.login-footnote {
  margin: 20px 0 0;
  text-align: center;
  font-size: 12px;
  color: $login-muted;
  line-height: 1.5;
}

/**
 * Element 表单：圆角输入、清晰 focus 环、标签层次。
 */
::v-deep .login-form .el-form-item {
  margin-bottom: 20px;
}

::v-deep .login-form .el-form-item__label {
  padding: 0 0 8px;
  line-height: 1.3;
  font-weight: 600;
  font-size: 13px;
  color: $login-text;
}

::v-deep .login-form .el-input__inner {
  height: 44px;
  line-height: 44px;
  border-radius: 12px;
  border: 1px solid $login-border;
  font-size: 15px;
  color: $login-text;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

::v-deep .login-form .el-input__inner:hover {
  border-color: #cbd5e1;
}

::v-deep .login-form .el-input__inner:focus {
  border-color: $login-primary;
  box-shadow: 0 0 0 3px $login-primary-soft;
}

::v-deep .login-form .el-input__inner::placeholder {
  color: #94a3b8;
}

.login-btn {
  width: 100%;
  height: 46px !important;
  margin-top: 4px;
  padding: 0 !important;
  font-size: 15px !important;
  font-weight: 600 !important;
  letter-spacing: 0.02em;
  border-radius: 12px !important;
  cursor: pointer;
  transition:
    background-color 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.2s ease;
}

.login-btn:not(.is-loading):hover {
  box-shadow: 0 10px 28px rgba(179, 0, 0, 0.28);
}

.login-btn:not(.is-loading):active {
  transform: translateY(1px);
}

@media (max-width: 480px) {
  .login-card {
    padding: 28px 22px 22px;
    border-radius: 16px;
  }

  .login-brand {
    flex-direction: column;
    align-items: flex-start;
    text-align: left;
  }

  .login-brand__icon {
    width: 48px;
    height: 48px;
  }
}

@media (min-width: 768px) {
  .login-shell {
    max-width: 440px;
  }

  .login-card {
    padding: 40px 40px 32px;
  }
}
</style>
