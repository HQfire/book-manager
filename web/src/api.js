import { t } from './i18n.js'

const TOKEN_KEY = 'bm_token'
const REFRESH_KEY = 'bm_refresh'
const USERNAME_KEY = 'bm_username'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function getRefreshToken() {
  return localStorage.getItem(REFRESH_KEY)
}

export function getUsername() {
  return localStorage.getItem(USERNAME_KEY)
}

export function saveSession(token, username, refreshToken) {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(USERNAME_KEY, username)
  if (refreshToken) {
    localStorage.setItem(REFRESH_KEY, refreshToken)
  }
}

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(REFRESH_KEY)
  localStorage.removeItem(USERNAME_KEY)
}

function messageForCode(code, fallback) {
  const key = `error_${code}`
  const mapped = t(key)
  return mapped === key ? fallback || t('error_network') : mapped
}

function authHeaders(json = false) {
  const headers = {}
  const token = getToken()
  if (token) {
    headers.Authorization = 'Bearer ' + token
  }
  if (json) {
    headers['Content-Type'] = 'application/json'
  }
  return headers
}

function parseJson(response) {
  return response.json().catch(() => ({
    code: response.status,
    message: response.statusText,
    data: null,
  }))
}

function raiseFor(json) {
  if (json.code !== 200) {
    const err = new Error(messageForCode(json.code, json.message))
    err.code = json.code
    throw err
  }
}

async function tryRefresh() {
  const refreshToken = getRefreshToken()
  if (!refreshToken) return false
  try {
    const response = await fetch('/api/auth/refresh', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    })
    const json = await parseJson(response)
    if (json.code !== 200 || !json.data?.token) return false
    localStorage.setItem(TOKEN_KEY, json.data.token)
    if (json.data.refreshToken) {
      localStorage.setItem(REFRESH_KEY, json.data.refreshToken)
    }
    return true
  } catch (e) {
    return false
  }
}

async function doFetch(method, path, body, isJson, retried = false) {
  let response
  try {
    response = await fetch('/api' + path, {
      method,
      headers: authHeaders(isJson),
      body: isJson && body ? JSON.stringify(body) : body,
    })
  } catch (e) {
    const err = new Error(t('error_network'))
    err.code = 0
    throw err
  }

  const json = await parseJson(response)
  if (json.code === 401 && !retried && (await tryRefresh())) {
    return doFetch(method, path, body, isJson, true)
  }
  if (json.code === 401) {
    clearSession()
  }
  raiseFor(json)
  return json.data
}

async function request(method, path, body) {
  return doFetch(method, path, body, true)
}

async function uploadFile(path, file) {
  const form = new FormData()
  form.append('file', file)
  return doFetch('POST', path, form, false)
}

export async function fetchHealth() {
  const response = await fetch('/actuator/health')
  return response.json()
}

export async function fetchCoverBlob(id) {
  const response = await fetch(`/api/books/${id}/cover`, {
    headers: authHeaders(),
  })
  if (!response.ok) {
    throw new Error(t('error_404'))
  }
  const blob = await response.blob()
  if (blob.type && blob.type.includes('application/json')) {
    throw new Error(t('error_404'))
  }
  return URL.createObjectURL(blob)
}

export const api = {
  register: (body) => request('POST', '/auth/register', body),
  login: (body) => request('POST', '/auth/login', body),
  logout: () =>
    request('POST', '/auth/logout', { refreshToken: getRefreshToken() }),
  createBook: (body) => request('POST', '/books', body),
  updateBook: (id, body) => request('PUT', `/books/${id}`, body),
  deleteBook: (id) => request('DELETE', `/books/${id}`),
  pageBooks: (params) => {
    const query = new URLSearchParams(
      Object.entries(params).filter(([, value]) => value !== '' && value != null),
    ).toString()
    return request('GET', `/books/page?${query}`)
  },
  uploadText: (id, file) => uploadFile(`/books/${id}/text`, file),
  uploadCover: (id, file) => uploadFile(`/books/${id}/cover`, file),
  reader: (id) => request('GET', `/books/${id}/reader`),
  chapter: (id, index) => request('GET', `/books/${id}/chapters/${index}`),
}
