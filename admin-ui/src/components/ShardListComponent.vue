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
    <div v-if="data.shards && data.shards.length > 0">
      <q-item>
        <q-item-label overline>Shards:</q-item-label>
      </q-item>
      <div v-for="shard in data.shards" :key="shard.index">
        <shard-info-component :shard="shard"></shard-info-component>
        <q-separator spaced inset/>
      </div>
    </div>
    <div v-else>
      <q-item class="centered">
        <q-item-section>
          <q-item-label overline>No shards found</q-item-label>
          <q-separator spaced inset vertical/>
          <q-item-label caption>There are no messages in any shard.</q-item-label>
          <q-item-label caption>None of the shards have ever been picked.</q-item-label>
        </q-item-section>
      </q-item>
    </div>
  </q-list>
</template>

<script lang="ts" setup>
import { inject, ref } from 'vue';
import { ShardService } from 'src/services/ShardService';
import ShardInfoComponent from "components/ShardInfoComponent.vue";
import { ShardInfoList } from "components/models";

const data = ref({} as ShardInfoList);

const shardService: ShardService = inject(ShardService.name) as ShardService;

shardService.shardInfo().then((received) => {
  data.value = received;
});

</script>

<style scoped>
.centered {
  text-align: center;
}
</style>
