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

package io.spine.delivery.admin;

import io.spine.delivery.admin.grpc.ShardInfoUpdate;
import io.spine.delivery.admin.grpc.SubscriptionResponse;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Utility that helps to create {@code SubscriptionResponse}s.
 */
public final class SubscriptionResponses {

    private SubscriptionResponses() {
    }

    /**
     * Creates a new {@code SubscriptionResponse} with the acknowledgement of the subscription.
     */
    public static SubscriptionResponse ack() {
        return SubscriptionResponse.newBuilder()
                .setCreated(true)
                .build();
    }

    /**
     * Creates a new {@code SubscriptionResponse} with the given {@code update}.
     */
    public static SubscriptionResponse toResponse(ShardInfoUpdate update) {
        checkNotNull(update);
        return SubscriptionResponse.newBuilder()
                .setUpdate(update)
                .build();
    }
}
