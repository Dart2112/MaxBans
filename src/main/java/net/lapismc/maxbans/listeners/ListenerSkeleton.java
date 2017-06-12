package net.lapismc.maxbans.listeners;

import net.lapismc.maxbans.MaxBans;
import org.bukkit.event.Listener;

public class ListenerSkeleton implements Listener {
    protected MaxBans getPlugin() {
        return MaxBans.instance;
    }
}
