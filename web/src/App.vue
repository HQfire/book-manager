<script setup>
import { ref } from 'vue'
import AuthView from './components/AuthView.vue'
import BookView from './components/BookView.vue'
import { api, clearSession, getToken, getUsername } from './api.js'

const token = ref(getToken())
const username = ref(getUsername() || '')

function onLogin(payload) {
  token.value = getToken()
  username.value = payload.username
}

async function onLogout() {
  try {
    await api.logout()
  } catch (e) {
    // ignore logout failure, local session is cleared below
  }
  clearSession()
  token.value = ''
  username.value = ''
}
</script>

<template>
  <AuthView v-if="!token" @logged-in="onLogin" />
  <BookView v-else :username="username" @logout="onLogout" />
</template>
