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

package io.spine.dependency.web

/**
 * [Eclipse Jetty](https://jetty.org/) — the servlet container embedded by
 * the `demo-appengine-11` deployment module.
 *
 * The version stays on the Jetty 10 line — the last one serving the `javax.servlet`
 * API used by the `client:demo` servlets and the App Engine Java runtime.
 *
 * This file is owned by this repository — the `config` distribution
 * does not declare Jetty.
 */
@Suppress("unused", "ConstPropertyName")
object Jetty {
    // https://central.sonatype.com/artifact/org.eclipse.jetty/jetty-server
    private const val version = "10.0.6"
    const val server = "org.eclipse.jetty:jetty-server:$version"
    const val servlet = "org.eclipse.jetty:jetty-servlet:$version"
    const val webapp = "org.eclipse.jetty:jetty-webapp:$version"
    const val annotations = "org.eclipse.jetty:jetty-annotations:$version"
    val all = listOf(server, servlet, webapp, annotations)
}
