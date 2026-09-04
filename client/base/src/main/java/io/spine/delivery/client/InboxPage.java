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

package io.spine.delivery.client;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.Timestamp;
import io.spine.server.delivery.InboxMessage;
import io.spine.server.delivery.InboxStorage;
import io.spine.server.delivery.Page;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A single page of the messages read from the {@link InboxStorage}.
 *
 * <p>Provides the navigation to the next page basing on the time of the last message read in the
 * scope of the current page.
 */
final class InboxPage implements Page<InboxMessage> {

    private final Lookup lookup;
    private final ImmutableList<InboxMessage> contents;

    private @MonotonicNonNull Timestamp whenLastRead = null;

    /**
     * Creates a new page with the specified way to read the next messages.
     */
    InboxPage(Lookup lookup) {
        this.lookup = checkNotNull(lookup);
        this.contents = readNext();
    }

    /**
     * Creates a page next to the previous one, with the initial contents pre-loaded.
     *
     * @param previous
     *         page that preceded the one being created
     * @param initialContents
     *         the initial contents of this newly created page instance
     */
    private InboxPage(InboxPage previous, ImmutableList<InboxMessage> initialContents) {
        this.lookup = previous.lookup;
        this.whenLastRead = previous.whenLastRead;
        this.contents = initialContents;
    }

    /**
     * Loads the content for the next page and returns a new instance of the {@code InboxPage}.
     *
     * <p>In case there were no messages loaded, this page is considered to be the last one,
     * and {@code Optional.empty()} is returned.
     */
    @Override
    public Optional<Page<InboxMessage>> next() {
        var moreContent = readNext();
        if (moreContent.isEmpty()) {
            return Optional.empty();
        }
        var nextPage = new InboxPage(this, moreContent);
        return Optional.of(nextPage);
    }

    private ImmutableList<InboxMessage> readNext() {
        var contents = lookup.readAll(whenLastRead);
        if (!contents.isEmpty()) {
            this.whenLastRead = contents.get(contents.size() - 1)
                                        .getWhenReceived();
        }
        return contents;
    }

    @Override
    public ImmutableList<InboxMessage> contents() {
        return contents;
    }

    @Override
    public int size() {
        return contents().size();
    }

    /**
     * A strategy on fetching the {@link InboxMessage}s from the storage based
     * on the passed timestamp.
     */
    interface Lookup {

        /**
         * Reads the messages that were received strictly later than the specified
         * {@code sinceWhen} value.
         *
         * <p>If the passed value is {@code null}, the time filtering is not applied.
         *
         * @param sinceWhen
         *         the time since when the messages should be read; all satisfying messages
         *         must be received strictly later than this value;
         *         {@code null} if no filtering should be applied
         * @return the iterator over the results
         */
        ImmutableList<InboxMessage> readAll(@Nullable Timestamp sinceWhen);
    }
}
