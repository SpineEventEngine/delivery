/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

import { ref } from 'vue';
import { AuthService } from 'src/services/AuthService';
import { useRouter } from 'vue-router';
import { useEndpoints } from 'src/services/endpoints';
import { useAxios } from '@vue-composable/axios';
import { fetchEventSource } from '@microsoft/fetch-event-source';
import {
  ShardInfo,
  ShardInfoList,
  ShardInfoUpdate,
  ShardStatus,
} from 'src/gen/spine/delivery/admin/admin_service_pb';
import { ShardIndex } from 'src/gen/spine/server/delivery/delivery_pb';

/**
 * A composable that gives access to shard information from the server.
 *
 * The shard info will be updated automatically on server updates.
 */
export function useShards() {
  const shards = ref(new Map());
  const isLoaded = ref(false);

  const router = useRouter();
  const { shardInfo, shardUpdates } = useEndpoints();
  const { client } = useAxios();

  /**
   * Reads the information about currently known shards from the server,
   * and subscribes to the server-side updates.
   *
   * Returns a `Promise` that resolves when the ShardInfo received.
   */
  function synchronizeShardInfo(): Promise<ShardInfoList> {
    return new Promise<ShardInfoList>((resolve, reject) => {
      const options = {
        auth: {
          username: AuthService.username(),
          password: AuthService.password(),
        },
      };
      client.get(`${shardInfo}`, options)
        .then((response) => {
          resolve(ShardInfoList.fromJson(response.data));
        })
        .catch((e) => {
          if (e.code === 'ERR_BAD_REQUEST' && e.response.status === 401) {
            router.push('login');
          } else {
            reject(e);
          }
        });
    });
  }

  /**
   * Returns the info of the existing shard, or creates and remembers a new one,
   * with a default value.
   */
  function getShard(index: ShardIndex): ShardInfo {
    const key = index.toJsonString();
    if (shards.value.has(key)) {
      return shards.value.get(key);
    }
    const info = new ShardInfo();
    info.index = index;
    info.messages = 0;
    info.status = ShardStatus.NOT_PICKED;
    shards.value.set(key, info);
    return shards.value.get(key);
  }

  /**
   * Applies the given `update` to a stored shard info.
   */
  function applyUpdate(update: ShardInfoUpdate) {
    const shard = getShard(update.index as ShardIndex);
    if (update.newMessagesCount) {
      shard.messages = update.newMessagesCount;
    }
    if (update.newStatus) {
      shard.status = update.newStatus;
    }
    if (update.whenLastPicked) {
      shard.lastPicked = update.whenLastPicked;
    }
  }

  /**
   * Subscribes to the event source on the server, and updates stored shard info according to
   * received updates.
   */
  function subscribeOnShards() {
    const credentials = btoa(`${AuthService.username()}:${AuthService.password()}`);
    const authHeader = { Authorization: `Basic ${credentials}` };
    fetchEventSource(shardUpdates, {
      headers: authHeader,
      onmessage(event) {
        const infoUpdate = ShardInfoUpdate.fromJsonString(event.data);
        applyUpdate(infoUpdate);
      },
    });
  }

  synchronizeShardInfo().then((shardInfoList) => {
    shardInfoList.shards?.forEach((info) => {
      shards.value.set((info.index as ShardIndex).toJsonString(), info);
    });
    isLoaded.value = true;
    subscribeOnShards();
  });

  return { shards, isLoaded };
}
