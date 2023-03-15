/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

/**
 * Server API endpoints.
 */
export function useEndpoints() {
  const shardInfo = '/admin/shardInfo';
  const shardUpdates = '/admin/shardUpdates';

  return { shardInfo, shardUpdates };
}
