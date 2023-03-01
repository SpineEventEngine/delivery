/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

/**
 * Server API endpoints.
 */
export function useEndpoints() {
  const shardInfo = 'http://localhost:8080/admin/shardInfo';
  const shardUpdates = 'http://localhost:8080/admin/shardUpdates';

  return { shardInfo, shardUpdates };
}
