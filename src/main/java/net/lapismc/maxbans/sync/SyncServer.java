package net.lapismc.maxbans.sync;

import org.bukkit.Bukkit;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

public class SyncServer {
    private int port;
    private String pass;
    private HashSet<ServerToClientConnection> connections;
    private HashMap<String, Integer> blacklist;
    private ServerSocket core;
    private Thread watcher;

    public SyncServer(final int port, final String pass) {
        super();
        this.connections = new HashSet<>();
        this.blacklist = new HashMap<>();
        this.watcher = new Thread(() -> {
            while (!SyncServer.this.core.isClosed()) {
                try {
                    final Socket s = SyncServer.this.core.accept();
                    final String ip = s.getInetAddress().getHostAddress();
                    Integer i = SyncServer.this.blacklist.get(ip);
                    if (i == null) {
                        i = 1;
                    } else {
                        ++i;
                    }
                    SyncServer.this.blacklist.put(ip, i);
                    if (i >= 10) {
                        if (SyncUtil.isDebug()) {
                            SyncServer.log("Connection from " + ip + " denied - Too many failed authentication attempts (" + i + ").");
                        }
                        s.close();
                    } else {
                        if (SyncUtil.isDebug()) {
                            SyncServer.log("Connection request from " + s.getInetAddress().getHostAddress());
                        }
                        final ServerToClientConnection con = new ServerToClientConnection(SyncServer.this, s);
                        con.start();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        this.port = port;
        try {
            this.pass = SyncUtil.encrypt(pass);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt password: " + e.getMessage());
        }
    }

    private static void log(final String s) {
        Bukkit.getConsoleSender().sendMessage("[MaxBans-SyncServer] " + s);
    }

    HashMap<String, Integer> getBlacklist() {
        return this.blacklist;
    }

    HashSet<ServerToClientConnection> getConnections() {
        return this.connections;
    }

    public void start() throws IOException {
        if (SyncUtil.isDebug()) {
            log("Starting server.");
        }
        this.core = new ServerSocket(this.port);
        this.watcher.setDaemon(true);
        this.watcher.start();
        if (SyncUtil.isDebug()) {
            log("Server started successfully.");
        }
    }

    public void stop() {
        try {
            this.core.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    void sendAll(final Packet p, final ServerToClientConnection except) {
        final Iterator<ServerToClientConnection> sit = this.connections.iterator();
        while (sit.hasNext()) {
            final ServerToClientConnection con = sit.next();
            if (con == except) {
                continue;
            }
            if (!con.isOpen()) {
                sit.remove();
            } else {
                try {
                    con.write(p);
                } catch (Exception e) {
                    if (SyncUtil.isDebug()) {
                        e.printStackTrace();
                    }
                    if (SyncUtil.isDebug()) {
                        log("Failed to send data to client.");
                    }
                    con.close();
                    sit.remove();
                }
            }
        }
    }

    String getPassword() {
        return this.pass;
    }
}
