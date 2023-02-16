<!--
  - Copyright (c) 2000-2023 TeamDev. All rights reserved.
  - TeamDev PROPRIETARY and CONFIDENTIAL.
  - Use is subject to license terms.
  -->

<template>
  <q-list bordered padding>
    <q-item>
      <q-item-section>
        <q-item-label header>Shard info</q-item-label>
        <q-item-label caption>
          This table shows the current state of shards on the Liquor server.
        </q-item-label>
      </q-item-section>
    </q-item>
    <q-separator spaced/>
    <q-item>
      <q-item-label overline>Shards:</q-item-label>
    </q-item>
    <div v-for="shard in data.shards" :key="shard.index">
      <shard-info-component :shard="shard"></shard-info-component>
      <q-separator spaced inset/>
    </div>
  </q-list>
</template>

<script lang="ts" setup>
import { inject, ref } from 'vue';
import { ShardService } from 'src/services/ShardService';
import ShardInfoComponent from "components/ShardInfoComponent.vue";

const data = ref({});

const shardService: ShardService = inject(ShardService.name)!;

shardService.shardInfo().then((received) => {
  data.value = received;
});

</script>

<style scoped>

</style>
