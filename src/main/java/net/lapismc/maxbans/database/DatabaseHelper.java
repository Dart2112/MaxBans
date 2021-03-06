package net.lapismc.maxbans.database;

import net.lapismc.maxbans.util.Util;
import org.bukkit.ChatColor;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper {
    public static void setup(final Database db) throws SQLException {
        createTables(db);
    }

    private static void createTables(final Database db) throws SQLException {
        if (!db.hasTable("bans")) {
            createBanTable(db);
        }
        if (!db.hasTable("ipbans")) {
            createIPBanTable(db);
        }
        if (!db.hasTable("mutes")) {
            createMuteTable(db);
        }
        if (!db.hasColumn("mutes", "reason")) {
            try {
                db.getConnection().prepareStatement("ALTER TABLE mutes ADD COLUMN reason TEXT(100)").execute();
                System.out.println("Updating mutes table (Adding reason column)");
            } catch (SQLException ignored) {
            }
        }
        if (!db.hasTable("iphistory")) {
            createIPHistoryTable(db);
        }
        if (!db.hasTable("warnings")) {
            createWarningsTable(db);
        }
        if (!db.hasTable("proxys")) {
            createProxysTable(db);
        }
        if (!db.hasTable("history")) {
            createHistoryTable(db);
        }
        if (!db.hasTable("rangebans")) {
            createRangeBansTable(db);
        }
        if (!db.hasTable("whitelist")) {
            createWhitelistTable(db);
        }
        if (!db.hasTable("players")) {
            createPlayersTable(db);
            final ResultSet rs = db.getConnection().prepareStatement("SELECT * FROM iphistory").executeQuery();
            final List<String> names = new ArrayList<>();
            while (rs.next()) {
                names.add(rs.getString("name"));
            }
            rs.close();
            if (!names.isEmpty()) {
                System.out.println("Created players table. Now converting old player list. Size: " + names.size() + ", please wait :)");
                int n = 0;
                final PreparedStatement ps = db.getConnection().prepareStatement("INSERT INTO players (name, actual) VALUES (?, ?)");
                for (final String name : names) {
                    ++n;
                    ps.setString(1, name);
                    ps.setString(2, name);
                    ps.addBatch();
                    if (n % 100 == 0) {
                        final long start = System.currentTimeMillis();
                        ps.executeBatch();
                        final long end = System.currentTimeMillis();
                        final long remaining = (names.size() - n) / 100 * (end - start);
                        System.out.println(String.valueOf(n) + " records copied... Remaining: " + Util.getTime(remaining));
                    }
                }
                ps.executeBatch();
                rs.close();
            }
        }
        if (!db.hasColumn("warnings", "expires")) {
            try {
                db.getConnection().prepareStatement("ALTER TABLE warnings ADD expires long").execute();
            } catch (SQLException ignored) {
            }
        }
        if (!db.hasColumn("history", "name")) {
            try {
                db.getConnection().prepareStatement("ALTER TABLE history ADD banner TEXT(30)").execute();
                db.getConnection().prepareStatement("ALTER TABLE history ADD name TEXT(30)").execute();
                db.getConnection().prepareStatement("UPDATE history SET banner = 'unknown', name = 'unknown'").execute();
                System.out.println("History has no banner/name, adding them...");
            } catch (SQLException ignored) {
            }
        }
    }

    private static void createWhitelistTable(final Database db) {
        final String query = "CREATE TABLE whitelist (name TEXT(30) NOT NULL)";
        try {
            final Statement st = db.getConnection().createStatement();
            st.execute(query);
        } catch (SQLException e) {
            System.out.println(ChatColor.RED + "Could not create whitelist table.");
        }
    }

    private static void createRangeBansTable(final Database db) {
        final String query = "CREATE TABLE rangebans (banner TEXT(100) NOT NULL, reason TEXT(100), start TEXT(30), end TEXT(30), created BIGINT NOT NULL, expires BIGINT NOT NULL)";
        try {
            final Statement st = db.getConnection().createStatement();
            st.execute(query);
        } catch (SQLException e) {
            System.out.println(ChatColor.RED + "Could not create rangebans table.");
        }
    }

    private static void createHistoryTable(final Database db) {
        final String query = "CREATE TABLE history (created BIGINT NOT NULL, message TEXT(100));";
        try {
            final Statement st = db.getConnection().createStatement();
            st.execute(query);
        } catch (SQLException e) {
            System.out.println(ChatColor.RED + "Could not create history table.");
        }
    }

    private static void createPlayersTable(final Database db) {
        final String query = "CREATE TABLE players (name TEXT(30) NOT NULL, actual TEXT(30) NOT NULL);";
        try {
            final Statement st = db.getConnection().createStatement();
            st.execute(query);
        } catch (SQLException e) {
            System.out.println(ChatColor.RED + "Could not create players table.");
        }
    }

    private static void createBanTable(final Database db) {
        final String query = "CREATE TABLE bans ( name  TEXT(30) NOT NULL, reason  TEXT(100), banner  TEXT(30), time  BIGINT NOT NULL DEFAULT 0, expires  BIGINT NOT NULL DEFAULT 0 );";
        try {
            final Statement st = db.getConnection().createStatement();
            st.execute(query);
        } catch (SQLException e) {
            System.out.println(ChatColor.RED + "Could not create bans table.");
        }
    }

    private static void createProxysTable(final Database db) {
        final String query = "CREATE TABLE proxys (ip TEXT(30) NOT NULL, status TEXT(30), created BIGINT NOT NULL)";
        try {
            final Statement st = db.getConnection().createStatement();
            st.execute(query);
        } catch (SQLException e) {
            System.out.println(ChatColor.RED + "Could not create proxys table.");
        }
    }

    private static void createIPBanTable(final Database db) {
        final String query = "CREATE TABLE ipbans ( ip  TEXT(20) NOT NULL, reason  TEXT(100), banner  TEXT(30), time  BIGINT NOT NULL DEFAULT 0, expires  BIGINT NOT NULL DEFAULT 0 );";
        try {
            final Statement st = db.getConnection().createStatement();
            st.execute(query);
        } catch (SQLException e) {
            System.out.println(ChatColor.RED + "Could not create ipbans table.");
        }
    }

    private static void createMuteTable(final Database db) {
        final String query = "CREATE TABLE mutes ( name  TEXT(30) NOT NULL, muter  TEXT(30), time  BIGINT DEFAULT 0, expires  BIGINT DEFAULT 0, reason  TEXT(100) NOT NULL );";
        try {
            final Statement st = db.getConnection().createStatement();
            st.execute(query);
        } catch (SQLException e) {
            System.out.println(ChatColor.RED + "Could not create mutes table.");
        }
    }

    private static void createIPHistoryTable(final Database db) {
        final String query = "CREATE TABLE iphistory ( name  TEXT(30) NOT NULL, ip  TEXT(20) NOT NULL);";
        try {
            final Statement st = db.getConnection().createStatement();
            st.execute(query);
        } catch (SQLException e) {
            System.out.println(ChatColor.RED + "Could not create iphistory table.");
        }
    }

    private static void createWarningsTable(final Database db) {
        final String query = "CREATE TABLE warnings (name TEXT(30) NOT NULL, reason TEXT(100) NOT NULL, banner TEXT(30) NOT NULL, expires BIGINT(30));";
        try {
            final Statement st = db.getConnection().createStatement();
            st.execute(query);
        } catch (SQLException e) {
            System.out.println(ChatColor.RED + "Could not create warnings table.");
        }
    }
}
