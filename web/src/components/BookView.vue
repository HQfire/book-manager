<script setup>
import { computed, onMounted, ref } from 'vue'
import { api } from '../api.js'
import { t } from '../i18n.js'
import SettingsModal from './SettingsModal.vue'

const settingsOpen = ref(false)

const props = defineProps({
  username: { type: String, required: true },
})
const emit = defineEmits(['logout'])

const books = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = 10
const filterTitle = ref('')
const filterAuthor = ref('')

const loading = ref(false)
const error = ref('')
const message = ref('')

const modalOpen = ref(false)
const editingId = ref(null)
const form = ref({ title: '', author: '', description: '' })

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

async function loadBooks() {
  loading.value = true
  error.value = ''
  try {
    const data = await api.pageBooks({
      pageNum: pageNum.value,
      pageSize,
      title: filterTitle.value,
      author: filterAuthor.value,
    })
    books.value = data.records || []
    total.value = data.total || 0
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function onSearch() {
  pageNum.value = 1
  loadBooks()
}

function openCreate() {
  editingId.value = null
  form.value = { title: '', author: '', description: '' }
  modalOpen.value = true
}

function openEdit(book) {
  editingId.value = book.id
  form.value = {
    title: book.title || '',
    author: book.author || '',
    description: book.description || '',
  }
  modalOpen.value = true
}

async function submitForm() {
  error.value = ''
  message.value = ''
  if (!form.value.title.trim()) {
    error.value = t('title_required')
    return
  }
  try {
    if (editingId.value == null) {
      await api.createBook(form.value)
      message.value = t('created')
    } else {
      await api.updateBook(editingId.value, form.value)
      message.value = t('updated')
    }
    modalOpen.value = false
    loadBooks()
  } catch (e) {
    error.value = e.message
  }
}

async function removeBook(book) {
  if (!window.confirm(t('confirm_delete', { title: book.title }))) return
  try {
    await api.deleteBook(book.id)
    message.value = t('deleted')
    loadBooks()
  } catch (e) {
    error.value = e.message
  }
}

function prevPage() {
  if (pageNum.value > 1) {
    pageNum.value -= 1
    loadBooks()
  }
}

function nextPage() {
  if (pageNum.value < totalPages.value) {
    pageNum.value += 1
    loadBooks()
  }
}

function logout() {
  emit('logout')
}

function formatTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '-'
}

onMounted(loadBooks)
</script>

<template>
  <div class="page">
    <header class="topbar card">
      <div class="brand">{{ t('app_title') }}</div>
      <div class="user-area">
        <span class="muted">{{ props.username }}</span>
        <button class="btn btn-secondary" @click="settingsOpen = true">{{ t('settings') }}</button>
        <button class="btn btn-secondary" @click="logout">{{ t('logout') }}</button>
      </div>
    </header>

    <section class="card toolbar">
      <div class="filters">
        <input v-model="filterTitle" :placeholder="t('col_title')" @keyup.enter="onSearch" />
        <input v-model="filterAuthor" :placeholder="t('col_author')" @keyup.enter="onSearch" />
        <button class="btn btn-secondary" @click="onSearch">{{ t('search') }}</button>
      </div>
      <button class="btn btn-primary" @click="openCreate">{{ t('new_book') }}</button>
    </section>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="message" class="muted message">{{ message }}</p>

    <section class="card table-card">
      <table>
        <thead>
          <tr>
            <th>{{ t('col_title') }}</th>
            <th>{{ t('col_author') }}</th>
            <th>{{ t('col_desc') }}</th>
            <th>{{ t('col_updated') }}</th>
            <th class="actions-col">{{ t('col_actions') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="books.length === 0 && !loading">
            <td colspan="5" class="muted empty">{{ t('empty') }}</td>
          </tr>
          <tr v-for="book in books" :key="book.id">
            <td>{{ book.title }}</td>
            <td>{{ book.author || '-' }}</td>
            <td class="desc">{{ book.description || '-' }}</td>
            <td>{{ formatTime(book.updatedAt) }}</td>
            <td class="actions-col">
              <button class="btn btn-secondary" @click="openEdit(book)">{{ t('edit') }}</button>
              <button class="btn btn-danger" @click="removeBook(book)">{{ t('delete') }}</button>
            </td>
          </tr>
        </tbody>
      </table>

      <div class="pagination">
        <button class="btn btn-secondary" :disabled="pageNum <= 1" @click="prevPage">{{ t('prev') }}</button>
        <span class="muted">
          {{ t('pagination', { page: pageNum, totalPages, total }) }}
        </span>
        <button class="btn btn-secondary" :disabled="pageNum >= totalPages" @click="nextPage">{{ t('next') }}</button>
      </div>
    </section>

    <div v-if="modalOpen" class="modal-mask" @click.self="modalOpen = false">
      <div class="card modal">
        <h2>{{ editingId == null ? t('modal_new') : t('modal_edit') }}</h2>
        <div class="field">
          <label>{{ t('col_title') }}</label>
          <input v-model="form.title" maxlength="100" />
        </div>
        <div class="field">
          <label>{{ t('col_author') }}</label>
          <input v-model="form.author" maxlength="100" />
        </div>
        <div class="field">
          <label>{{ t('col_desc') }}</label>
          <textarea v-model="form.description" rows="3" maxlength="500"></textarea>
        </div>
        <p v-if="error" class="error">{{ error }}</p>
        <div class="modal-actions">
          <button class="btn btn-secondary" @click="modalOpen = false">{{ t('cancel') }}</button>
          <button class="btn btn-primary" @click="submitForm">{{ t('save') }}</button>
        </div>
      </div>
    </div>

    <SettingsModal :open="settingsOpen" :username="props.username" @close="settingsOpen = false" />
  </div>
</template>

<style scoped>
.topbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  margin-bottom: 16px;
}

.brand {
  font-size: 18px;
  font-weight: 600;
}

.user-area {
  display: flex;
  align-items: center;
  gap: 12px;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  margin-bottom: 16px;
}

.filters {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.filters input {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 8px 12px;
  width: 160px;
}

.table-card {
  padding: 8px 20px 16px;
}

table {
  width: 100%;
  border-collapse: collapse;
}

th,
td {
  text-align: left;
  padding: 12px 8px;
  border-bottom: 1px solid var(--border);
}

th {
  color: var(--text-muted);
  font-weight: 500;
  font-size: 13px;
}

.desc {
  max-width: 260px;
}

.actions-col {
  white-space: nowrap;
  text-align: right;
}

.actions-col .btn {
  margin-left: 8px;
  padding: 6px 10px;
}

.empty {
  text-align: center;
  padding: 32px 0;
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 16px;
}

.message {
  margin: 0 0 12px;
}

.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.35);
  display: flex;
  justify-content: center;
  align-items: center;
}

.modal {
  width: 420px;
  padding: 24px;
}

.modal h2 {
  margin: 0 0 16px;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
