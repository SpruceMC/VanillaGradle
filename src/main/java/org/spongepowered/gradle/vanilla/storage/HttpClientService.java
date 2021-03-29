/*
 * This file is part of VanillaGradle, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.gradle.vanilla.storage;

import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.async.MinimalHttpAsyncClient;
import org.apache.hc.core5.http.nio.entity.AbstractBinDataConsumer;
import org.apache.hc.core5.http.nio.entity.DigestingEntityConsumer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.spongepowered.gradle.vanilla.Constants;

import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;

public abstract class HttpClientService implements BuildService<BuildServiceParameters.None>, AutoCloseable {
    private final CloseableHttpAsyncClient client;

    public HttpClientService() {
        // TODO: Custom TLS strategy needed?
        // https://github.com/apache/httpcomponents-client/blob/5.0.x/httpclient5/src/test/java/org/apache/hc/client5/http/examples/AsyncClientTlsAlpn.java#L57
        final IOReactorConfig config = IOReactorConfig.custom()
            .setSoTimeout(Timeout.ofSeconds(5))
            .build();

        this.client = HttpAsyncClientBuilder.create()
            .setIOReactorConfig(config)
            .setUserAgent(Constants.USER_AGENT)
            .setRetryStrategy(new DefaultHttpRequestRetryStrategy(5, TimeValue.ofMilliseconds(500)))
            .build();

    }

    public static BasicResponseConsumer<Path> responseToFile(final Path path) {
        return new BasicResponseConsumer<>(new ToPathEntityConsumer(path));
    }

    public static BasicResponseConsumer<Path> responseToFileValidating(final Path path, final String sha1) {
        try {
            return new BasicResponseConsumer<>(new Sha1ValidatingDigestingEntityConsumer<>(new ToPathEntityConsumer(path), sha1));
        } catch (final NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Could not resolve SHA-1 algorithm");
        }
    }

    public CloseableHttpAsyncClient client() {
        this.client.start();
        return this.client;
    }

    @Override
    public void close() {
        this.client.close(CloseMode.GRACEFUL);
    }
}