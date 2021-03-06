package net.lapismc.maxbans.database;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.LinkedList;

public class SQLiteCore implements DatabaseCore {
    private final LinkedList<BufferStatement> queue;
    private Connection connection;
    private File dbFile;
    private Thread watcher;

    public SQLiteCore(final File dbFile) {
        super();
        this.queue = new LinkedList<>();
        this.dbFile = dbFile;
    }

    public Connection getConnection() {
        try {
            if (this.connection != null && !this.connection.isClosed()) {
                return this.connection;
            }
        } catch (SQLException ignored) {
        }
        if (this.dbFile.exists()) {
            try {
                Class.forName("org.sqlite.JDBC");
                return this.connection = DriverManager.getConnection("jdbc:sqlite:" + this.dbFile);
            } catch (ClassNotFoundException | SQLException e2) {
                return null;
            }
        }
        try {
            this.dbFile.createNewFile();
            return this.getConnection();
        } catch (IOException e3) {
            return null;
        }
    }

    public void queue(final BufferStatement bs) {
        synchronized (this.queue) {
            this.queue.add(bs);
        }
        // monitorexit(this.queue)
        if (this.watcher == null || !this.watcher.isAlive()) {
            this.startWatcher();
        }
    }

    public void flush() {
        while (!this.queue.isEmpty()) {
            final BufferStatement bs;
            synchronized (this.queue) {
                bs = this.queue.removeFirst();
            }
            // monitorexit(this.queue)
            try {
                final PreparedStatement ps = bs.prepareStatement(this.getConnection());
                ps.execute();
                ps.close();
            } catch (SQLException ignored) {
            }
        }
    }

    public void close() {
        this.flush();
    }

    private void startWatcher() {
        (this.watcher = new Thread(() -> {
            try {
                Thread.sleep(30000L);
            } catch (InterruptedException ignored) {
            }
            SQLiteCore.this.flush();
        })).start();
    }
}
