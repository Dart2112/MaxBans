package net.lapismc.maxbans.banmanager;

import net.lapismc.maxbans.MaxBans;
import net.lapismc.maxbans.database.Database;
import net.lapismc.maxbans.database.DatabaseHelper;
import net.lapismc.maxbans.util.DNSBL;
import net.lapismc.maxbans.util.Formatter;
import net.lapismc.maxbans.util.IPAddress;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Pattern;

public class BanManager {
    public String defaultReason;
    protected final MaxBans plugin;
    private final HashMap<String, Ban> bans;
    private final HashMap<String, TempBan> tempbans;
    private final HashMap<String, IPBan> ipbans;
    private final HashMap<String, TempIPBan> tempipbans;
    private final HashSet<String> whitelist;
    private final HashMap<String, Mute> mutes;
    private final HashMap<String, TempMute> tempmutes;
    private final HashMap<String, List<Warn>> warnings;
    private final ArrayList<HistoryRecord> history;
    private final HashMap<String, ArrayList<HistoryRecord>> personalHistory;
    private final HashMap<String, String> recentips;
    private final HashMap<String, HashSet<String>> iplookup;
    private final TrieSet players;
    private final HashMap<String, String> actualNames;
    private final HashSet<String> chatCommands;
    private boolean lockdown;
    private String lockdownReason;
    private String appealMessage;
    private Database db;
    private DNSBL dnsbl;
    private final HashSet<String> immunities;
    private final TreeSet<RangeBan> rangebans;

    public BanManager(final MaxBans plugin) {
        super();
        this.bans = new HashMap<>();
        this.tempbans = new HashMap<>();
        this.ipbans = new HashMap<>();
        this.tempipbans = new HashMap<>();
        this.whitelist = new HashSet<>();
        this.mutes = new HashMap<>();
        this.tempmutes = new HashMap<>();
        this.warnings = new HashMap<>();
        this.history = new ArrayList<>(50);
        this.personalHistory = new HashMap<>();
        this.recentips = new HashMap<>();
        this.iplookup = new HashMap<>();
        this.players = new TrieSet();
        this.actualNames = new HashMap<>();
        this.chatCommands = new HashSet<>();
        this.lockdown = false;
        this.lockdownReason = "Maintenance";
        this.defaultReason = "Misconduct";
        this.appealMessage = "";
        this.immunities = new HashSet<>();
        this.rangebans = new TreeSet<>();
        this.plugin = plugin;
        this.db = plugin.getDB();
        this.reload();
    }

    public HashSet<String> getWhitelist() {
        return this.whitelist;
    }

    String getAppealMessage() {
        return this.appealMessage;
    }

    private void setAppealMessage(final String msg) {
        this.appealMessage = ChatColor.translateAlternateColorCodes('&', msg);
    }

    public HashMap<String, Ban> getBans() {
        return this.bans;
    }

    public HashMap<String, IPBan> getIPBans() {
        return this.ipbans;
    }

    public HashMap<String, Mute> getMutes() {
        return this.mutes;
    }

    public HashMap<String, TempBan> getTempBans() {
        return this.tempbans;
    }

    public HashMap<String, TempIPBan> getTempIPBans() {
        return this.tempipbans;
    }

    public HashMap<String, TempMute> getTempMutes() {
        return this.tempmutes;
    }

    public HashMap<String, String> getPlayers() {
        return this.actualNames;
    }

    public HistoryRecord[] getHistory() {
        return this.history.toArray(new HistoryRecord[this.history.size()]);
    }

    public HistoryRecord[] getHistory(final String name) {
        final ArrayList<HistoryRecord> history = this.personalHistory.get(name);
        if (history != null) {
            return history.toArray(new HistoryRecord[history.size()]);
        }
        return new HistoryRecord[0];
    }

    public void addHistory(String name, String banner, final String message) {
        name = name.toLowerCase();
        banner = banner.toLowerCase();
        final HistoryRecord record = new HistoryRecord(name, banner, message);
        this.history.add(0, record);
        this.plugin.getDB().execute("INSERT INTO history (created, message, name, banner) VALUES (?, ?, ?, ?)", System.currentTimeMillis(), message, name, banner);
        ArrayList<HistoryRecord> personal = this.personalHistory.computeIfAbsent(name, k -> new ArrayList<>());
        personal.add(0, record);
        if (name.equals(banner)) {
            return;
        }
        personal = this.personalHistory.computeIfAbsent(banner, k -> new ArrayList<>());
        personal.add(0, record);
        this.personalHistory.put(banner, personal);
    }

    private void reload() {
        this.db = this.plugin.getDB();
        this.db.getCore().flush();
        this.bans.clear();
        this.tempbans.clear();
        this.ipbans.clear();
        this.tempipbans.clear();
        this.mutes.clear();
        this.tempmutes.clear();
        this.recentips.clear();
        this.players.clear();
        this.actualNames.clear();
        this.plugin.reloadConfig();
        try {
            DatabaseHelper.setup(this.db);
        } catch (SQLException e1) {
            e1.printStackTrace();
        }
        this.lockdown = this.plugin.getConfig().getBoolean("lockdown");
        this.lockdownReason = ChatColor.translateAlternateColorCodes('&', this.plugin.getConfig().getString("lockdown-reason", ""));
        this.setAppealMessage(this.plugin.getConfig().getString("appeal-message", ""));
        String query = "";
        this.plugin.getLogger().info("Loading from DB...");
        try {
            this.db.getConnection().close();
            final boolean readOnly = this.plugin.getConfig().getBoolean("read-only", false);
            PreparedStatement ps = null;
            ResultSet rs = null;
            try {
                if (!readOnly) {
                    ps = this.db.getConnection().prepareStatement("DELETE FROM bans WHERE expires <> 0 AND expires < ?");
                    ps.setLong(1, System.currentTimeMillis());
                    ps.execute();
                }
                this.plugin.getLogger().info("Loading bans");
                query = "SELECT * FROM bans";
                ps = this.db.getConnection().prepareStatement(query);
                rs = ps.executeQuery();
                while (rs.next()) {
                    final String name = rs.getString("name");
                    final String reason = rs.getString("reason");
                    final String banner = rs.getString("banner");
                    this.players.add(name);
                    final long expires = rs.getLong("expires");
                    final long time = rs.getLong("time");
                    if (expires != 0L) {
                        final TempBan tb = new TempBan(name, reason, banner, time, expires);
                        this.tempbans.put(name.toLowerCase(), tb);
                    } else {
                        final Ban ban = new Ban(name, reason, banner, time);
                        this.bans.put(name.toLowerCase(), ban);
                    }
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
            try {
                if (!readOnly) {
                    ps = this.db.getConnection().prepareStatement("DELETE FROM ipbans WHERE expires <> 0 AND expires < ?");
                    ps.setLong(1, System.currentTimeMillis());
                    ps.execute();
                }
                this.plugin.getLogger().info("Loading ipbans");
                query = "SELECT * FROM ipbans";
                ps = this.db.getConnection().prepareStatement(query);
                rs = ps.executeQuery();
                while (rs.next()) {
                    final String ip = rs.getString("ip");
                    final String reason = rs.getString("reason");
                    final String banner = rs.getString("banner");
                    final long expires = rs.getLong("expires");
                    final long time = rs.getLong("time");
                    if (expires != 0L) {
                        final TempIPBan tib = new TempIPBan(ip, reason, banner, time, expires);
                        this.tempipbans.put(ip, tib);
                    } else {
                        final IPBan ipban = new IPBan(ip, reason, banner, time);
                        this.ipbans.put(ip, ipban);
                    }
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
            try {
                if (!readOnly) {
                    ps = this.db.getConnection().prepareStatement("DELETE FROM mutes WHERE expires <> 0 AND expires < ?");
                    ps.setLong(1, System.currentTimeMillis());
                    ps.execute();
                }
                this.plugin.getLogger().info("Loading mutes");
                query = "SELECT * FROM mutes";
                ps = this.db.getConnection().prepareStatement(query);
                rs = ps.executeQuery();
                while (rs.next()) {
                    final String name = rs.getString("name");
                    final String banner2 = rs.getString("muter");
                    final String reason2 = rs.getString("reason");
                    this.players.add(name);
                    final long expires = rs.getLong("expires");
                    final long time = rs.getLong("time");
                    if (expires != 0L) {
                        final TempMute tmute = new TempMute(name, banner2, reason2, time, expires);
                        this.tempmutes.put(name.toLowerCase(), tmute);
                    } else {
                        final Mute mute = new Mute(name, banner2, reason2, time);
                        this.mutes.put(name.toLowerCase(), mute);
                    }
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
            try {
                this.plugin.getLogger().info("Loading player names...");
                query = "SELECT * FROM players";
                ps = this.db.getConnection().prepareStatement(query);
                rs = ps.executeQuery();
                while (rs.next()) {
                    final String actual = rs.getString("actual");
                    final String name2 = rs.getString("name");
                    this.actualNames.put(name2, actual);
                    this.players.add(name2);
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
            try {
                this.plugin.getLogger().info("Loading IP History");
                query = "SELECT * FROM iphistory";
                ps = this.db.getConnection().prepareStatement(query);
                rs = ps.executeQuery();
                while (rs.next()) {
                    final String name = rs.getString("name").toLowerCase();
                    final String ip2 = rs.getString("ip");
                    this.recentips.put(name, ip2);
                    HashSet<String> list = this.iplookup.computeIfAbsent(ip2, k -> new HashSet<>(2));
                    list.add(name);
                    this.iplookup.put(ip2, list);
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
            try {
                if (!readOnly) {
                    ps = this.db.getConnection().prepareStatement("DELETE FROM warnings WHERE expires < ?");
                    ps.setLong(1, System.currentTimeMillis());
                    ps.execute();
                }
                this.plugin.getLogger().info("Loading warn history...");
                query = "SELECT * FROM warnings";
                ps = this.db.getConnection().prepareStatement(query);
                rs = ps.executeQuery();
                while (rs.next()) {
                    final String name = rs.getString("name");
                    final String reason = rs.getString("reason");
                    final String banner = rs.getString("banner");
                    this.players.add(name);
                    final long expires = rs.getLong("expires");
                    final Warn warn = new Warn(reason, banner, expires);
                    List<Warn> warns = this.warnings.computeIfAbsent(name.toLowerCase(), k -> new ArrayList<>());
                    warns.add(warn);
                    this.warnings.put(name.toLowerCase(), warns);
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
            try {
                this.plugin.getLogger().info("Loading chat commands...");
                final List<String> cmds = this.plugin.getConfig().getStringList("chat-commands");
                for (final String s : cmds) {
                    this.addChatCommand(s);
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
            try {
                this.plugin.getLogger().info("Loading history...");
                if (!readOnly && this.plugin.getConfig().getInt("history-expirey-minutes", 10080) > 0) {
                    this.db.getConnection().prepareStatement("DELETE FROM history WHERE created < " + (System.currentTimeMillis() - this.plugin.getConfig().getInt("history-expirey-minutes", 10080) * 60000)).execute();
                }
                query = "SELECT * FROM history ORDER BY created DESC";
                rs = this.db.getConnection().prepareStatement(query).executeQuery();
                while (rs.next()) {
                    String name = rs.getString("name");
                    this.players.add(name);
                    String banner2 = rs.getString("banner");
                    final String message = rs.getString("message");
                    final long created = rs.getLong("created");
                    if (name == null) {
                        name = "unknown";
                    }
                    if (banner2 == null) {
                        banner2 = "unknown";
                    }
                    final HistoryRecord record = new HistoryRecord(name, banner2, message, created);
                    this.history.add(record);
                    ArrayList<HistoryRecord> personal = this.personalHistory.computeIfAbsent(name, k -> new ArrayList<>());
                    personal.add(record);
                    if (record.getName().equals(banner2)) {
                        continue;
                    }
                    personal = this.personalHistory.computeIfAbsent(banner2, k -> new ArrayList<>());
                    personal.add(record);
                    this.personalHistory.put(banner2, personal);
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
            try {
                query = "SELECT * FROM whitelist";
                rs = this.db.getConnection().prepareStatement(query).executeQuery();
                while (rs.next()) {
                    final String name = rs.getString("name");
                    this.whitelist.add(name);
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
            query = "SELECT * FROM rangebans";
            try {
                rs = this.plugin.getDB().getConnection().prepareStatement(query).executeQuery();
                while (rs.next()) {
                    final String banner3 = rs.getString("banner");
                    final String reason = rs.getString("reason");
                    final IPAddress start = new IPAddress(rs.getString("start"));
                    final IPAddress end = new IPAddress(rs.getString("end"));
                    final long created2 = rs.getLong("created");
                    final long expires2 = rs.getLong("created");
                    RangeBan rb;
                    if (expires2 == 0L) {
                        rb = new TempRangeBan(banner3, reason, created2, expires2, start, end);
                    } else {
                        rb = new RangeBan(banner3, reason, created2, start, end);
                    }
                    this.rangebans.add(rb);
                }
            } catch (SQLException e3) {
                e3.printStackTrace();
                this.plugin.getLogger().warning("Could not load rangebans!");
            }
            if (rs != null) {
                rs.close();
            }
            if (ps != null) {
                ps.close();
            }
        } catch (SQLException e4) {
            this.plugin.getLogger().severe(Formatter.secondary + "Could not load database history using: " + query);
            e4.printStackTrace();
        }
        if (this.plugin.getConfig().getBoolean("dnsbl.use", true)) {
            this.plugin.getLogger().info("Starting DNS blacklist");
            this.dnsbl = new DNSBL(this.plugin);
        }
        String defaultReason = this.plugin.getConfig().getString("default-reason");
        if (defaultReason == null || defaultReason.isEmpty()) {
            defaultReason = "Misconduct";
        }
        this.defaultReason = ChatColor.translateAlternateColorCodes('&', defaultReason);
        this.loadImmunities();
    }

    public DNSBL getDNSBL() {
        return this.dnsbl;
    }

    public boolean isWhitelisted(String name) {
        name = name.toLowerCase();
        return this.whitelist.contains(name);
    }

    public void setWhitelisted(String name, final boolean white) {
        name = name.toLowerCase();
        if (white) {
            this.whitelist.add(name);
            this.db.execute("INSERT INTO whitelist (name) VALUES (?)", name);
        } else {
            this.whitelist.remove(name);
            this.db.execute("DELETE FROM whitelist WHERE name = ?", name);
        }
    }

    public Mute getMute(String name) {
        name = name.toLowerCase();
        final Mute mute = this.mutes.get(name);
        if (mute != null) {
            return mute;
        }
        final TempMute tempm = this.tempmutes.get(name);
        if (tempm != null) {
            if (!tempm.hasExpired()) {
                return tempm;
            }
            this.tempmutes.remove(name);
            this.db.execute("DELETE FROM mutes WHERE name = ? AND expires <> 0", name);
        }
        return null;
    }

    public Ban getBan(String name) {
        name = name.toLowerCase();
        final Ban ban = this.bans.get(name);
        if (ban != null) {
            return ban;
        }
        final TempBan tempBan = this.tempbans.get(name);
        if (tempBan != null) {
            if (!tempBan.hasExpired()) {
                return tempBan;
            }
            this.tempbans.remove(name);
            this.db.execute("DELETE FROM bans WHERE name = ? AND expires <> 0", name);
        }
        return null;
    }

    public IPBan getIPBan(final String ip) {
        final IPBan ipBan = this.ipbans.get(ip);
        if (ipBan != null) {
            return ipBan;
        }
        final TempIPBan tempIPBan = this.tempipbans.get(ip);
        if (tempIPBan != null) {
            if (!tempIPBan.hasExpired()) {
                return tempIPBan;
            }
            this.tempipbans.remove(ip);
            this.db.execute("DELETE FROM ipbans WHERE ip = ? AND expires <> 0", ip);
        }
        return null;
    }

    public HashMap<String, String> getIPHistory() {
        return this.recentips;
    }

    public HashSet<String> getUsers(final String ip) {
        if (ip == null) {
            return null;
        }
        final HashSet<String> ips = this.iplookup.get(ip);
        if (ips == null) {
            return null;
        }
        return new HashSet<>(ips);
    }

    public List<Warn> getWarnings(String name) {
        name = name.toLowerCase();
        final List<Warn> warnings = this.warnings.get(name);
        if (warnings == null) {
            return null;
        }
        boolean q = false;
        final Iterator<Warn> it = warnings.iterator();
        while (it.hasNext()) {
            final Warn w = it.next();
            if (w.getExpires() < System.currentTimeMillis()) {
                it.remove();
                q = true;
            }
        }
        if (q) {
            this.db.execute("DELETE FROM warnings WHERE name = ? AND expires < ?", name, System.currentTimeMillis());
        }
        return warnings;
    }

    public boolean deleteWarning(final String name, final Warn warn) {
        final List<Warn> warnings = this.getWarnings(name);
        if (warnings != null && warnings.remove(warn)) {
            this.db.execute("DELETE FROM warnings WHERE name = ? AND expires = ? AND reason = ?", name.toLowerCase(), warn.getExpires(), warn.getReason());
            return true;
        }
        return false;
    }

    public void ban(String name, final String reason, String banner) {
        name = name.toLowerCase();
        banner = banner.toLowerCase();
        this.players.add(name);
        this.unban(name);
        final Ban ban = new Ban(name, reason, banner, System.currentTimeMillis());
        this.bans.put(name, ban);
        this.db.execute("INSERT INTO bans (name, reason, banner, time) VALUES (?, ?, ?, ?)", name, reason, banner, System.currentTimeMillis());
        this.kick(name, ban.getKickMessage());
    }

    public void kick(final String user, final String msg) {
        final Runnable r = () -> {
            final Player p = Bukkit.getPlayerExact(user);
            if (p != null && p.isOnline() && !BanManager.this.hasImmunity(user)) {
                p.kickPlayer(msg);
            }
        };
        if (Bukkit.isPrimaryThread()) {
            r.run();
        } else {
            Bukkit.getScheduler().runTask(MaxBans.instance, r);
        }
    }

    public void kickIP(final String ip, final String msg) {
        final Runnable r = () -> {
            for (final Player p : Bukkit.getOnlinePlayers()) {
                if (!BanManager.this.hasImmunity(p.getName())) {
                    final String pip = BanManager.this.getIP(p.getName());
                    if (!ip.equals(pip)) {
                        continue;
                    }
                    p.kickPlayer(msg);
                }
            }
        };
        if (Bukkit.isPrimaryThread()) {
            r.run();
        } else {
            Bukkit.getScheduler().runTask(MaxBans.instance, r);
        }
    }

    public void unban(String name) {
        name = name.toLowerCase();
        final Ban ban = this.bans.get(name);
        final TempBan tBan = this.tempbans.get(name);
        if (ban != null) {
            this.bans.remove(name);
            this.db.execute("DELETE FROM bans WHERE name = ?", name);
        }
        if (tBan != null) {
            this.tempbans.remove(name);
            if (ban == null) {
                this.db.execute("DELETE FROM bans WHERE name = ?", name);
            }
        }
    }

    public void unbanip(final String ip) {
        final IPBan ipBan = this.ipbans.get(ip);
        final TempIPBan tipBan = this.tempipbans.get(ip);
        if (ipBan != null) {
            this.ipbans.remove(ip);
            this.db.execute("DELETE FROM ipbans WHERE ip = ?", ip);
        }
        if (tipBan != null) {
            this.tempipbans.remove(ip);
            if (ipBan == null) {
                this.db.execute("DELETE FROM ipbans WHERE ip = ?", ip);
            }
        }
    }

    public void unmute(String name) {
        name = name.toLowerCase();
        final Mute mute = this.mutes.get(name);
        final TempMute tMute = this.tempmutes.get(name);
        if (mute != null) {
            this.mutes.remove(name);
            this.db.execute("DELETE FROM mutes WHERE name = ?", name);
        }
        if (tMute != null) {
            this.tempmutes.remove(name);
            if (mute == null) {
                this.db.execute("DELETE FROM mutes WHERE name = ?", name);
            }
        }
    }

    public void tempban(String name, final String reason, String banner, final long expires) {
        name = name.toLowerCase();
        banner = banner.toLowerCase();
        this.players.add(name);
        this.unban(name);
        final TempBan ban = new TempBan(name, reason, banner, System.currentTimeMillis(), expires);
        this.tempbans.put(name, ban);
        this.db.execute("INSERT INTO bans (name, reason, banner, time, expires) VALUES (?, ?, ?, ?, ?)", name, reason, banner, System.currentTimeMillis(), expires);
        this.kick(name, ban.getKickMessage());
    }

    public void ipban(final String ip, final String reason, String banner) {
        banner = banner.toLowerCase();
        this.unbanip(ip);
        final IPBan ipban = new IPBan(ip, reason, banner, System.currentTimeMillis());
        this.ipbans.put(ip, ipban);
        this.db.execute("INSERT INTO ipbans (ip, reason, banner, time) VALUES (?, ?, ?, ?)", ip, reason, banner, System.currentTimeMillis());
        this.kickIP(ip, ipban.getKickMessage());
    }

    public void tempipban(final String ip, final String reason, String banner, final long expires) {
        banner = banner.toLowerCase();
        this.unbanip(ip);
        final TempIPBan tib = new TempIPBan(ip, reason, banner, System.currentTimeMillis(), expires);
        this.tempipbans.put(ip, tib);
        this.db.execute("INSERT INTO ipbans (ip, reason, banner, time, expires) VALUES (?, ?, ?, ?, ?)", ip, reason, banner, System.currentTimeMillis(), expires);
        this.kickIP(ip, tib.getKickMessage());
    }

    public void mute(String name, final String banner, final String reason) {
        name = name.toLowerCase();
        this.players.add(name);
        this.unmute(name);
        final Mute mute = new Mute(name, banner, reason, System.currentTimeMillis());
        this.mutes.put(name, mute);
        this.db.execute("INSERT INTO mutes (name, muter, time) VALUES (?, ?, ?)", name, banner, System.currentTimeMillis());
    }

    public void tempmute(String name, String banner, final String reason, final long expires) {
        name = name.toLowerCase();
        banner = banner.toLowerCase();
        this.players.add(name);
        this.unmute(name);
        final TempMute tmute = new TempMute(name, banner, reason, System.currentTimeMillis(), expires);
        this.tempmutes.put(name, tmute);
        this.db.execute("INSERT INTO mutes (name, muter, time, expires) VALUES (?, ?, ?, ?)", name, banner, System.currentTimeMillis(), expires);
    }

    public void warn(String name, final String reason, String banner) {
        name = name.toLowerCase();
        banner = banner.toLowerCase();
        this.players.add(name);
        final ConfigurationSection cfg = this.plugin.getConfig().getConfigurationSection("warnings");
        long expires = 259200000L;
        int maxWarns = 3;
        if (cfg != null) {
            expires = cfg.getLong("expirey-in-minutes") * 60000L;
            if (expires <= 0L) {
                expires = Long.MAX_VALUE;
            } else {
                expires += System.currentTimeMillis();
            }
            maxWarns = cfg.getInt("max");
        }
        List<Warn> warns = this.getWarnings(name);
        if (warns == null) {
            warns = new ArrayList<>();
            this.warnings.put(name, warns);
        }
        warns.add(new Warn(reason, banner, expires));
        this.db.execute("INSERT INTO warnings (name, reason, banner, expires) VALUES (?, ?, ?, ?)", name, reason, banner, expires);
        if (maxWarns <= 0) {
            return;
        }
        final int warnsSize = warns.size();
        if (warnsSize != 0) {
            int pos = warnsSize % maxWarns;
            if (pos == 0) {
                pos = maxWarns;
            }
            final Ban ban = this.getBan(name);
            if (ban != null) {
                if (!(ban instanceof Temporary)) {
                    return;
                }
                if (((Temporary) ban).getExpires() > System.currentTimeMillis() + 3600000L) {
                    return;
                }
            }
            final ConfigurationSection actions = cfg.getConfigurationSection("actions");
            if (actions == null) {
                return;
            }
            for (final String key : actions.getKeys(false)) {
                try {
                    if (pos != Integer.parseInt(key)) {
                        continue;
                    }
                    final String action = actions.getString(key);
                    final String[] cmds = action.split("[^\\\\];");
                    String[] array;
                    for (int length = (array = cmds).length, j = 0; j < length; ++j) {
                        String cmd = array[j];
                        cmd = cmd.trim();
                        CommandSender sender = Bukkit.getConsoleSender();
                        if (cmd.startsWith("/")) {
                            cmd = cmd.replaceFirst("/", "");
                            final Player pBanner = Bukkit.getPlayerExact(banner);
                            if (pBanner != null) {
                                sender = pBanner;
                            }
                        }
                        final String lowercaseCmd = cmd.toLowerCase();
                        int index = lowercaseCmd.indexOf("{name}");
                        if (index >= 0) {
                            final Pattern p = Pattern.compile("\\{name}", Pattern.CASE_INSENSITIVE);
                            cmd = p.matcher(cmd).replaceAll(name);
                        }
                        final String ip = this.getIP(name);
                        index = lowercaseCmd.indexOf("{ip}");
                        if (index >= 0 && ip != null) {
                            final Pattern p2 = Pattern.compile("\\{ip}", Pattern.CASE_INSENSITIVE);
                            cmd = p2.matcher(cmd).replaceAll(name);
                        }
                        index = lowercaseCmd.indexOf("{reason}");
                        if (index >= 0) {
                            final Pattern p2 = Pattern.compile("\\{reason}", Pattern.CASE_INSENSITIVE);
                            cmd = p2.matcher(cmd).replaceAll(reason);
                        }
                        index = lowercaseCmd.indexOf("{banner}");
                        if (index >= 0) {
                            final Pattern p2 = Pattern.compile("\\{banner}", Pattern.CASE_INSENSITIVE);
                            cmd = p2.matcher(cmd).replaceAll(banner);
                        }
                        index = lowercaseCmd.indexOf("{reasons}");
                        if (index >= 0) {
                            final Pattern p2 = Pattern.compile("\\{reasons}", Pattern.CASE_INSENSITIVE);
                            StringBuilder msg = new StringBuilder();
                            for (int i = warnsSize - 1; i >= warnsSize - pos; --i) {
                                final Warn warn = warns.get(i);
                                String rsn = warn.getReason();
                                if (msg.length() > 0) {
                                    rsn = String.valueOf(rsn) + "\\\\n";
                                }
                                msg.insert(0, String.valueOf(rsn));
                            }
                            cmd = p2.matcher(cmd).replaceAll(msg.toString());
                        }
                        Bukkit.dispatchCommand(sender, cmd);
                    }
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                    System.out.println("Warning: " + key + " is not a valid number in plugins\\MaxBans\\config.yml! Please check your warnings configuration!");
                }
            }
        }
    }

    public void clearWarnings(String name) {
        name = name.toLowerCase();
        this.warnings.remove(name);
        this.db.execute("DELETE FROM warnings WHERE name = ?", name);
    }

    public String getIP(final String user) {
        if (user == null) {
            return null;
        }
        return this.recentips.get(user.toLowerCase());
    }

    public boolean logActual(String name, final String actual) {
        name = name.toLowerCase();
        final String oldActual = this.actualNames.put(name, actual);
        if (oldActual == null) {
            this.plugin.getDB().execute("INSERT INTO players (name, actual) VALUES (?, ?)", name, actual);
            return true;
        }
        if (!oldActual.equals(actual)) {
            this.plugin.getDB().execute("UPDATE players SET actual = ? WHERE name = ?", actual, name);
            return true;
        }
        return false;
    }

    public boolean logIP(String name, final String ip) {
        name = name.toLowerCase();
        final String oldIP = this.recentips.get(name);
        if (oldIP != null && ip.equals(oldIP)) {
            return false;
        }
        final boolean isNew = this.recentips.put(name, ip) == null;
        if (!isNew) {
            final HashSet<String> usersFromOldIP = this.iplookup.get(oldIP);
            usersFromOldIP.remove(name);
        } else {
            this.players.add(name);
        }
        HashSet<String> usersFromNewIP = this.iplookup.computeIfAbsent(ip, k -> new HashSet<>());
        usersFromNewIP.add(name);
        if (!isNew) {
            this.db.execute("UPDATE iphistory SET ip = ? WHERE name = ?", ip, name);
        } else {
            this.db.execute("INSERT INTO iphistory (name, ip) VALUES (?, ?)", name, ip);
        }
        this.iplookup.put(ip, usersFromNewIP);
        return true;
    }

    public void announce(final String s) {
        this.announce(s, false, null);
    }

    public void announce(String s, final boolean silent, final CommandSender sender) {
        if (silent) {
            s = Formatter.primary + "[Silent] " + s;
            for (final Player p : Bukkit.getOnlinePlayers()) {
                if (p.hasPermission("maxbans.seesilent")) {
                    p.sendMessage(s);
                }
            }
            if (sender != null && !sender.hasPermission("maxbans.seesilent")) {
                sender.sendMessage(s);
            }
        } else {
            for (final Player p : Bukkit.getOnlinePlayers()) {
                if (p.hasPermission("maxbans.seebroadcast")) {
                    p.sendMessage(s);
                }
            }
        }
        Bukkit.getConsoleSender().sendMessage(s);
    }

    public String match(final String partial) {
        return this.match(partial, false);
    }

    public String match(String partial, final boolean excludeOnline) {
        partial = partial.toLowerCase();
        final String ip = this.recentips.get(partial);
        if (ip != null) {
            return partial;
        }
        if (!excludeOnline) {
            final Player p = Bukkit.getPlayer(partial);
            if (p != null) {
                return p.getName();
            }
        }
        final String nearestMap = this.players.nearestKey(partial);
        if (nearestMap != null) {
            return nearestMap;
        }
        return partial;
    }

    public boolean isLockdown() {
        return this.lockdown;
    }

    public String getLockdownReason() {
        return this.lockdownReason;
    }

    public void setLockdown(final boolean lockdown, String reason) {
        this.lockdown = lockdown;
        reason = ChatColor.translateAlternateColorCodes('&', reason);
        if (lockdown) {
            this.plugin.getConfig().set("lockdown", true);
            this.plugin.getConfig().set("lockdown-reason", reason);
            this.lockdownReason = reason;
        } else {
            this.plugin.getConfig().set("lockdown", false);
            this.plugin.getConfig().set("lockdown-reason", "");
            this.lockdownReason = "";
        }
        this.plugin.saveConfig();
    }

    private void addChatCommand(String s) {
        s = s.toLowerCase();
        this.chatCommands.add(s);
    }

    public boolean isChatCommand(String s) {
        s = s.toLowerCase();
        return this.chatCommands.contains(s);
    }

    private void loadImmunities() {
        final File f = new File(MaxBans.instance.getDataFolder(), "immunities.txt");
        if (f.exists()) {
            try {
                final Scanner sc = new Scanner(f);
                while (sc.hasNext()) {
                    String name = sc.nextLine();
                    name = name.toLowerCase();
                    this.immunities.add(name);
                }
                sc.close();
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("Failed to load immunities.txt file!");
            }
        }
    }

    private void saveImmunities() {
        final File f = new File(MaxBans.instance.getDataFolder(), "immunities.txt");
        try {
            f.createNewFile();
            final PrintStream ps = new PrintStream(f);
            for (final String s : this.immunities) {
                ps.println(s);
            }
            ps.close();
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Failed to save immunities.txt file!");
        }
    }

    public boolean hasImmunity(final String user) {
        return user != null && this.immunities.contains(user.toLowerCase());
    }

    public boolean setImmunity(String user, final boolean immune) {
        user = user.toLowerCase();
        boolean success;
        if (immune) {
            success = this.immunities.add(user);
        } else {
            success = this.immunities.remove(user);
        }
        if (success) {
            this.saveImmunities();
        }
        return success;
    }

    public HashSet<String> getImmunities() {
        return new HashSet<>(this.immunities);
    }

    public RangeBan getBan(final IPAddress ip) {
        final RangeBan dummy = new RangeBan("dummy", "n/a", System.currentTimeMillis(), ip, ip);
        final RangeBan rb = this.rangebans.floor(dummy);
        if (rb == null) {
            return null;
        }
        if (!rb.contains(ip)) {
            return null;
        }
        if (rb instanceof Temporary && ((Temporary) rb).hasExpired()) {
            this.unban(rb);
            return null;
        }
        return rb;
    }

    public RangeBan ban(final RangeBan rb) {
        RangeBan previous = this.rangebans.floor(rb);
        if (previous != null && previous.overlaps(rb)) {
            return previous;
        }
        previous = this.rangebans.ceiling(rb);
        if (previous != null && previous.overlaps(rb)) {
            return previous;
        }
        this.rangebans.add(rb);
        long expires = 0L;
        if (rb instanceof Temporary) {
            expires = ((Temporary) rb).getExpires();
        }
        this.plugin.getDB().execute("INSERT INTO rangebans (banner, reason, start, end, created, expires) VALUES (?, ?, ?, ?, ?, ?)", rb.getBanner(), rb.getReason(), rb.getStart().toString(), rb.getEnd().toString(), rb.getCreated(), expires);
        return null;
    }

    public void unban(final RangeBan rb) {
        if (this.rangebans.contains(rb)) {
            this.rangebans.remove(rb);
            this.plugin.getDB().execute("DELETE FROM rangebans WHERE start = ? AND end = ?", rb.getStart().toString(), rb.getEnd().toString());
        }
    }

    public TreeSet<RangeBan> getRangeBans() {
        return this.rangebans;
    }
}
