package net.lapismc.maxbans.sync;

import net.lapismc.maxbans.MaxBans;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class SyncUtil {

    static {
        "abcdefghijklmnopqrstuvwrxyzABCDEFGHIJKLMNOPQRSTUVWRXYZ0123456789".toCharArray();
    }

    static boolean isDebug() {
        return MaxBans.instance.getConfig().getBoolean("sync.debug");
    }

    private static String convertedToHex(final byte[] data) {
        final StringBuilder buf = new StringBuilder();
        for (byte aData : data) {
            int halfOfByte = aData >>> 4 & 0xF;
            int twoHalfBytes = 0;
            do {
                if (halfOfByte >= 0 && halfOfByte <= 9) {
                    buf.append((char) (48 + halfOfByte));
                } else {
                    buf.append((char) (97 + (halfOfByte - 10)));
                }
                halfOfByte = (aData & 0xF);
            } while (twoHalfBytes++ < 1);
        }
        return buf.toString();
    }

    private static String MD5(final String text) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        final MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] md2;
        md.update(text.getBytes("ISO-8859-1"), 0, text.length());
        md2 = md.digest();
        return convertedToHex(md2);
    }

    static String encrypt(final String text) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        return MD5(String.valueOf(MD5(text)) + "fuQJ7_q#eF78A&D");
    }
}
