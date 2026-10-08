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

package io.spine.delivery.storage.hazelcast

import io.spine.delivery.storage.MissedChangeListeners
import io.spine.delivery.storage.ShardSessionStore
import io.spine.delivery.storage.ShardSessionStoreContract
import java.time.Duration
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.DisplayName

@DisplayName("`HazelcastShardSessionStore` should")
internal class HazelcastShardSessionStoreSpec : ShardSessionStoreContract() {

    override val quietPeriod: Duration = Duration.ofMillis(300)

    override fun newStore(): ShardSessionStore {
        val map = member.getMap<String, ByteArray>(SESSIONS_MAP)
        map.clear()
        return HazelcastShardSessionStore(map, MissedChangeListeners())
    }

    companion object {

        private val member = com.hazelcast.core.Hazelcast.newHazelcastInstance(
            HazelcastStores.configure(testConfig())
        )

        @JvmStatic
        @AfterAll
        fun shutDown() {
            member.shutdown()
        }
    }
}
