<!--
  - Copyright (c) 2000-2023 TeamDev. All rights reserved.
  - TeamDev PROPRIETARY and CONFIDENTIAL.
  - Use is subject to license terms.
  -->

<template>
  <q-layout view="hHh Lpr lFf">
    <q-header elevated>
      <q-toolbar>
        <q-avatar>
          <q-btn flat @click="isMini = !isMini" round dense icon="menu"/>
          <img src="spine-sign-white.svg" alt="spine-logo">
        </q-avatar>
        <q-toolbar-title>Liquor Admin</q-toolbar-title>
        <q-btn @click="logout"
               v-if="isAuth"
               icon="logout"
               color="primary"
               label="Log Out"
        />
      </q-toolbar>
    </q-header>
    <q-drawer
      v-model="drawer"
      show-if-above

      :mini="isMini"

      :width="200"
      :breakpoint="500"
      bordered
      class="bg-grey-3"
    >
      <q-scroll-area class="fit" :horizontal-thumb-style="{ opacity: 0 }">
        <q-list padding>
          <q-item clickable v-ripple @click="router.push({name:'shardInfo'})">
            <q-item-section avatar>
              <q-icon name="view_timeline"/>
            </q-item-section>
            <q-item-section>Shard Info</q-item-section>
          </q-item>
        </q-list>
      </q-scroll-area>
    </q-drawer>
    <q-page-container>
      <router-view/>
    </q-page-container>
  </q-layout>
</template>

<script lang="ts" setup>
import { AuthService } from 'src/services/AuthService';
import { useRouter } from 'vue-router';
import { ref } from 'vue';

const router = useRouter();
const isAuth = AuthService.isAuthenticated;

const isMini = ref(true);
const drawer = ref(false);

function logout() {
  AuthService.logout();
  router.push('login');
}
</script>
