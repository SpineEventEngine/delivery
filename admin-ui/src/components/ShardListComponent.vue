<!--
  - Copyright (c) 2000-2023 TeamDev. All rights reserved.
  - TeamDev PROPRIETARY and CONFIDENTIAL.
  - Use is subject to license terms.
  -->

<template>
  <div class="q-pa-md">
    <q-table
      title="Shards"
      :rows="[...shards.values()]"
      :columns="columns"
      row-key="index.index"
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
            <div v-if="props.row.status === ShardStatus.PICKED" class="bold">Picked</div>
            <div v-else>Not Picked</div>
          </q-td>
          <q-td key="lastPicked" :props="props">
            {{ props.row.lastPicked ? props.row.lastPicked.toDate().toLocaleString() : 'Never' }}
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
import { useShards } from 'src/services/shards';
import { ShardIndex } from 'src/gen/spine/server/delivery/delivery_pb';
import { ShardStatus } from 'src/gen/spine/message/delivery/admin/admin_service_pb';

const { shards } = useShards();

const columns = [
  {
    name: 'index',
    label: 'Index',
    field: 'index',
    align: 'left',
    sortable: true,
    sort: (a: ShardIndex, b: ShardIndex) => {
      return a.index - b.index;
    },
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
