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

package io.spine.delivery;

import com.google.common.testing.NullPointerTester;
import io.spine.server.delivery.DeliveryBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("`DeliveryBootstrapper` should")
final class DeliveryBootstrapperTest {

    @Test
    @DisplayName("be `NPE`-safe")
    void beNpeSafe() {
        assertThrows(
                NullPointerException.class,
                () -> DeliveryBootstrapper.newInstance().init()
        );
        var tester = new NullPointerTester();
        tester.testAllPublicInstanceMethods(DeliveryBootstrapper.newInstance());
    }

    @Test
    @DisplayName("bootstrap `DeliveryBuilder` configuration")
    void bootstrapDeliveryConfig() {
        var builder = DeliveryBootstrapper.newInstance()
                                          .withChannel(NoOpChannel::new)
                                          .init();
        assertThat(builder.hasInboxStorage())
                .isTrue();
        assertThat(builder.hasWorkRegistry())
                .isTrue();
    }
}
