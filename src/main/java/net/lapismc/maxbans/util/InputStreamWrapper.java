package net.lapismc.maxbans.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

public class InputStreamWrapper extends InputStream {
    private InputStream i;

    public InputStreamWrapper(final InputStream in) {
        super();
        this.i = in;
    }

    public String readString() {
        final ByteArrayOutputStream data = new ByteArrayOutputStream();
        byte b;
        while ((b = this.readByte()) != 0) {
            data.write(b);
        }
        try {
            return new String(data.toByteArray(), "ISO-8859-1");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        }
    }

    public byte readByte() {
        return (byte) this.read();
    }

    public void close() {
        try {
            this.i.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public int read() {
        try {
            final int n = this.i.read();
            if (n < 0) {
                throw new RuntimeException("Socket is closed!");
            }
            return n;
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public int available() {
        try {
            return this.i.available();
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public long skip(final long n) {
        try {
            return this.i.skip(n);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
    }
}
