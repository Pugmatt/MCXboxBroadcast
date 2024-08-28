package com.rtm516.mcxboxbroadcast.core.sql;

import java.io.File;
import java.io.FileReader;
import java.io.PrintWriter;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class Data {

    private Connection connection;


    public Data(Connection connections) {
        connection = connections;
        try {
            createTables();
        } catch (Exception e) {
            errorAlert(e);
        }
    }

    public void createTables() throws SQLException {
        // Create table if table does not exist
        String sqlCreate = "CREATE TABLE IF NOT EXISTS friend_logins"
                + "  (id         INTEGER PRIMARY KEY AUTO_INCREMENT,"
                + "   xuid            TEXT,"
                + "   last_login      DATETIME,"
                + "   INDEX index_xuid (xuid),"
                + "   INDEX index_last_login (last_login)"
                + ");";

        Statement stmt = connection.createStatement();
        stmt.execute(sqlCreate);
    }

    // If user exists
    public void userExists(String xuid) {
        Data db = this;
        new Thread(() -> {
            try {
                PreparedStatement searchUUID = connection.prepareStatement("SELECT COUNT(*) AS total FROM friend_logins where xuid = ?");
                searchUUID.setString(1, xuid);
                ResultSet RS = searchUUID.executeQuery();
                while (RS.next()) {
                    if (RS.getInt("total") > 0) {
                        PreparedStatement getUser = connection.prepareStatement("SELECT * FROM friend_logins WHERE xuid = ?;");
                        getUser.setString(1, xuid);
                        ResultSet rs = getUser.executeQuery();
                        while (rs.next()) {
                            PreparedStatement updateXuid = connection.prepareStatement("UPDATE friend_logins SET last_login = CURDATE() WHERE xuid= ?");
                            updateXuid.setString(1, xuid);
                            updateXuid.executeUpdate();
                        }
                    } else {
                        PreparedStatement s = connection.prepareStatement("INSERT INTO friend_logins (xuid, last_login) VALUES (?, CURDATE())");
                        s.setString(1, xuid);
                        s.executeUpdate();
                    }
                }
            } catch (Exception e) {
                errorAlert(e);
            }
        }).start();
    }

    public void updateXLastLogin(String xuid) {
        new Thread(() -> {
            try {
                PreparedStatement s = connection.prepareStatement("UPDATE friend_logins SET last_login = CURDATE() WHERE xuid= ?");
                s.setString(1, xuid);

                s.executeUpdate();
            } catch (Exception e) {
                errorAlert(e);
            }
        }).start();
    }

    public void errorAlert(Exception e) {
        System.out.println("[BedrockConnect] WARNING!!! DATABASE ERROR: " + e.getMessage());
        e.printStackTrace();
    }

}