<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { api } from '../api.js'
import { t } from '../i18n.js'

const props = defineProps({
  bookId: { type: Number, required: true },
  bookTitle: { type: String, default: '' },
})
const emit = defineEmits(['close'])

const PREF_KEY = 'bm_reader_pref'

const FAMILIES = {
  sans: `system-ui, 'Segoe UI', Roboto, 'PingFang SC', 'Microsoft YaHei', sans-serif`,
  serif: `Georgia, 'Songti SC', 'SimSun', serif`,
  mono: `'Courier New', Consolas, monospace`,
}

const BG_OPTIONS = ['#f5f0e6', '#ffffff', '#e8eef7', '#22252a']
const COLOR_OPTIONS = ['#2b2118', '#1f2937', '#7c2d12', '#ffffff']

const savedPref = (() => {
  try {
    return JSON.parse(localStorage.getItem(PREF_KEY) || '{}')
  } catch {
    return {}
  }
})()

const prefs = reactive({
  family: savedPref.family || 'serif',
  size: savedPref.size || 18,
  bg: savedPref.bg || '#f5f0e6',
  color: savedPref.color || '#2b2118',
})

watch(prefs, (value) => {
  localStorage.setItem(PREF_KEY, JSON.stringify(value))
}, { deep: true })

const loading = ref(true)
const error = ref('')
const reader = ref(null)
const chapterIndex = ref(0)
const settingsOpen = ref(false)

const currentChapter = computed(() => {
  const chapters = reader.value?.chapters || []
  return chapters[chapterIndex.value] || null
})

const fontFamily = computed(() => FAMILIES[prefs.family] || FAMILIES.serif)

async function loadReader() {
  loading.value = true
  error.value = ''
  try {
    reader.value = await api.reader(props.bookId)
    chapterIndex.value = 0
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function selectChapter(index) {
  chapterIndex.value = index
}

function prevChapter() {
  if (chapterIndex.value > 0) chapterIndex.value -= 1
}

function nextChapter() {
  const total = reader.value?.chapters?.length || 0
  if (chapterIndex.value < total - 1) chapterIndex.value += 1
}

function formatSize(bytes) {
  if (!bytes && bytes !== 0) return '-'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

onMounted(loadReader)
</script>

<template>
  <div class="reader-mask">
    <div class="reader-shell">
      <header class="reader-bar">
        <button class="btn btn-secondary" @click="emit('close')">&times; {{ t('close') }}</button>
        <div class="reader-title">{{ bookTitle || t('reader_title') }}</div>
        <button class="btn btn-secondary" @click="settingsOpen = !settingsOpen">
          {{ t('font_size') }}
        </button>
      </header>

      <div v-if="settingsOpen" class="reader-settings card">
        <div class="setting-row">
          <label>{{ t('font_family') }}</label>
          <select v-model="prefs.family">
            <option value="sans">{{ t('font_sans') }}</option>
            <option value="serif">{{ t('font_serif') }}</option>
            <option value="mono">{{ t('font_mono') }}</option>
          </select>
        </div>
        <div class="setting-row">
          <label>{{ t('font_size') }}</label>
          <input type="range" min="14" max="28" v-model.number="prefs.size" />
          <span class="muted">{{ prefs.size }} px</span>
        </div>
        <div class="setting-row">
          <label>{{ t('bg_color') }}</label>
          <div class="swatches">
            <button
              v-for="bg in BG_OPTIONS"
              :key="bg"
              class="swatch"
              :class="{ active: prefs.bg === bg }"
              :style="{ background: bg }"
              @click="prefs.bg = bg"
            />
          </div>
        </div>
        <div class="setting-row">
          <label>{{ t('text_color') }}</label>
          <div class="swatches">
            <button
              v-for="color in COLOR_OPTIONS"
              :key="color"
              class="swatch"
              :class="{ active: prefs.color === color }"
              :style="{ background: color }"
              @click="prefs.color = color"
            />
          </div>
        </div>
      </div>

      <div v-if="loading" class="reader-body muted">{{ t('reader_loading') }}</div>
      <p v-if="error" class="reader-body error">{{ error }}</p>

      <div v-else-if="reader" class="reader-layout">
        <aside class="toc">
          <div class="toc-title">{{ t('toc') }}（{{ reader.chapters.length }}）</div>
          <button
            v-for="chapter in reader.chapters"
            :key="chapter.index"
            class="toc-item"
            :class="{ active: chapter.index === chapterIndex }"
            @click="selectChapter(chapter.index)"
          >
            {{ chapter.title }}
          </button>
        </aside>

        <main
          class="reader-content"
          :style="{
            fontFamily,
            fontSize: prefs.size + 'px',
            backgroundColor: prefs.bg,
            color: prefs.color,
          }"
        >
          <h2 v-if="currentChapter" class="chapter-title">{{ currentChapter.title }}</h2>
          <div class="chapter-text" v-text="currentChapter?.content || ''" />
          <div class="reader-nav">
            <button class="btn btn-secondary" :disabled="chapterIndex === 0" @click="prevChapter">
              {{ t('prev_chapter') }}
            </button>
            <button
              class="btn btn-secondary"
              :disabled="chapterIndex >= reader.chapters.length - 1"
              @click="nextChapter"
            >
              {{ t('next_chapter') }}
            </button>
          </div>
        </main>
      </div>
    </div>
  </div>
</template>

<style scoped>
.reader-mask {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  display: flex;
  justify-content: center;
  padding: 24px;
  z-index: 30;
}

.reader-shell {
  width: 100%;
  max-width: 1000px;
  background: var(--card);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.reader-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border);
}

.reader-title {
  font-weight: 600;
}

.reader-settings {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border);
}

.setting-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.setting-row label {
  color: var(--text-muted);
  font-size: 13px;
}

.swatches {
  display: flex;
  gap: 6px;
}

.swatch {
  width: 24px;
  height: 24px;
  border-radius: 6px;
  border: 1px solid var(--border);
  cursor: pointer;
}

.swatch.active {
  outline: 2px solid var(--primary);
  outline-offset: 1px;
}

.reader-body {
  padding: 32px 24px;
}

.reader-layout {
  display: flex;
  flex: 1;
  min-height: 0;
}

.toc {
  width: 220px;
  border-right: 1px solid var(--border);
  overflow-y: auto;
  padding: 12px 0;
}

.toc-title {
  padding: 0 16px 8px;
  color: var(--text-muted);
  font-size: 13px;
}

.toc-item {
  display: block;
  width: 100%;
  text-align: left;
  border: none;
  background: transparent;
  padding: 9px 16px;
  cursor: pointer;
  color: var(--text);
}

.toc-item.active,
.toc-item:hover {
  background: #f3f4f6;
}

.reader-content {
  flex: 1;
  padding: 24px 32px;
  overflow-y: auto;
  line-height: 1.8;
}

.chapter-title {
  margin: 0 0 16px;
  font-size: 1.25em;
}

.chapter-text {
  white-space: pre-wrap;
  word-break: break-word;
}

.reader-nav {
  display: flex;
  justify-content: space-between;
  margin-top: 24px;
}
</style>
