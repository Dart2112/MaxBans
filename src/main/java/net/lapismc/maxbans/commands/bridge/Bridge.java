package net.lapismc.maxbans.commands.bridge;

public interface Bridge {
    void export() throws Exception;

    void load() throws Exception;
}
