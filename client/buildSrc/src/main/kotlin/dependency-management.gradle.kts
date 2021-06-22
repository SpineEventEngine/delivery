/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

import io.spine.internal.dependency.CheckerFramework
import io.spine.internal.dependency.ErrorProne
import io.spine.internal.dependency.FindBugs
import io.spine.internal.dependency.Flogger
import io.spine.internal.dependency.Gson
import io.spine.internal.dependency.Guava
import io.spine.internal.dependency.JavaX
import io.spine.internal.dependency.Protobuf
import io.spine.internal.dependency.Spine
import io.spine.internal.dependency.Truth

plugins {
    `java-library`
}

configurations.all {
    resolutionStrategy {
        force(
            CheckerFramework.annotations,
            ErrorProne.annotations,
            Guava.lib,
            Guava.testLib,
            FindBugs.annotations,
            Flogger.lib,
            Gson.lib,
            JavaX.annotations,
            Protobuf.libs,
            Truth.libs,
            Spine.Stable.base,
            Spine.Stable.core,
            Spine.Stable.server,
            Spine.Stable.client,
            Spine.Stable.time,
            Spine.Stable.Test.base,
            Spine.Stable.Test.core,
            Spine.Stable.Test.server,
            Spine.Stable.Test.client,
            Spine.Stable.Test.time
        )
    }
}
