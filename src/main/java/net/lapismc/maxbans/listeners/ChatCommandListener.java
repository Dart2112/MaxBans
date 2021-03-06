package net.lapismc.maxbans.listeners;

import net.lapismc.maxbans.banmanager.Mute;
import net.lapismc.maxbans.banmanager.TempMute;
import net.lapismc.maxbans.util.Util;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public class ChatCommandListener extends ListenerSkeleton {
    @EventHandler(priority = EventPriority.NORMAL)
    public void onCommand(final PlayerCommandPreprocessEvent e) {
        if (e.isCancelled()) {
            return;
        }
        final String cmd = e.getMessage().split(" ")[0].replaceFirst("/", "");
        if (this.getPlugin().getBanManager().isChatCommand(cmd)) {
            final Player p = e.getPlayer();
            final Mute mute = this.getPlugin().getBanManager().getMute(p.getName());
            if (mute != null) {
                if (this.getPlugin().getBanManager().hasImmunity(p.getName())) {
                    return;
                }
                if (mute instanceof TempMute) {
                    final TempMute tMute = (TempMute) mute;
                    p.sendMessage(ChatColor.RED + "You're muted for another " + Util.getTimeUntil(tMute.getExpires()));
                } else {
                    p.sendMessage(ChatColor.RED + "You're muted!");
                }
                e.setCancelled(true);
            }
        }
    }
}
