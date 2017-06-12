package net.lapismc.maxbans.util;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

public class OutputStreamWrapper extends OutputStream {
    private OutputStream o;

    public OutputStreamWrapper(final OutputStream out) {
        super();
        this.o = out;
    }

    public void write(final String s) {
        byte[] data;
        try {
            data = s.getBytes("ISO-8859-1");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            data = new byte[0];
        }
        byte[] array;
        for (int length = (array = data).length, i = 0; i < length; ++i) {
            final byte b = array[i];
            this.write(b);
        }
        this.write(0);
    }

    public void close() {
        try {
            this.o.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public void write(final int b) {
        try {
            this.o.write(b);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
    }
}
