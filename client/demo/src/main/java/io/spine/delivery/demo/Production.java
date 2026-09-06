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

package io.spine.delivery.demo;

import io.spine.environment.CustomEnvironmentType;
import io.spine.environment.Tests;

/**
 * Declares a “production” environment.
 *
 * <p>When this environment is used, the system is meant to run and work as it should run
 * in production.
 */
public final class Production extends CustomEnvironmentType<Production> {

    private static final Production INSTANCE = new Production();

    private Production() {
        super();
    }

    /**
     * Obtains the singleton instance.
     */
    static Production type() {
        return INSTANCE;
    }

    @Override
    protected boolean enabled() {
        var isTests = Tests.type()
                           .enabled();
        return !isTests;
    }

    @Override
    protected Production self() {
        return this;
    }
}
