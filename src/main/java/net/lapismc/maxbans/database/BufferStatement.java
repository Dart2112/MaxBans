package net.lapismc.maxbans.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;

public class BufferStatement {
    private Object[] values;
    private String query;

    BufferStatement(final String query, final Object... values) {
        super();
        this.query = query;
        this.values = values;
    }

    PreparedStatement prepareStatement(final Connection con) throws SQLException {
        final PreparedStatement ps = con.prepareStatement(this.query);
        for (int i = 1; i <= this.values.length; ++i) {
            ps.setObject(i, String.valueOf(this.values[i - 1]));
        }
        return ps;
    }

    public String toString() {
        return "Query: " + this.query + ", values: " + Arrays.toString(this.values);
    }
}
