/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
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
} from 'src/gen/spine/message/delivery/admin/admin_service_pb';
import { ShardIndex } from 'src/gen/spine/server/delivery/delivery_pb';

export function useShards() {
  const shards = ref(new Map());
  const isLoaded = ref(false);

  const router = useRouter();
  const { shardInfo, shardUpdates } = useEndpoints();
  const { client } = useAxios();

  /**
   * Requests current shard status from the server.
   */
  function getShardInfo(): Promise<ShardInfoList> {
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

  function startUpdateInfo() {
    const credentials = btoa(`${AuthService.username()}:${AuthService.password()}`);
    const authHeader = { Authorization: `Basic ${credentials}` };
    fetchEventSource(shardUpdates, {
      headers: authHeader,
      onmessage(event) {
        const update = ShardInfoUpdate.fromJsonString(event.data);
        const shard = getShard(update.index!);
        if (update.messagesCountUpdatedTo) {
          shard.messages = update.messagesCountUpdatedTo;
        }
        if (update.statusUpdatedTo) {
          shard.status = update.statusUpdatedTo;
        }
        if (update.lastPickedUpdatedTo) {
          shard.lastPicked = update.lastPickedUpdatedTo;
        }
      },
    }).then();
  }

  getShardInfo().then((shardInfoList) => {
    shardInfoList.shards?.forEach((info) => {
      shards.value.set(info.index!.toJsonString(), info);
    });
    isLoaded.value = true;
    startUpdateInfo();
  });

  return { shards, isLoaded };
}
