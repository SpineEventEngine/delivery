<template>
  <q-page class="row items-center justify-evenly">
    <router-view></router-view>
  </q-page>
</template>

<script lang="ts">
import { defineComponent } from 'vue';

export default defineComponent({
  name: 'IndexPage',
  components: { },
});
</script>
