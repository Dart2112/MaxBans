package net.lapismc.maxbans.bungee;

import net.lapismc.maxbans.MaxBans;
import net.lapismc.maxbans.banmanager.IPBan;
import net.lapismc.maxbans.banmanager.RangeBan;
import net.lapismc.maxbans.banmanager.Temporary;
import net.lapismc.maxbans.util.Formatter;
import net.lapismc.maxbans.util.IPAddress;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;

public class BungeeListener implements PluginMessageListener {
    private MaxBans plugin;

    public BungeeListener() {
        super();
        this.plugin = MaxBans.instance;
    }

    public void onPluginMessageReceived(final String channel, final Player player, final byte[] message) {
        try {
            final DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));
            if (in.readUTF().equals("IP")) {
                final String ip = in.readUTF();
                MaxBans.instance.getBanManager().logIP(player.getName(), ip);
                Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
                    final boolean whitelisted = MaxBans.instance.getBanManager().isWhitelisted(player.getName());
                    if (!whitelisted) {
                        final IPBan ipban = BungeeListener.this.plugin.getBanManager().getIPBan(ip);
                        if (ipban != null) {
                            player.kickPlayer(ipban.getKickMessage());
                            return;
                        }
                        final IPAddress address = new IPAddress(ip);
                        final RangeBan rb = BungeeListener.this.plugin.getBanManager().getBan(address);
                        if (rb != null) {
                            player.kickPlayer(rb.getKickMessage());
                            if (BungeeListener.this.plugin.getConfig().getBoolean("notify", true)) {
                                final String msg = Formatter.secondary + player.getName() + Formatter.primary + " (" + ChatColor.RED + address + Formatter.primary + ")" + " tried to join, but is " + ((rb instanceof Temporary) ? "temp " : "") + "RangeBanned.";
                                for (final Player p : Bukkit.getOnlinePlayers()) {
                                    if (p.hasPermission("maxbans.notify")) {
                                        p.sendMessage(msg);
                                    }
                                }
                            }
                            return;
                        }
                        if (BungeeListener.this.plugin.getBanManager().getDNSBL() != null) {
                            BungeeListener.this.plugin.getBanManager().getDNSBL().handle(player, ip);
                        }
                    }
                }, 1L);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
