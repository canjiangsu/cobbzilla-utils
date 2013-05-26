package org.cobbzilla.util.io;

import org.apache.commons.io.IOUtils;

import java.io.*;

public class StreamUtil {

    public static final String SUFFIX = ".tmp";
    public static final String PREFIX = "stream2file";

    public static File stream2file (InputStream in) throws IOException {
        final File tempFile = File.createTempFile(PREFIX, SUFFIX);
        tempFile.deleteOnExit();
        try (OutputStream out = new FileOutputStream(tempFile)) {
            IOUtils.copy(in, out);
        }
        return tempFile;
    }

}