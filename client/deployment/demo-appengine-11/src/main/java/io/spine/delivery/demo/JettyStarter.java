/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.demo;

import org.eclipse.jetty.annotations.AnnotationConfiguration;
import org.eclipse.jetty.plus.webapp.EnvConfiguration;
import org.eclipse.jetty.plus.webapp.PlusConfiguration;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.Configuration;
import org.eclipse.jetty.webapp.FragmentConfiguration;
import org.eclipse.jetty.webapp.JettyWebXmlConfiguration;
import org.eclipse.jetty.webapp.MetaInfConfiguration;
import org.eclipse.jetty.webapp.WebAppConfiguration;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.webapp.WebInfConfiguration;
import org.eclipse.jetty.webapp.WebXmlConfiguration;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;
import static java.lang.Integer.parseInt;

/**
 * Demo application-specific server that sets up logging and starts the server.
 *
 * @implNote Uses {@linkplain Server Jetty's server} to run the web application.
 */
public final class JettyStarter {

    /**
     * An amount of seconds in 24 hours.
     */
    private static final int DAY = 86400;

    private static final String PORT_ENV_NAME = "PORT";
    private static final String CURRENT_DIRECTORY = ".";

    private final Server server;

    private JettyStarter(Builder builder) {
        super();
        this.server = new Server(builder.port);
        server.setHandler(webAppContext());
        builder.beans.forEach(server::addBean);
    }

    /**
     * Starts Jetty server.
     */
    public static void main(String[] args) {
        var starter = newBuilder().build();
        Runtime.getRuntime()
               .addShutdownHook(new Thread(starter::stop));
        starter.start();
    }

    /**
     * Runs the server and blocks the execution until the server is stopped.
     */
    public void start() {
        try {
            server.start();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to start Jetty server.", e);
        }
        try {
            server.join();
        } catch (InterruptedException ignored) {
            // there's nothing we can do when the server is interrupted.
        }
    }

    /**
     * Stops the server.
     */
    public void stop() {
        try {
            server.stop();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to stop Jetty server.", e);
        }
    }

    /**
     * Creates a new {@code WebAppContext} with the {@code web.xml} descriptor from classpath and
     * resource base set to the folder where the application was run from.
     */
    private static WebAppContext webAppContext() {
        var webapp = new WebAppContext();
        webapp.getSessionHandler()
              .setMaxInactiveInterval(DAY);
        webapp.setResourceBase(CURRENT_DIRECTORY);
        webapp.setAttribute(
                "org.eclipse.jetty.server.webapp.ContainerIncludeJarPattern",
                ".*"
        );
        webapp.setConfigurations(new Configuration[]{
                new AnnotationConfiguration(),
                new WebInfConfiguration(),
                new WebXmlConfiguration(),
                new MetaInfConfiguration(),
                new FragmentConfiguration(),
                new EnvConfiguration(),
                new PlusConfiguration(),
                new JettyWebXmlConfiguration(),
                new WebAppConfiguration()
        });
        return webapp;
    }

    /**
     * Creates a new {@code Builder}.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * A {@code Builder} for the {@linkplain JettyStarter}.
     */
    public static final class Builder {

        /**
         * A collection of beans to be added to the {@linkplain Server#addBean(Object)} method.
         */
        private final List<Object> beans = new ArrayList<>();
        private int port;

        /**
         * A port to listen for incoming server connections.
         */
        public Builder port(int port) {
            checkArgument(port > 0);
            this.port = port;
            return this;
        }

        /**
         * Adds the {@code bean} to the {@linkplain Server} to modify its behaviour.
         *
         * @see Server#addBean(Object)
         */
        public Builder withBean(Object bean) {
            checkNotNull(bean);
            this.beans.add(bean);
            return this;
        }

        /**
         * Creates a new {@code JettyStarter} instance.
         */
        public JettyStarter build() {
            port = port == 0 ? portFromEnvVariable() : port;
            return new JettyStarter(this);
        }

        /**
         * Gets the port form the {@code PORT} environment variable, or returns {@code 8080}
         * if not set.
         */
        @SuppressWarnings("CallToSystemGetenv") // We want to use environment variables.
        private static int portFromEnvVariable() {
            var port = System.getenv(PORT_ENV_NAME);
            port = isNullOrEmpty(port) ? "8080" : port;
            return parseInt(port);
        }
    }
}
