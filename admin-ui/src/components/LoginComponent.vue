<!--
  - Copyright (c) 2000-2023 TeamDev. All rights reserved.
  - TeamDev PROPRIETARY and CONFIDENTIAL.
  - Use is subject to license terms.
  -->

<template>
  <div class="q-pa-md holder">
    <q-form
      @submit="onSubmit"
      @reset="onReset"
      class="q-gutter-md"
    >
      <q-input
        filled
        v-model=login
        label="Login"
        lazy-rules
        :rules="[ val => val && val.length > 0 || 'Login cannot be empty']"
      />
      <q-input
        filled
        type="password"
        v-model=password
        label="Password"
        lazy-rules
        :rules="[val => val && val.length > 0 || 'Password cannot be empty']"
      />
      <div>
        <q-btn label="Submit" type="submit" color="primary"/>
        <q-btn label="Clear" type="reset" color="primary" flat class="q-ml-sm"/>
      </div>
    </q-form>
  </div>
</template>

<script lang="ts" setup>
import { ref } from 'vue';
import { useQuasar } from 'quasar';
import { AuthService } from 'src/services/AuthService';
import { useRouter } from 'vue-router';

const $q = useQuasar();

const router = useRouter();

const login = ref('');
const password = ref('');

function onSubmit() {
  AuthService.tryLogin(login.value, password.value)
    .then((authenticated) => {
      if (authenticated) {
        router.push({ path: '/admin' });
        login.value = '';
        password.value = '';
      } else {
        $q.notify({
          color: 'red-5',
          textColor: 'white',
          icon: 'warning',
          message: 'Incorrect login or password',
        });
      }
    })
    .catch((error) => {
      $q.notify({
        color: 'red-5',
        textColor: 'white',
        icon: 'error',
        message: `Unexpected error: '${error.message}'`,
      });
    });
}

function onReset() {
  login.value = '';
  password.value = '';
}
</script>

<style lang="scss" scoped>
.holder {
  min-width: 300px;
}
</style>
