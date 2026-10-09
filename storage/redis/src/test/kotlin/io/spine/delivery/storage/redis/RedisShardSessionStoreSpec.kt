/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package io.spine.delivery.storage.redis

import io.spine.delivery.storage.ShardSessionStore
import io.spine.delivery.storage.ShardSessionStoreContract
import java.time.Duration
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.DisplayName

@DisplayName("`RedisShardSessionStore` should")
@RequiresDocker
internal class RedisShardSessionStoreSpec : ShardSessionStoreContract() {

    /**
     * Redis reports the changes through its channels, after the operation returns, so
     * a test waits this long to make sure that no other change is reported.
     */
    override val quietPeriod: Duration = Duration.ofMillis(300)

    override fun newStore(): ShardSessionStore {
        redis.clear()
        return RedisShardSessionStore(redis.client)
    }

    companion object {

        /**
         * The Redis server shared by all the tests, started by the first of them.
         */
        private val redis by lazy { TestRedis() }

        @JvmStatic
        @AfterAll
        fun stopRedis() {
            redis.close()
        }
    }
}
