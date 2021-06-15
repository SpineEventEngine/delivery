/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server;

import io.spine.server.aggregate.Aggregate;
import io.spine.server.delivery.ShardIndex;

final class SessionRegistry
        extends Aggregate<ShardIndex, ShardSessionRegistry, ShardSessionRegistry.Builder> {

}
