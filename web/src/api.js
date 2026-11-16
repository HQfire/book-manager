const TOKEN_KEY = 'bm_token'
const USERNAME_KEY = 'bm_username'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function getUsername() {
  return localStorage.getItem(USERNAME_KEY)
}

export function saveSession(token, username) {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(USERNAME_KEY, username)
}

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USERNAME_KEY)
}

async function request(method, path, body) {
  const headers = { 'Content-Type': 'application/json' }
  const token = getToken()
  if (token) {
    headers.Authorization = 'Bearer ' + token
  }

  const response = await fetch('/api' + path, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  })

  const json = await response.json().catch(() => ({
    code: response.status,
    message: response.statusText,
    data: null,
  }))

  if (json.code === 401) {
    clearSession()
  }
  if (json.code !== 200) {
    throw new Error(json.message || 'request failed')
  }
  return json.data
}

export async function fetchHealth() {
  const response = await fetch('/actuator/health')
  return response.json()
}

export const api = {
  register: (body) => request('POST', '/auth/register', body),
  login: (body) => request('POST', '/auth/login', body),
  createBook: (body) => request('POST', '/books', body),
  updateBook: (id, body) => request('PUT', `/books/${id}`, body),
  deleteBook: (id) => request('DELETE', `/books/${id}`),
  pageBooks: (params) => {
    const query = new URLSearchParams(
      Object.entries(params).filter(([, value]) => value !== '' && value != null),
    ).toString()
    return request('GET', `/books/page?${query}`)
  },
}
