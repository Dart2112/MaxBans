package net.lapismc.maxbans;

import net.lapismc.maxbans.banmanager.BanManager;
import net.lapismc.maxbans.banmanager.SyncBanManager;
import net.lapismc.maxbans.bungee.BungeeListener;
import net.lapismc.maxbans.commands.*;
import net.lapismc.maxbans.database.Database;
import net.lapismc.maxbans.database.DatabaseCore;
import net.lapismc.maxbans.database.MySQLCore;
import net.lapismc.maxbans.database.SQLiteCore;
import net.lapismc.maxbans.geoip.GeoIPDatabase;
import net.lapismc.maxbans.listeners.ChatCommandListener;
import net.lapismc.maxbans.listeners.ChatListener;
import net.lapismc.maxbans.listeners.HeroChatListener;
import net.lapismc.maxbans.listeners.JoinListener;
import net.lapismc.maxbans.sync.SyncServer;
import net.lapismc.maxbans.sync.Syncer;
import net.lapismc.maxbans.util.Formatter;
import net.lapismc.maxbans.util.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.logging.Logger;

public class MaxBans extends JavaPlugin {
    public static MaxBans instance;
    public boolean filter_names;
    private Logger logger = getLogger();
    private BanManager banManager;
    private Syncer syncer;
    private SyncServer syncServer;
    private GeoIPDatabase geoIPDB;
    private Database db;
    private Metrics metrics;

    private static void access$0(final MaxBans maxBans, final GeoIPDatabase geoIPDB) {
        maxBans.geoIPDB = geoIPDB;
    }

    public GeoIPDatabase getGeoDB() {
        return this.geoIPDB;
    }

    public void onEnable() {
        MaxBans.instance = this;
        if (!this.getDataFolder().exists()) {
            if (!this.getDataFolder().mkdir()) {
                logger.warning("Failed to generate data folder!");
            }
        }
        final File configFile = new File(this.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            this.saveResource("config.yml", false);
        }
        this.reloadConfig();
        Msg.reload();
        this.getConfig().options().copyDefaults();
        final File geoCSV = new File(this.getDataFolder(), "geoip.csv");
        if (!geoCSV.exists()) {
            final Runnable download = () -> {
                final String url = "http://maxgamer.org/plugins/maxbans/geoip.csv";
                MaxBans.this.getLogger().info("Downloading geoIPDatabase...");
                try {
                    final FileOutputStream out = new FileOutputStream(geoCSV);
                    final BufferedInputStream in = new BufferedInputStream(new URL(url).openStream());
                    final byte[] data = new byte[1024];
                    int count;
                    while ((count = in.read(data, 0, 1024)) != -1) {
                        out.write(data, 0, count);
                    }
                    MaxBans.this.getLogger().info("Download complete.");
                    out.close();
                    in.close();
                    MaxBans.access$0(MaxBans.this, new GeoIPDatabase(geoCSV));
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("Failed to download MaxBans GeoIPDatabase");
                }
            };
            Bukkit.getScheduler().runTaskAsynchronously(this, download);
        } else {
            this.geoIPDB = new GeoIPDatabase(geoCSV);
        }
        this.filter_names = this.getConfig().getBoolean("filter-names");
        Formatter.load(this);
        final ConfigurationSection dbConfig = this.getConfig().getConfigurationSection("database");
        DatabaseCore dbCore;
        if (this.getConfig().getBoolean("database.mysql", false)) {
            this.getLogger().info("Using MySQL");
            final String user = dbConfig.getString("user");
            final String pass = dbConfig.getString("pass");
            final String host = dbConfig.getString("host");
            final String name = dbConfig.getString("name");
            final String port = dbConfig.getString("port");
            dbCore = new MySQLCore(host, user, pass, name, port);
        } else {
            this.getLogger().info("Using SQLite");
            dbCore = new SQLiteCore(new File(this.getDataFolder(), "bans.db"));
        }
        final boolean readOnly = dbConfig.getBoolean("read-only", false);
        try {
            this.db = new Database(dbCore) {
                public void execute(final String query, final Object... objs) {
                    if (readOnly) {
                        return;
                    }
                    super.execute(query, objs);
                }
            };
        } catch (Database.ConnectionException e1) {
            e1.printStackTrace();
            System.out.println("Failed to create connection to database. Disabling MaxBans :(");
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        }
        final ConfigurationSection syncConfig = this.getConfig().getConfigurationSection("sync");
        if (syncConfig.getBoolean("use", false)) {
            this.getLogger().info("Using Sync.");
            final String host = syncConfig.getString("host");
            final int port2 = syncConfig.getInt("port");
            final String pass2 = syncConfig.getString("pass");
            if (syncConfig.getBoolean("server", false)) {
                try {
                    (this.syncServer = new SyncServer(port2, pass2)).start();
                } catch (IOException e2) {
                    e2.printStackTrace();
                    this.getLogger().info("Could not start sync server!");
                }
            }
            (this.syncer = new Syncer(host, port2, pass2)).start();
            this.banManager = new SyncBanManager(this);
        } else {
            this.banManager = new BanManager(this);
        }
        this.registerCommands();
        Bukkit.getServer().getPluginManager().registerEvents(new ToggleChat(), this);
        if (Bukkit.getPluginManager().getPlugin("Herochat") != null) {
            this.getLogger().info("Found Herochat... Hooking!");
            HeroChatListener herochatListener = new HeroChatListener(this);
            Bukkit.getServer().getPluginManager().registerEvents(herochatListener, this);
        } else {
            ChatListener chatListener = new ChatListener(this);
            Bukkit.getServer().getPluginManager().registerEvents(chatListener, this);
        }
        JoinListener joinListener = new JoinListener();
        ChatCommandListener chatCommandListener = new ChatCommandListener();
        Bukkit.getServer().getPluginManager().registerEvents(joinListener, this);
        Bukkit.getServer().getPluginManager().registerEvents(chatCommandListener, this);
        this.startMetrics();
        if (this.isBungee()) {
            Bukkit.getMessenger().registerIncomingPluginChannel(this, "BungeeCord", new BungeeListener());
            Bukkit.getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        }
    }

    public boolean isBungee() {
        return MaxBans.instance.getConfig().getBoolean("bungee");
    }

    public void onDisable() {
        this.getLogger().info("Disabling Maxbans...");
        if (this.syncer != null) {
            this.syncer.stop();
            this.syncer = null;
        }
        if (this.syncServer != null) {
            this.syncServer.stop();
            this.syncServer = null;
        }
        this.getLogger().info("Clearing buffer...");
        this.db.close();
        this.getLogger().info("Cleared buffer...");
        MaxBans.instance = null;
    }

    public BanManager getBanManager() {
        return this.banManager;
    }

    public Database getDB() {
        return this.db;
    }

    private void registerCommands() {
        new BanCommand();
        new IPBanCommand();
        new MuteCommand();
        new TempBanCommand();
        new TempIPBanCommand();
        new TempMuteCommand();
        new UnbanCommand();
        new UnMuteCommand();
        new UUID();
        new CheckIPCommand();
        new CheckBanCommand();
        new DupeIPCommand();
        new WarnCommand();
        new UnWarnCommand();
        new ClearWarningsCommand();
        new LockdownCommand();
        new KickCommand();
        new ForceSpawnCommand();
        new MBCommand();
        new HistoryCommand();
        new MBImportCommand();
        new MBExportCommand();
        new MBDebugCommand();
        new ReloadCommand();
        new WhitelistCommand();
        new ImmuneCommand();
        new RangeBanCommand();
        new TempRangeBanCommand();
        new UnbanRangeCommand();
    }

    private void startMetrics() {
        try {
            if (this.metrics != null) {
                return;
            }
            this.metrics = new Metrics(this);
            if (!this.metrics.start()) {
                return;
            }
            final Metrics.Graph bans = this.metrics.createGraph("Bans");
            final Metrics.Graph ipbans = this.metrics.createGraph("IP Bans");
            final Metrics.Graph mutes = this.metrics.createGraph("Mutes");
            bans.addPlotter(new Metrics.Plotter() {
                public int getValue() {
                    return MaxBans.this.getBanManager().getBans().size();
                }
            });
            ipbans.addPlotter(new Metrics.Plotter() {
                public int getValue() {
                    return MaxBans.this.getBanManager().getIPBans().size();
                }
            });
            mutes.addPlotter(new Metrics.Plotter() {
                public int getValue() {
                    return MaxBans.this.getBanManager().getMutes().size();
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Metrics start failed");
        }
    }

    public Syncer getSyncer() {
        return this.syncer;
    }
}
