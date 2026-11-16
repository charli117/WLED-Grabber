package com.abrenoch.wledgrabber.common.fixtures;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Loads the shared WLED JSON fixtures from test resources. */
public final class DeviceJsonFixture {
    private DeviceJsonFixture() {
    }

    public static String infoJson() {
        return read("/fixtures/wled-info.json");
    }

    public static String stateJson() {
        return read("/fixtures/wled-state.json");
    }

    private static String read(String resource) {
        try (InputStream input = DeviceJsonFixture.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("missing fixture resource: " + resource);
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toString("UTF-8");
        } catch (IOException e) {
            throw new IllegalStateException("unable to read " + resource, e);
        }
    }
}
