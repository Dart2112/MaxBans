package net.lapismc.maxbans.commands;

import net.lapismc.maxbans.MaxBans;
import net.lapismc.maxbans.Msg;
import net.lapismc.maxbans.banmanager.Ban;
import net.lapismc.maxbans.banmanager.IPBan;
import net.lapismc.maxbans.banmanager.TempBan;
import net.lapismc.maxbans.banmanager.TempIPBan;
import net.lapismc.maxbans.util.Util;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

public class TempBanCommand extends CmdSkeleton {
    public TempBanCommand() {
        super("tempban", "maxbans.tempban");
    }

    public boolean run(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        if (args.length < 3) {
            sender.sendMessage(this.getUsage());
            return true;
        }
        final boolean silent = Util.isSilent(args);
        String name = args[0];
        if (name.isEmpty()) {
            sender.sendMessage(Msg.get("error.no-player-given"));
            return true;
        }
        long expires = Util.getTime(args);
        if (expires <= 0L) {
            sender.sendMessage(this.getUsage());
            return true;
        }
        expires += System.currentTimeMillis();
        final FileConfiguration conf = MaxBans.instance.getConfig();
        long tempbanTime;
        try {
            tempbanTime = conf.getLong("MaxTempbanTime");
        } catch (Exception e) {
            tempbanTime = 604800L;
        }
        long compare = tempbanTime;
        tempbanTime = tempbanTime * 1000;

        tempbanTime += System.currentTimeMillis();
        if (compare != 0 && expires > tempbanTime) {
            sender.sendMessage("Ban time is too long! Reducing to ban limit! (" + Util.getTimeUntil(tempbanTime) + ")");
            expires = tempbanTime;
        }
        final String reason = Util.buildReason(args);
        final String banner = Util.getName(sender);
        if (!Util.isIP(name)) {
            name = this.plugin.getBanManager().match(name);
            if (name == null) {
                name = args[0];
            }
            final Ban ban = this.plugin.getBanManager().getBan(name);
            if (ban != null) {
                if (!(ban instanceof TempBan)) {
                    final String msg = Msg.get("error.tempban-shorter-than-last");
                    sender.sendMessage(msg);
                    return true;
                }
                final TempBan tBan = (TempBan) ban;
                if (tBan.getExpires() > expires) {
                    final String msg2 = Msg.get("error.tempban-shorter-than-last");
                    sender.sendMessage(msg2);
                    return true;
                }
                this.plugin.getBanManager().unban(name);
            }
            this.plugin.getBanManager().tempban(name, reason, banner, expires);
        } else {
            final IPBan ipban = this.plugin.getBanManager().getIPBan(name);
            if (ipban != null && ipban instanceof TempIPBan) {
                final TempIPBan tiBan = (TempIPBan) ipban;
                if (tiBan.getExpires() > expires) {
                    final String msg3 = Msg.get("error.tempipban-shorter-than-last");
                    sender.sendMessage(msg3);
                    return true;
                }
                this.plugin.getBanManager().unbanip(name);
            }
            this.plugin.getBanManager().tempipban(name, reason, banner, expires);
        }
        final String message = Msg.get("announcement.player-was-tempbanned", new String[]{"banner", "name", "reason", "time"}, new String[]{banner, name, reason, Util.getTimeUntil(expires)});
        this.plugin.getBanManager().announce(message, silent, sender);
        this.plugin.getBanManager().addHistory(name, banner, message);
        return true;
    }
}
