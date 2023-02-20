<!--
  - Copyright (c) 2000-2023 TeamDev. All rights reserved.
  - TeamDev PROPRIETARY and CONFIDENTIAL.
  - Use is subject to license terms.
  -->

<template>
  <div class="q-pa-md">
    <q-table
      title="Shards"
      :rows="data.shards"
      :columns="columns"
      row-key="name"
    >
      <template v-slot:body="props">
        <q-tr :props="props">
          <q-td key="index" :props="props">
            {{ props.row.index.index }} / {{ props.row.index.ofTotal }}
          </q-td>
          <q-td key="messages" :props="props">
            {{ props.row.messages ? props.row.messages : 0 }}
          </q-td>
          <q-td key="status" :props="props">
            <div v-if="props.row.status === 'PICKED'" class="bold">Picked</div>
            <div v-else>Not Picked</div>
          </q-td>
          <q-td key="lastPicked" :props="props">
            {{ props.row.lastPicked ? new Date(props.row.lastPicked).toLocaleString() : 'Never' }}
          </q-td>
        </q-tr>
      </template>
      <template v-slot:no-data="{ }">
        <div class="full-width column flex-center text-accent q-gutter-sm">
          <div>There are no messages in any shard.</div>
          <div>None of the shards have ever been picked.</div>
        </div>
      </template>
    </q-table>
  </div>
</template>

<script lang="ts" setup>
import { inject, ref } from 'vue';
import { ShardService } from 'src/services/ShardService';
import { ShardIndex, ShardInfoList } from 'components/models';

const data = ref({} as ShardInfoList);

const shardService: ShardService = inject(ShardService.name) as ShardService;

shardService.shardInfo().then((received) => {
  data.value = received;
});

const columns = [
  {
    name: 'index',
    label: 'Index',
    field: 'index',
    align: 'left',
    sortable: true,
    sort: (a: ShardIndex, b: ShardIndex) => a.index - b.index,
  },
  {
    name: 'messages',
    label: 'Messages count',
    field: 'messages',
    sortable: true,
  },
  {
    name: 'status',
    label: 'Status',
    field: 'status',
    sortable: true,
  },
  {
    name: 'lastPicked',
    label: 'Last Picked',
    field: 'lastPicked',
    sortable: true,
  },
];

</script>

<style scoped>
.bold {
  font-weight: bold;
}
</style>
