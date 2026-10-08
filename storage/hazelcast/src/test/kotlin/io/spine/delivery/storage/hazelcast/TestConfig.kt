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

import com.hazelcast.config.Config
import java.util.UUID

/**
 * Creates the configuration of a test member that forms a cluster of its own, unless
 * other members are given the same [clusterName].
 *
 * Multicast and cloud auto-detection are off, so that a test member never joins
 * the members of other tests or of a running Delivery server. Members of one cluster
 * find each other over TCP/IP on the loopback interface.
 */
internal fun testConfig(clusterName: String = "delivery-test-${UUID.randomUUID()}"): Config {
    val config = Config()
        .setClusterName(clusterName)
        .setProperty("hazelcast.phone.home.enabled", "false")
        .setProperty("hazelcast.logging.type", "none")
    val join = config.networkConfig.join
    join.multicastConfig.isEnabled = false
    join.autoDetectionConfig.isEnabled = false
    join.tcpIpConfig.setEnabled(true).addMember("127.0.0.1")
    config.networkConfig.interfaces.setEnabled(true).addInterface("127.0.0.1")
    return config
}
