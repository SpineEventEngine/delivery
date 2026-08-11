/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

import io.spine.dependency.local.CoreJvm

dependencies {
    api(project(":delivery-model"))
    api(CoreJvm.serverTestLib)
}
