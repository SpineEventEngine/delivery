<!--
  - Copyright (c) 2000-2023 TeamDev. All rights reserved.
  - TeamDev PROPRIETARY and CONFIDENTIAL.
  - Use is subject to license terms.
  -->

<template>
  <q-list>
    <div v-for="shard in data.shards" :key="shard.index">
      <shard-info-component :shard="shard"></shard-info-component>
      <q-separator spaced inset />
    </div>
  </q-list>
</template>

<script lang="ts" setup>
import { inject, ref } from 'vue';
import { ShardService } from 'src/services/ShardService';
import ShardInfoComponent from 'components/ShardInfoComponent.vue';

const data = ref({});

const shardService: ShardService = inject(ShardService.name) as ShardService;

shardService.shardInfo().then((received) => {
  data.value = received;
});

</script>

<style scoped>

</style>
