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
import { ShardStatus } from 'src/gen/spine/delivery/admin/admin_service_pb';

const { shards } = useShards();

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
