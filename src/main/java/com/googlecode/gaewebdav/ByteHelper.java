package com.googlecode.gaewebdav;

import java.io.*;

/**
 * @author Will
 * @date Oct 19, 2009 - 12:01:03 PM
 */
public class ByteHelper {
    private static final int BUFFER_SIZE = 65536;

    public static byte[] loadBytes(InputStream is) throws IOException {
        byte[] bytes;
        if (is instanceof ByteArrayInputStream) {
            bytes = new byte[is.available()];
            int sizeRead = is.read(bytes);
            //the next byte of data, or -1 if the end of the stream has been reached.
            if (((sizeRead != bytes.length) && (sizeRead != -1)) || (is.available() != 0)) {
                throw new IOException("ByteHelper.loadBytes optimized read for ByteArrayInputStream failed");
            }
        } else {
            BufferedInputStream bis = new BufferedInputStream(is);
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            copy(os, bis);
            bytes = os.toByteArray();
        }
        return bytes;
    }

    public static int copy(OutputStream bos, InputStream is) throws IOException {
        byte[] bytes = new byte[BUFFER_SIZE];
        return copy(bos, is, bytes);
    }


    public static int copy(OutputStream bos, InputStream is, byte[] bytes) throws IOException {
        int total = 0;
        while (true) {
            int read = is.read(bytes);
            if (read == -1) {
                // end of file reached
                break;
            }
            bos.write(bytes, 0, read);
            total += read;
        }
        bos.flush();
        return total;
    }
}
