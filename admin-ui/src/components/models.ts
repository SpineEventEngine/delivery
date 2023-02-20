/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

/**
 * An identifier of the shard.
 */
export interface ShardIndex {
  index: number,
  ofTotal: number,
}

/**
 * An information about shards.
 */
export class ShardInfo {
  index: ShardIndex;

  /**
   * Number of messages in the shard.
   */
  messages: number;

  /**
   * Shard status: `PICKED` or `NOT_PICKED`.
   */
  status: string;

  /**
   * When the shard was last picked by a worker.
   *
   * Can be unset if the shard have never been picked.
   */
  lastPicked: Date;

  constructor(
    index: ShardIndex,
    messages: number,
    status: string,
    lastPicked: Date,
  ) {
    this.index = index;
    this.messages = messages;
    this.status = status;
    this.lastPicked = lastPicked;
  }
}

/**
 * A list of `ShardInfo` types.
 */
export interface ShardInfoList {
  shards: [ShardInfo]
}
