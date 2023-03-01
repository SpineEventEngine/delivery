/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

import axios from 'axios';
import { AuthService } from 'src/services/AuthService';
import { useEndpoints } from 'src/services/endpoints';
import router from 'src/router/index';
import { ShardInfoList } from 'components/models';

/**
 * Allows getting shard information.
 */
export class ShardService {
  private endpoints = useEndpoints();

  /**
   * Requests current shard status from the server.
   */
  shardInfo(): Promise<ShardInfoList> {
    return new Promise<ShardInfoList>((resolve, reject) => {
      axios.get(`${this.endpoints.shardInfo}`, AuthService.authOptions())
        .then((response) => {
          resolve(response.data);
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
}
