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

package io.spine.server.storage.hazelcast

import com.hazelcast.config.Config
import com.hazelcast.config.YamlConfigBuilder
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Verifies the Hazelcast configuration shipped with this module.
 *
 * The `hazelcast.yaml` resource makes Delivery servers discover each other by multicast
 * instead of relying on Hazelcast's cloud auto-detection, which starts a member standalone
 * on a cloud host without credentials. No Hazelcast member is started by this spec.
 */
@DisplayName("`storage:hazelcast` should ship a `hazelcast.yaml` which")
internal class HazelcastConfigSpec {

    @Test
    fun `is picked up by Hazelcast instead of the bundled defaults`() {
        val url = Config.load().configurationUrl
        url.shouldNotBeNull()
        url.path shouldEndWith "hazelcast.yaml"
    }

    @Test
    fun `names the cluster after Delivery`() {
        shipped().clusterName shouldBe "delivery"
    }

    @Test
    fun `disables cloud auto-detection`() {
        shipped().networkConfig.join.autoDetectionConfig.isEnabled shouldBe false
    }

    @Test
    fun `enables multicast discovery`() {
        shipped().networkConfig.join.multicastConfig.isEnabled shouldBe true
    }

    @Test
    fun `enables a single discovery mechanism`() {
        shouldNotThrowAny { shipped().networkConfig.join.verify() }
    }

    /**
     * Parses the shipped resource directly, so that `HZ_*` environment variables
     * of the developer machine cannot alter the outcome.
     */
    private fun shipped(): Config {
        val resource = HazelcastStorageFactory::class.java.classLoader.getResource("hazelcast.yaml")
        resource.shouldNotBeNull()
        return YamlConfigBuilder(resource).build()
    }
}
