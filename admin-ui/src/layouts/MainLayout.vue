<template>
  <q-layout view="lHh Lpr lFf">
    <q-header elevated>
      <q-toolbar>
        <q-avatar>
          <img src="spine-sign-white.svg" alt="spine-logo">
        </q-avatar>
        <q-toolbar-title >Liquor Admin</q-toolbar-title>
        <q-btn @click="logout"
               v-if="isAuth"
               icon="logout"
               color="primary"
               label="Log Out"
        />
      </q-toolbar>
    </q-header>
    <q-page-container>
      <router-view/>
    </q-page-container>
  </q-layout>
</template>

<script lang="ts" setup>
import { AuthService } from "src/services/AuthService";
import { useRouter } from "vue-router";

const router = useRouter();
const isAuth = AuthService.isAuthenticated;

function logout() {
  AuthService.logout();
  router.push("login");
}
</script>
