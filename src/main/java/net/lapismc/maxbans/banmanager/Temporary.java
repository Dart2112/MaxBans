package net.lapismc.maxbans.banmanager;

public interface Temporary {
    long getExpires();

    boolean hasExpired();
}
