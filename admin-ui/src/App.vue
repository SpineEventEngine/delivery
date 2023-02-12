<template>
  <router-view/>
</template>

<script lang="ts" setup>

import { provide } from 'vue';
import { ShardService } from 'src/services/ShardService';

provide(ShardService.name, new ShardService());

</script>
