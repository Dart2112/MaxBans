package net.lapismc.maxbans.util;

import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.scheduler.BukkitTask;

import java.io.*;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.*;
import java.util.logging.Level;

public class Metrics {
    /*private static final int REVISION = 6;
    private static final String BASE_URL = "http://mcstats.org";
    private static final String REPORT_URL = "/report/%s";
    private static final String CUSTOM_DATA_SEPARATOR = "~~";
    private static final int PING_INTERVAL = 10;*/
    private final Plugin plugin;
    private final Set<Graph> graphs;
    private final YamlConfiguration configuration;
    private final String guid;
    private final boolean debug;
    private final Object optOutLock;
    private volatile BukkitTask task;

    public Metrics(final Plugin plugin) throws IOException {
        super();
        this.graphs = Collections.synchronizedSet(new HashSet<Graph>());
        this.optOutLock = new Object();
        this.task = null;
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        this.plugin = plugin;
        File configurationFile = this.getConfigFile();
        (this.configuration = YamlConfiguration.loadConfiguration(configurationFile)).addDefault("opt-out", false);
        this.configuration.addDefault("guid", UUID.randomUUID().toString());
        this.configuration.addDefault("debug", false);
        if (this.configuration.get("guid", null) == null) {
            this.configuration.options().header("http://mcstats.org").copyDefaults(true);
            this.configuration.save(configurationFile);
        }
        this.guid = this.configuration.getString("guid");
        this.debug = this.configuration.getBoolean("debug", false);
    }

    private static void encodeDataPair(final StringBuilder buffer, final String key, final String value) throws UnsupportedEncodingException {
        buffer.append('&').append(encode(key)).append('=').append(encode(value));
    }

    private static String encode(final String text) throws UnsupportedEncodingException {
        return URLEncoder.encode(text, "UTF-8");
    }

    private static /* synthetic */ void access$2(final Metrics metrics) {
        metrics.task = null;
    }

    public Graph createGraph(final String name) {
        if (name == null) {
            throw new IllegalArgumentException("Graph name cannot be null");
        }
        final Graph graph = new Graph(name);
        this.graphs.add(graph);
        return graph;
    }

    public boolean start() {
        synchronized (this.optOutLock) {
            if (this.isOptOut()) {
                // monitorexit(this.optOutLock)
                return false;
            }
            if (this.task != null) {
                // monitorexit(this.optOutLock)
                return true;
            }
            this.task = this.plugin.getServer().getScheduler().runTaskTimerAsynchronously(this.plugin, new Runnable() {
                private boolean firstPost = true;

                public void run() {
                    try {
                        synchronized (Metrics.this.optOutLock) {
                            if (Metrics.this.isOptOut() && Metrics.this.task != null) {
                                Metrics.this.task.cancel();
                                Metrics.access$2(Metrics.this);
                                for (final Graph graph : Metrics.this.graphs) {
                                    graph.onOptOut();
                                }
                            }
                        }
                        // monitorexit(Metrics.access$0(this.this$0))
                        Metrics.this.postPlugin(!this.firstPost);
                        this.firstPost = false;
                    } catch (IOException e) {
                        if (Metrics.this.debug) {
                            Bukkit.getLogger().log(Level.INFO, "[Metrics] " + e.getMessage());
                        }
                    }
                }
            }, 0L, 12000L);
            // monitorexit(this.optOutLock)
            return true;
        }
    }

    private boolean isOptOut() {
        synchronized (this.optOutLock) {
            try {
                this.configuration.load(this.getConfigFile());
            } catch (IOException | InvalidConfigurationException ex) {
                if (this.debug) {
                    Bukkit.getLogger().log(Level.INFO, "[Metrics] " + ex.getMessage());
                }
                // monitorexit(this.optOutLock)
                return true;
            }
            // monitorexit(this.optOutLock)
            return this.configuration.getBoolean("opt-out", false);
        }
    }

    private File getConfigFile() {
        final File pluginsFolder = this.plugin.getDataFolder().getParentFile();
        return new File(new File(pluginsFolder, "PluginMetrics"), "config.yml");
    }

    private void postPlugin(final boolean isPing) throws IOException {
        final PluginDescriptionFile description = this.plugin.getDescription();
        final String pluginName = description.getName();
        final boolean onlineMode = Bukkit.getServer().getOnlineMode();
        final String pluginVersion = description.getVersion();
        final String serverVersion = Bukkit.getVersion();
        final int playersOnline = Bukkit.getServer().getOnlinePlayers().size();
        final StringBuilder data = new StringBuilder();
        data.append(encode("guid")).append('=').append(encode(this.guid));
        encodeDataPair(data, "version", pluginVersion);
        encodeDataPair(data, "server", serverVersion);
        encodeDataPair(data, "players", Integer.toString(playersOnline));
        encodeDataPair(data, "revision", String.valueOf(6));
        final String osname = System.getProperty("os.name");
        String osarch = System.getProperty("os.arch");
        final String osversion = System.getProperty("os.version");
        final String java_version = System.getProperty("java.version");
        final int coreCount = Runtime.getRuntime().availableProcessors();
        if (osarch.equals("amd64")) {
            osarch = "x86_64";
        }
        encodeDataPair(data, "osname", osname);
        encodeDataPair(data, "osarch", osarch);
        encodeDataPair(data, "osversion", osversion);
        encodeDataPair(data, "cores", Integer.toString(coreCount));
        encodeDataPair(data, "online-mode", Boolean.toString(onlineMode));
        encodeDataPair(data, "java_version", java_version);
        if (isPing) {
            encodeDataPair(data, "ping", "true");
        }
        synchronized (this.graphs) {
            for (final Graph graph : this.graphs) {
                for (final Plotter plotter : graph.getPlotters()) {
                    final String key = String.format("C%s%s%s%s", "~~", graph.getName(), "~~", plotter.getColumnName());
                    final String value = Integer.toString(plotter.getValue());
                    encodeDataPair(data, key, value);
                }
            }
        }
        // monitorexit(this.graphs)
        final URL url = new URL("http://mcstats.org" + String.format("/report/%s", encode(pluginName)));
        URLConnection connection;
        if (this.isMineshafterPresent()) {
            connection = url.openConnection(Proxy.NO_PROXY);
        } else {
            connection = url.openConnection();
        }
        connection.setDoOutput(true);
        final OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream());
        writer.write(data.toString());
        writer.flush();
        final BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        final String response = reader.readLine();
        writer.close();
        reader.close();
        if (response == null || response.startsWith("ERR")) {
            throw new IOException(response);
        }
        if (response.contains("OK This is your first update this hour")) {
            synchronized (this.graphs) {
                for (final Graph graph2 : this.graphs) {
                    for (final Plotter plotter2 : graph2.getPlotters()) {
                        plotter2.reset();
                    }
                }
            }
            // monitorexit(this.graphs)
        }
    }

    private boolean isMineshafterPresent() {
        try {
            Class.forName("mineshafter.MineServer");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static class Graph {
        private final String name;
        private final Set<Plotter> plotters;

        private Graph(final String name) {
            super();
            this.plotters = new LinkedHashSet<>();
            this.name = name;
        }

        public String getName() {
            return this.name;
        }

        public void addPlotter(final Plotter plotter) {
            this.plotters.add(plotter);
        }

        Set<Plotter> getPlotters() {
            return Collections.unmodifiableSet(this.plotters);
        }

        public int hashCode() {
            return this.name.hashCode();
        }

        public boolean equals(final Object object) {
            if (!(object instanceof Graph)) {
                return false;
            }
            final Graph graph = (Graph) object;
            return graph.name.equals(this.name);
        }

        void onOptOut() {
        }
    }

    public abstract static class Plotter {
        private final String name;

        protected Plotter() {
            this("Default");
        }

        Plotter(final String name) {
            super();
            this.name = name;
        }

        public abstract int getValue();

        String getColumnName() {
            return this.name;
        }

        void reset() {
        }

        public int hashCode() {
            return this.getColumnName().hashCode();
        }

        public boolean equals(final Object object) {
            if (!(object instanceof Plotter)) {
                return false;
            }
            final Plotter plotter = (Plotter) object;
            return plotter.name.equals(this.name) && plotter.getValue() == this.getValue();
        }
    }
}
