/*
 * Copyright 2021, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Redistribution and use in source and/or binary forms, with or without
 * modification, must retain the above copyright notice and the following
 * disclaimer.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.spine.server.storage.redis;

import com.google.protobuf.Message;
import io.spine.server.ContextSpec;
import io.spine.server.storage.RecordSpec;
import io.spine.server.storage.StorageFactory;
import org.redisson.api.RedissonClient;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A factory for in-memory storages.
 */
public final class RedisStorageFactory implements StorageFactory {

    private final RedissonClient client;

    private RedisStorageFactory(RedissonClient client) {
        this.client = checkNotNull(client);
    }

    /**
     * Creates new instance of the factory which would serve the specified context.
     *
     * @return new instance of the factory
     */
    @SuppressWarnings("ConstantConditions" /* temporary stub. */)
    public static RedisStorageFactory newInstance() {
        return new RedisStorageFactory(null);
    }

    @Override
    public <I, M extends Message> RedisRecordStorage<I, M>
    createRecordStorage(ContextSpec context, RecordSpec<I, M, ?> spec) {
        return new RedisRecordStorage<>(context, spec, client);
    }

    @Override
    public void close() {
        // NOP
    }
}
