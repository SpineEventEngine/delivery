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

package io.spine.delivery.client.strategy;

/**
 * An operation that is applied when {@code RequestWithResult} failed.
 *
 * @param <R>
 *         type of the result that has to be returned by the {@code RequestWithResult}. This
 *         action can return a result instead of the request in case of request failure.
 */
public interface ActionWithResult<R> {

    /**
     * Executes the action.
     */
    R execute();
}
