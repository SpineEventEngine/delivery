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
  AuthService.login(login.value, password.value)
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
