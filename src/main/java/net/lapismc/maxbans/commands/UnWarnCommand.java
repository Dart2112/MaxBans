package net.lapismc.maxbans.commands;

import net.lapismc.maxbans.Msg;
import net.lapismc.maxbans.banmanager.Warn;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class UnWarnCommand extends CmdSkeleton {
    public UnWarnCommand() {
        super("unwarn", "maxbans.unwarn");
    }

    public boolean run(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        if (args.length <= 0) {
            sender.sendMessage(this.getUsage());
            return true;
        }
        String name = args[0];
        name = this.plugin.getBanManager().match(name);
        if (name == null) {
            name = args[0];
        }
        String banner;
        if (sender instanceof Player) {
            banner = sender.getName();
        } else {
            banner = "Console";
        }
        final List<Warn> warnings = this.plugin.getBanManager().getWarnings(name);
        if (warnings == null || warnings.size() == 0) {
            sender.sendMessage(Msg.get("error.no-warnings", new String[]{"name"}, new String[]{name}));
            return true;
        }
        this.plugin.getBanManager().deleteWarning(name, warnings.get(warnings.size() - 1));
        final String message = Msg.get("announcement.unwarn-success", new String[]{"banner", "name"}, new String[]{banner, name});
        this.plugin.getBanManager().announce(message);
        this.plugin.getBanManager().addHistory(name, banner, message);
        return true;
    }
}
