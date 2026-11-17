<script setup>
import { ref } from 'vue'
import { api, saveSession } from '../api.js'
import { t } from '../i18n.js'
import SettingsModal from './SettingsModal.vue'

const emit = defineEmits(['logged-in'])

const settingsOpen = ref(false)
const mode = ref('login')
const username = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)

function switchMode(next) {
  mode.value = next
  error.value = ''
}

async function submit() {
  error.value = ''
  if (!username.value.trim() || !password.value) {
    error.value = t('required_account')
    return
  }
  loading.value = true
  try {
    if (mode.value === 'login') {
      const data = await api.login({
        username: username.value.trim(),
        password: password.value,
      })
      saveSession(data.token, data.username, data.refreshToken)
      emit('logged-in', { username: data.username })
    } else {
      await api.register({
        username: username.value.trim(),
        password: password.value,
      })
      mode.value = 'login'
      password.value = ''
      error.value = ''
    }
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page auth-page">
    <div class="settings-corner">
      <button class="btn btn-secondary" type="button" @click="settingsOpen = true">
        {{ t('settings') }}
      </button>
    </div>

    <SettingsModal :open="settingsOpen" @close="settingsOpen = false" />

    <div class="card auth-card">
      <h1 class="auth-title">{{ t('app_title') }}</h1>
      <p class="muted auth-subtitle">{{ t('subtitle') }}</p>

      <div class="tabs">
        <button
          class="tab"
          :class="{ active: mode === 'login' }"
          @click="switchMode('login')"
        >
          {{ t('login') }}
        </button>
        <button
          class="tab"
          :class="{ active: mode === 'register' }"
          @click="switchMode('register')"
        >
          {{ t('register') }}
        </button>
      </div>

      <form @submit.prevent="submit">
        <div class="field">
          <label for="username">{{ t('username') }}</label>
          <input id="username" v-model="username" autocomplete="username" />
        </div>
        <div class="field">
          <label for="password">{{ t('password') }}</label>
          <input
            id="password"
            v-model="password"
            type="password"
            autocomplete="current-password"
          />
        </div>

        <p v-if="error" class="error">{{ error }}</p>

        <button class="btn btn-primary submit-btn" type="submit" :disabled="loading">
          {{ loading ? t('please_wait') : mode === 'login' ? t('login') : t('register') }}
        </button>
      </form>
    </div>
  </div>
</template>

<style scoped>
.auth-page {
  position: relative;
  display: flex;
  justify-content: center;
  padding-top: 80px;
}

.settings-corner {
  position: absolute;
  top: 24px;
  right: 24px;
}

.auth-card {
  width: 360px;
  padding: 28px;
}

.auth-title {
  margin: 0 0 4px;
  font-size: 24px;
}

.auth-subtitle {
  margin-bottom: 20px;
}

.tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 20px;
}

.tab {
  flex: 1;
  background: #f3f4f6;
  border: 1px solid transparent;
  border-radius: 8px;
  padding: 8px 0;
  cursor: pointer;
  color: var(--text-muted);
}

.tab.active {
  background: #fff;
  border-color: var(--border);
  color: var(--primary);
}

.submit-btn {
  width: 100%;
}
</style>
