package net.lapismc.maxbans.sync;

import net.lapismc.maxbans.util.InputStreamWrapper;
import net.lapismc.maxbans.util.OutputStreamWrapper;
import org.bukkit.Bukkit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.Socket;

public class ServerToClientConnection {
    private Socket socket;
    private SyncServer server;
    private InputStreamWrapper in;
    private OutputStreamWrapper out;
    private Thread socketListener;

    ServerToClientConnection(final SyncServer server, final Socket s) {
        super();
        this.socketListener = new Thread(() -> {
            try {
                ServerToClientConnection.access$1(ServerToClientConnection.this, new InputStreamWrapper(ServerToClientConnection.this.socket.getInputStream()));
                ServerToClientConnection.access$2(ServerToClientConnection.this, new OutputStreamWrapper(ServerToClientConnection.this.socket.getOutputStream()));
                if (SyncUtil.isDebug()) {
                    ServerToClientConnection.log("Waiting for authentication from " + ServerToClientConnection.this);
                }
                try {
                    Thread.sleep(500L);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
                final ByteArrayOutputStream read = new ByteArrayOutputStream();
                byte b;
                while (ServerToClientConnection.this.in.available() > 0 && (b = ServerToClientConnection.this.in.readByte()) != 0) {
                    read.write(b);
                }
                if (SyncUtil.isDebug()) {
                    ServerToClientConnection.log("Read " + read.size() + " bytes of authentication.");
                }
                String data;
                try {
                    data = new String(read.toByteArray(), "ISO-8859-1");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                    return;
                }
                try {
                    Packet p = Packet.unserialize(data);
                    if (!p.getCommand().equals("connect") || !ServerToClientConnection.this.server.getPassword().equals(p.get("pass"))) {
                        ServerToClientConnection.log(ServerToClientConnection.this + " failed to send correct password! Disconnecting.");
                        ServerToClientConnection.this.close();
                    } else {
                        p = new Packet("connect");
                        ServerToClientConnection.this.write(p);
                        ServerToClientConnection.log("Connection Authenticated!");
                        ServerToClientConnection.this.server.getBlacklist().remove(ServerToClientConnection.this.socket.getInetAddress().getHostAddress());
                    }
                } catch (Exception e2) {
                    e2.printStackTrace();
                    ServerToClientConnection.log("Received malformed packet before authorising. Closing. Packet: " + data);
                    ServerToClientConnection.this.socket.close();
                    return;
                }
                ServerToClientConnection.this.server.getConnections().add(ServerToClientConnection.this);
                if (SyncUtil.isDebug()) {
                    ServerToClientConnection.log("Ready for syncing!");
                }
                while (!ServerToClientConnection.this.socket.isClosed()) {
                    data = ServerToClientConnection.this.in.readString();
                    Packet p;
                    try {
                        p = Packet.unserialize(data);
                    } catch (Exception e2) {
                        e2.printStackTrace();
                        ServerToClientConnection.log("Received malformed packet: " + data);
                        continue;
                    }
                    if (p.has("broadcast")) {
                        p.remove();
                        ServerToClientConnection.this.server.sendAll(p, ServerToClientConnection.this);
                    }
                }
            } catch (IOException e3) {
                ServerToClientConnection.log("Client disconnected.");
                if (SyncUtil.isDebug()) {
                    e3.printStackTrace();
                }
                try {
                    ServerToClientConnection.this.socket.close();
                } catch (IOException ex2) {
                    ex2.printStackTrace();
                }
            }
            if (SyncUtil.isDebug()) {
                ServerToClientConnection.log("Removing connection.");
            }
            ServerToClientConnection.this.server.getConnections().remove(ServerToClientConnection.this);
        });
        this.socket = s;
        this.server = server;
    }

    private static void log(final String s) {
        Bukkit.getConsoleSender().sendMessage("[MaxBans-SyncServer] " + s);
    }

    private static /* synthetic */ void access$1(final ServerToClientConnection serverToClientConnection, final InputStreamWrapper in) {
        serverToClientConnection.in = in;
    }

    private static /* synthetic */ void access$2(final ServerToClientConnection serverToClientConnection, final OutputStreamWrapper out) {
        serverToClientConnection.out = out;
    }

    public void start() {
        this.socketListener.setDaemon(true);
        this.socketListener.start();
    }

    boolean isOpen() {
        return !this.socket.isClosed();
    }

    public void close() {
        try {
            log("Closing connection!");
            this.socket.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    void write(final Packet p) {
        if (SyncUtil.isDebug()) {
            log("Writing " + p.serialize());
        }
        this.out.write(p.serialize());
    }

    public String toString() {
        return "Server->Client Connection (" + this.socket.getInetAddress().getHostAddress() + ") " + "Open: " + (this.socket != null && !this.socket.isClosed() && this.socket.isConnected());
    }
}
