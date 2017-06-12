package net.lapismc.maxbans.commands.bridge;

import net.lapismc.maxbans.MaxBans;
import net.lapismc.maxbans.banmanager.IPBan;
import net.lapismc.maxbans.banmanager.TempIPBan;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.Map;

public class VanillaBridge implements Bridge {
    @SuppressWarnings("deprecation")
    public void export() {
        System.out.println("Exporting to Vanilla bans...");
        final MaxBans plugin = MaxBans.instance;
        for (final Map.Entry<String, IPBan> entry2 : plugin.getBanManager().getIPBans().entrySet()) {
            if (entry2.getValue() instanceof TempIPBan) {
                continue;
            }
            Bukkit.banIP(entry2.getKey());
        }
    }

    public void load() {
        System.out.println("Importing from Vanilla bans...");
        final MaxBans plugin = MaxBans.instance;
        for (final OfflinePlayer p : Bukkit.getBannedPlayers()) {
            plugin.getBanManager().ban(p.getName(), "Vanilla Ban", "Console");
        }
        for (final String ip : Bukkit.getIPBans()) {
            plugin.getBanManager().ipban(ip, "Vanilla IP Ban", "Console");
        }
    }
}
