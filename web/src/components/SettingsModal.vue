<script setup>
import { computed, ref, watch } from 'vue'
import { fetchHealth } from '../api.js'
import { LANG_OPTIONS, getLocale, setLocale, t } from '../i18n.js'

const props = defineProps({
  open: { type: Boolean, default: false },
  username: { type: String, default: '' },
})
const emit = defineEmits(['close'])

const activeTab = ref('general')
const query = ref('')
const healthStatus = ref('')
const healthLoading = ref(false)
const healthError = ref('')

const menus = [
  { key: 'general', label: 'tab_general' },
  { key: 'system', label: 'tab_system' },
  { key: 'api', label: 'tab_api' },
]

const filteredMenus = computed(() =>
  menus.filter((m) => t(m.label).toLowerCase().includes(query.value.toLowerCase())),
)

const currentTitle = computed(() => {
  const menu = menus.find((m) => m.key === activeTab.value)
  return menu ? t(menu.label) : ''
})

const avatarText = computed(() => {
  const name = props.username || t('app_title')
  return name.charAt(0).toUpperCase()
})

const apiRoutes = [
  { module: 'auth', method: 'POST', path: '/api/auth/register' },
  { module: 'auth', method: 'POST', path: '/api/auth/login' },
  { module: 'book', method: 'POST', path: '/api/books' },
  { module: 'book', method: 'PUT', path: '/api/books/{id}' },
  { module: 'book', method: 'DELETE', path: '/api/books/{id}' },
  { module: 'book', method: 'GET', path: '/api/books/{id}' },
  { module: 'book', method: 'GET', path: '/api/books/page' },
]

const systemInfo = [
  { label: 'app_title', value: 'Book Manager' },
  { label: 'version', value: '0.1.0' },
  { label: 'backend', value: 'http://127.0.0.1:8080' },
  { label: 'frontend', value: 'Vue 3 + Vite' },
  {
    label: 'tech_stack',
    value: 'Spring Boot 4 · MyBatis-Plus · MySQL · Redis · JJWT · Vue 3',
  },
]

async function loadHealth() {
  healthLoading.value = true
  healthError.value = ''
  healthStatus.value = ''
  try {
    const data = await fetchHealth()
    healthStatus.value = data.status || 'UNKNOWN'
  } catch (e) {
    healthError.value = e.message
  } finally {
    healthLoading.value = false
  }
}

function moduleLabel(module) {
  return module === 'auth' ? t('module_auth') : t('module_book')
}

watch(
  () => props.open,
  (val) => {
    if (val) {
      activeTab.value = 'general'
      query.value = ''
      loadHealth()
    }
  },
)
</script>

<template>
  <div v-if="open" class="modal-mask" @click.self="emit('close')">
    <div class="modal">
      <aside class="sidebar">
        <div class="search-box">
          <input v-model="query" :placeholder="t('search')" />
        </div>

        <div class="profile">
          <div class="avatar">{{ avatarText }}</div>
          <div class="profile-meta">
            <div class="profile-name">{{ props.username || t('app_title') }}</div>
            <div class="profile-sub">v0.1.0</div>
          </div>
        </div>

        <nav class="menu">
          <button
            v-for="menu in filteredMenus"
            :key="menu.key"
            type="button"
            class="menu-item"
            :class="{ active: activeTab === menu.key }"
            @click="activeTab = menu.key"
          >
            <span class="dot" :class="menu.key"></span>
            {{ t(menu.label) }}
          </button>
        </nav>
      </aside>

      <section class="content">
        <header class="content-header">
          <h2>{{ currentTitle }}</h2>
          <button class="close-btn" type="button" @click="emit('close')">✕</button>
        </header>

        <div class="content-body">
          <section v-if="activeTab === 'general'">
            <h3 class="group-title">{{ t('preferences') }}</h3>
            <div class="setting-row">
              <div class="row-left">
                <span class="row-icon blue">A</span>
                <span class="row-label">{{ t('language') }}</span>
              </div>
              <div class="row-right lang-options">
                <button
                  v-for="opt in LANG_OPTIONS"
                  :key="opt.code"
                  type="button"
                  class="mini-option"
                  :class="{ active: getLocale() === opt.code }"
                  @click="setLocale(opt.code)"
                >
                  {{ opt.label }}
                </button>
              </div>
            </div>
          </section>

          <section v-if="activeTab === 'system'">
            <h3 class="group-title">{{ t('system_info') }}</h3>
            <div v-for="item in systemInfo" :key="item.label" class="setting-row">
              <div class="row-left">
                <span class="row-icon gray">i</span>
                <span class="row-label">{{ t(item.label) }}</span>
              </div>
              <div class="row-right value">{{ item.value }}</div>
            </div>

            <h3 class="group-title">{{ t('health') }}</h3>
            <div class="setting-row">
              <div class="row-left">
                <span class="row-icon green">H</span>
                <span class="row-label">{{ t('health') }}</span>
              </div>
              <div class="row-right">
                <span v-if="healthLoading" class="muted">{{ t('health_checking') }}</span>
                <span
                  v-else-if="healthStatus"
                  class="badge"
                  :class="healthStatus === 'UP' ? 'badge-up' : 'badge-down'"
                >
                  {{ healthStatus === 'UP' ? t('health_up') : t('health_down') }}
                </span>
                <span v-if="healthError" class="error">{{ healthError }}</span>
              </div>
            </div>
          </section>

          <section v-if="activeTab === 'api'">
            <h3 class="group-title">{{ t('api_interfaces') }}</h3>
            <table class="api-table">
              <thead>
                <tr>
                  <th>{{ t('col_module') }}</th>
                  <th>{{ t('col_method') }}</th>
                  <th>{{ t('col_path') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="route in apiRoutes" :key="route.method + route.path">
                  <td>{{ moduleLabel(route.module) }}</td>
                  <td><span class="method">{{ route.method }}</span></td>
                  <td class="path">{{ route.path }}</td>
                </tr>
              </tbody>
            </table>
          </section>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.35);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 20;
}

.modal {
  display: flex;
  width: 760px;
  max-width: calc(100vw - 32px);
  height: 480px;
  background: #fff;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 20px 40px rgba(15, 23, 42, 0.18);
}

.sidebar {
  width: 230px;
  flex-shrink: 0;
  background: #f7f8fa;
  border-right: 1px solid var(--border);
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.search-box input {
  width: 100%;
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 9px 12px;
  background: #fff;
}

.profile {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 4px 6px;
}

.avatar {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  background: var(--primary);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
}

.profile-name {
  font-weight: 600;
}

.profile-sub {
  color: var(--text-muted);
  font-size: 12px;
}

.menu {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  border: none;
  background: transparent;
  border-radius: 10px;
  padding: 10px 12px;
  cursor: pointer;
  text-align: left;
}

.menu-item.active {
  background: #fff;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.08);
  color: var(--primary);
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.dot.general {
  background: var(--primary);
}

.dot.system {
  background: #059669;
}

.dot.api {
  background: #7c3aed;
}

.content {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.content-header {
  display: flex;
  justify-content: center;
  align-items: center;
  position: relative;
  padding: 18px 24px;
}

.content-header h2 {
  margin: 0;
  font-size: 16px;
}

.close-btn {
  position: absolute;
  right: 20px;
  top: 16px;
  border: none;
  background: transparent;
  font-size: 16px;
  cursor: pointer;
  color: var(--text-muted);
  padding: 6px;
}

.content-body {
  flex: 1;
  overflow-y: auto;
  padding: 0 24px 24px;
}

.group-title {
  margin: 8px 0 4px;
  color: var(--text-muted);
  font-size: 13px;
  font-weight: 500;
}

.setting-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 14px 0;
  border-bottom: 1px solid var(--border);
}

.row-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.row-icon {
  width: 32px;
  height: 32px;
  border-radius: 9px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 13px;
  flex-shrink: 0;
}

.row-icon.blue {
  background: var(--primary);
}

.row-icon.gray {
  background: #6b7280;
}

.row-icon.green {
  background: #059669;
}

.row-label {
  font-size: 14px;
}

.row-right {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-muted);
}

.row-right.value {
  font-size: 13px;
  text-align: right;
}

.lang-options {
  gap: 6px;
}

.mini-option {
  border: 1px solid var(--border);
  background: #fff;
  border-radius: 8px;
  padding: 6px 12px;
  cursor: pointer;
}

.mini-option.active {
  border-color: var(--primary);
  background: rgba(37, 99, 235, 0.08);
  color: var(--primary);
}

.badge {
  border-radius: 999px;
  padding: 4px 12px;
  font-size: 13px;
}

.badge-up {
  background: #ecfdf5;
  color: #059669;
}

.badge-down {
  background: #fef2f2;
  color: var(--danger);
}

.api-table {
  width: 100%;
  border-collapse: collapse;
}

.api-table th,
.api-table td {
  text-align: left;
  padding: 10px 8px;
  border-bottom: 1px solid var(--border);
}

.method {
  font-family: ui-monospace, Consolas, monospace;
  font-size: 12px;
  color: var(--primary);
}

.path {
  font-family: ui-monospace, Consolas, monospace;
  font-size: 12px;
}
</style>
