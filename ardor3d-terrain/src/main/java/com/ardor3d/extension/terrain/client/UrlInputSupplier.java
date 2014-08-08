
package com.ardor3d.extension.terrain.client;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import com.google.common.io.ByteSource;

public class UrlInputSupplier extends ByteSource {
    private final URL url;

    public UrlInputSupplier(final URL url) {
        this.url = url;
    }

    @Override
    public InputStream openStream() throws IOException {
        return url.openStream();
    }
}
