/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.internal.dependency

// https://www.eclipse.org/jetty/
object Jetty {
    private const val version = "10.0.6"
    const val server = "org.eclipse.jetty:jetty-server:${version}"
    const val servlet = "org.eclipse.jetty:jetty-servlet:${version}"
    const val webapp = "org.eclipse.jetty:jetty-webapp:${version}"
    const val annotations = "org.eclipse.jetty:jetty-annotations:${version}"

    val all = listOf(server, servlet, webapp, annotations)
}
