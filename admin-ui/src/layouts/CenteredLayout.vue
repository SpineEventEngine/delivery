<!--
  ~ Copyright 2026 CodeMatters, Lda.
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
  ~ in compliance with the License. You may obtain a copy of the License at
  ~
  ~ https://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software distributed under the License
  ~ is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
  ~ or implied. See the License for the specific language governing permissions and limitations under
  ~ the License.
  -->

<template>
  <q-layout view="lHh Lpr lFf">
    <q-header elevated>
      <q-toolbar>
        <q-avatar>
          <img src="spine-sign-white.svg" alt="spine-logo">
        </q-avatar>
        <q-toolbar-title >Delivery Server Admin</q-toolbar-title>
      </q-toolbar>
    </q-header>
    <q-page-container>
      <router-view class="row items-center justify-evenly"/>
    </q-page-container>
  </q-layout>
</template>

<script lang="ts" setup>

</script>
