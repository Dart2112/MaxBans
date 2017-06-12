package net.lapismc.maxbans.util;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

public class Formatter {
    public static ChatColor primary;
    public static ChatColor secondary;
    public static ChatColor banner;
    public static ChatColor reason;
    public static ChatColor time;

    public static void load(final Plugin plugin) {
        Formatter.primary = getColor(plugin.getConfig().getString("color.primary"));
        Formatter.secondary = getColor(plugin.getConfig().getString("color.secondary"));
        final ConfigurationSection cfg = plugin.getConfig().getConfigurationSection("kick-colors");
        getColor(cfg.getString("regular"));
        Formatter.banner = getColor(cfg.getString("banner"));
        Formatter.reason = getColor(cfg.getString("reason"));
        Formatter.time = getColor(cfg.getString("time"));
    }

    private static ChatColor getColor(final String s) {
        ChatColor col = ChatColor.getByChar(s);
        if (col != null) {
            return col;
        }
        col = ChatColor.valueOf(s.toUpperCase());
        return col;
    }
}
