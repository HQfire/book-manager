<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { api, fetchCoverBlob } from '../api.js'
import { t } from '../i18n.js'
import SettingsModal from './SettingsModal.vue'
import ReaderView from './ReaderView.vue'

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
const createCover = ref(null)
const createText = ref(null)
const createCoverPreview = ref('')

const detailBook = ref(null)
const detailReader = ref(null)
const coverUrl = ref('')
const readerOpen = ref(false)
const readerBook = ref(null)
const rowCovers = ref({})

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
    loadRowCovers()
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

async function loadRowCovers() {
  Object.values(rowCovers.value).forEach((url) => URL.revokeObjectURL(url))
  rowCovers.value = {}
  for (const book of books.value) {
    try {
      rowCovers.value[book.id] = await fetchCoverBlob(book.id)
    } catch {
      rowCovers.value[book.id] = ''
    }
  }
}

function onSearch() {
  pageNum.value = 1
  loadBooks()
}

function resetCreateFiles() {
  createCover.value = null
  createText.value = null
  if (createCoverPreview.value) {
    URL.revokeObjectURL(createCoverPreview.value)
    createCoverPreview.value = ''
  }
}

function openCreate() {
  editingId.value = null
  form.value = { title: '', author: '', description: '' }
  resetCreateFiles()
  modalOpen.value = true
}

function onCreateCoverPick(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  createCover.value = file || null
  if (createCoverPreview.value) URL.revokeObjectURL(createCoverPreview.value)
  createCoverPreview.value = file ? URL.createObjectURL(file) : ''
}

function onCreateTextPick(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  createText.value = file || null
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
      const created = await api.createBook(form.value)
      if (createCover.value) await api.uploadCover(created.id, createCover.value)
      if (createText.value) await api.uploadText(created.id, createText.value)
      message.value = t('created')
    } else {
      await api.updateBook(editingId.value, form.value)
      message.value = t('updated')
    }
    resetCreateFiles()
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
    closeDetail()
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

function formatSize(bytes) {
  if (bytes == null) return '-'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

async function loadCover(bookId) {
  if (coverUrl.value) {
    URL.revokeObjectURL(coverUrl.value)
    coverUrl.value = ''
  }
  try {
    coverUrl.value = await fetchCoverBlob(bookId)
  } catch {
    coverUrl.value = ''
  }
}

async function openDetail(book) {
  detailBook.value = book
  detailReader.value = null
  error.value = ''
  loadCover(book.id)
  try {
    detailReader.value = await api.reader(book.id)
  } catch {
    detailReader.value = null
  }
}

function closeDetail() {
  detailBook.value = null
  detailReader.value = null
  if (coverUrl.value) {
    URL.revokeObjectURL(coverUrl.value)
    coverUrl.value = ''
  }
}

async function onCoverPick(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file || !detailBook.value) return
  try {
    await api.uploadCover(detailBook.value.id, file)
    message.value = t('upload_success')
    loadCover(detailBook.value.id)
  } catch (e) {
    error.value = e.message
  }
}

async function onTextPick(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file || !detailBook.value) return
  try {
    await api.uploadText(detailBook.value.id, file)
    message.value = t('upload_success')
    detailReader.value = await api.reader(detailBook.value.id)
  } catch (e) {
    error.value = e.message
  }
}

function openReader() {
  if (!detailReader.value) return
  readerBook.value = detailBook.value
  readerOpen.value = true
}

async function readBook(book) {
  error.value = ''
  try {
    detailReader.value = await api.reader(book.id)
    readerBook.value = book
    readerOpen.value = true
  } catch (e) {
    error.value = e.message
  }
}

onUnmounted(() => {
  if (coverUrl.value) URL.revokeObjectURL(coverUrl.value)
  if (createCoverPreview.value) URL.revokeObjectURL(createCoverPreview.value)
  Object.values(rowCovers.value).forEach((url) => URL.revokeObjectURL(url))
})

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
            <th>{{ t('col_cover') }}</th>
            <th>{{ t('col_title') }}</th>
            <th>{{ t('col_author') }}</th>
            <th>{{ t('col_desc') }}</th>
            <th>{{ t('col_updated') }}</th>
            <th class="actions-col">{{ t('col_actions') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="books.length === 0 && !loading">
            <td colspan="6" class="muted empty">{{ t('empty') }}</td>
          </tr>
          <tr v-for="book in books" :key="book.id">
            <td class="cover-cell">
              <img v-if="rowCovers[book.id]" :src="rowCovers[book.id]" :alt="book.title" />
              <div v-else class="row-placeholder">{{ t('no_cover') }}</div>
            </td>
            <td>
              <button class="link-btn" @click="openDetail(book)">{{ book.title }}</button>
            </td>
            <td>{{ book.author || '-' }}</td>
            <td class="desc">{{ book.description || '-' }}</td>
            <td>{{ formatTime(book.updatedAt) }}</td>
            <td class="actions-col">
              <button class="btn btn-secondary" @click="openDetail(book)">{{ t('detail') }}</button>
              <button class="btn btn-primary" @click="readBook(book)">{{ t('read') }}</button>
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
        <div v-if="editingId == null" class="create-attachments">
          <div class="field">
            <label>{{ t('upload_cover') }}</label>
            <div class="file-pick-row">
              <img v-if="createCoverPreview" :src="createCoverPreview" class="create-thumb" :alt="t('cover')" />
              <label class="btn btn-secondary file-btn">
                {{ t('select_cover') }}
                <input type="file" accept="image/png,image/jpeg,image/webp" hidden @change="onCreateCoverPick" />
              </label>
              <span v-if="createCover" class="muted">{{ createCover.name }}</span>
            </div>
          </div>
          <div class="field">
            <label>{{ t('upload_text') }}</label>
            <div class="file-pick-row">
              <label class="btn btn-secondary file-btn">
                {{ t('select_text') }}
                <input type="file" accept=".txt,.md" hidden @change="onCreateTextPick" />
              </label>
              <span v-if="createText" class="muted">{{ createText.name }}</span>
            </div>
          </div>
        </div>
        <p v-if="error" class="error">{{ error }}</p>
        <div class="modal-actions">
          <button class="btn btn-secondary" @click="modalOpen = false">{{ t('cancel') }}</button>
          <button class="btn btn-primary" @click="submitForm">{{ t('save') }}</button>
        </div>
      </div>
    </div>

    <div v-if="detailBook" class="modal-mask" @click.self="closeDetail">
      <div class="card modal detail-modal">
        <h2>{{ t('about_book') }}</h2>
        <div class="detail-body">
          <div class="detail-cover">
            <img v-if="coverUrl" :src="coverUrl" :alt="detailBook.title" />
            <div v-else class="cover-placeholder">{{ t('no_cover') }}</div>
          </div>
          <div class="detail-info">
            <h3>{{ detailBook.title }}</h3>
            <p class="muted">{{ detailBook.author || '-' }}</p>
            <p class="detail-desc">{{ detailBook.description || '-' }}</p>
            <div class="meta-row" v-if="detailReader">
              <span>{{ t('text_file') }}：{{ detailReader.fileName || '-' }}</span>
              <span>{{ t('encoding') }}：{{ detailReader.encoding }}</span>
              <span>{{ t('file_size') }}：{{ formatSize(detailReader.size) }}</span>
              <span>{{ t('chapter_count') }}：{{ detailReader.chapters.length }}</span>
            </div>
            <p v-else class="muted">{{ t('no_text') }}</p>
          </div>
        </div>
        <p v-if="error" class="error">{{ error }}</p>
        <div class="detail-actions">
          <label class="btn btn-secondary file-btn">
            {{ t('upload_cover') }}
            <input type="file" accept="image/png,image/jpeg,image/webp" hidden @change="onCoverPick" />
          </label>
          <label class="btn btn-secondary file-btn">
            {{ t('upload_text') }}
            <input type="file" accept=".txt,.md" hidden @change="onTextPick" />
          </label>
          <button class="btn btn-primary" :disabled="!detailReader" @click="openReader">{{ t('read') }}</button>
          <button class="btn btn-secondary" @click="openEdit(detailBook); closeDetail()">{{ t('edit') }}</button>
          <button class="btn btn-danger" @click="removeBook(detailBook)">{{ t('delete') }}</button>
          <button class="btn btn-secondary" @click="closeDetail">{{ t('close') }}</button>
        </div>
      </div>
    </div>

    <ReaderView
      v-if="readerOpen && readerBook"
      :book-id="readerBook.id"
      :book-title="readerBook.title"
      @close="readerOpen = false"
    />

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

.cover-cell {
  width: 64px;
}

.cover-cell img,
.row-placeholder {
  width: 44px;
  height: 60px;
  object-fit: cover;
  border-radius: var(--radius);
  border: 1px solid var(--border);
}

.row-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  border-style: dashed;
  color: var(--text-muted);
  font-size: 12px;
  text-align: center;
  padding: 4px;
}

.actions-col {
  white-space: nowrap;
  text-align: right;
}

.actions-col .btn {
  margin-left: 8px;
  padding: 6px 10px;
}

.link-btn {
  border: none;
  background: transparent;
  color: var(--primary);
  cursor: pointer;
  padding: 0;
  font-size: inherit;
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
  z-index: 20;
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

.detail-modal {
  width: 620px;
}

.detail-body {
  display: flex;
  gap: 20px;
}

.detail-cover {
  width: 150px;
  flex-shrink: 0;
}

.detail-cover img {
  width: 150px;
  height: 200px;
  object-fit: cover;
  border-radius: var(--radius);
  border: 1px solid var(--border);
}

.cover-placeholder {
  width: 150px;
  height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px dashed var(--border);
  border-radius: var(--radius);
  color: var(--text-muted);
}

.detail-info {
  flex: 1;
  min-width: 0;
}

.detail-info h3 {
  margin: 0 0 6px;
}

.detail-desc {
  white-space: pre-wrap;
}

.meta-row {
  display: flex;
  flex-direction: column;
  gap: 4px;
  color: var(--text-muted);
  font-size: 13px;
}

.detail-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 18px;
}

.file-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.create-attachments {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 4px;
}

.file-pick-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.create-thumb {
  width: 44px;
  height: 60px;
  object-fit: cover;
  border-radius: var(--radius);
  border: 1px solid var(--border);
}
</style>
