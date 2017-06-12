package net.lapismc.maxbans.commands.bridge;

import net.lapismc.maxbans.MaxBans;
import net.lapismc.maxbans.database.Database;

import java.sql.SQLException;

public class SQLiteBridge implements Bridge {
    private Database db;

    public SQLiteBridge(final Database db) {
        super();
        this.db = db;
    }

    public void export() throws SQLException {
        MaxBans.instance.getDB().copyTo(this.db);
    }

    public void load() throws SQLException {
        this.db.copyTo(MaxBans.instance.getDB());
    }
}
