<!--
  - Copyright (c) 2000-2023 TeamDev. All rights reserved.
  - TeamDev PROPRIETARY and CONFIDENTIAL.
  - Use is subject to license terms.
  -->

<template>
  <q-item v-if="shard">
    <q-item-section>
      <q-item-label>{{shard.index.index}} / {{shard.index.ofTotal}}</q-item-label>
      <q-item-label caption>Messages: {{shard.messages ? shard.messages : 0}}</q-item-label>
      <q-item-label caption v-if="shard.lastPicked">
        Last picked: {{new Date(shard.lastPicked).toLocaleString()}}
      </q-item-label>
      <q-item-label caption v-if="!shard.lastPicked">
        Last picked: Never
      </q-item-label>
    </q-item-section>

    <q-item-section side top>
      <q-item-label caption v-if="shard.status === 'PICKED'" class="bold">Picked</q-item-label>
      <q-item-label caption v-if="shard.status === 'NOT_PICKED'">Not Picked</q-item-label>
    </q-item-section>
  </q-item>
</template>

<script lang="ts" setup>
import { ShardInfo } from 'components/models';

defineProps({ shard: ShardInfo });
</script>

<style scoped>
.bold{
  font-weight: bold;
}
</style>
