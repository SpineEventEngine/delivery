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

/**
 * Client implementation of Message Delivery.
 *
 * <p>The {@code delivery-client-base} module declares the same package.
 * Only one {@code package-info} wins at run time — which one depends on
 * the classpath order — so the annotations here must stay identical to
 * those of the {@code base} declaration.
 */
@CheckReturnValue
@JvmLoggingDomain("Delivery Client")
@NullMarked
package io.spine.delivery.client;

import com.google.errorprone.annotations.CheckReturnValue;

import io.spine.logging.JvmLoggingDomain;

import org.jspecify.annotations.NullMarked;
