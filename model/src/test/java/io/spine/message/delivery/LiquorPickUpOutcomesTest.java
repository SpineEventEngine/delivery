/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery;

import io.spine.message.delivery.grpc.LiquorPickUpOutcome;
import io.spine.testing.UtilityClassTest;
import org.junit.jupiter.api.DisplayName;

@DisplayName("`LiquorPickUpOutcome` utility class should")
class LiquorPickUpOutcomesTest extends UtilityClassTest<LiquorPickUpOutcome> {

    LiquorPickUpOutcomesTest() {
        super(LiquorPickUpOutcome.class);
    }
}
