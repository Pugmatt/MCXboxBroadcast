package com.rtm516.mcxboxbroadcast.core.sql;

import java.sql.*;




/**
 * Connects to and uses a MySQL database
 *
 * @author -_Husky_-
 * @author tips48
 */
public class MySQL extends Database {
    private final String user;
    private final String database;
    private final String password;
    private final String hostname;

    private final DatabaseTypes databasetype;

    private final Boolean autoReconnect;

    private Connection connection;

    /**
     * Creates a new MySQL instance
     *
     * @param hostname
     *            Name of the host
     * @param database
     *            Database name
     * @param username
     *            Username
     * @param password
     *            Password
     */
    public MySQL(String hostname, String database, String username, String password, DatabaseTypes databasetype, Boolean autoReconnect) {
        this.hostname = hostname;
        this.database = database;
        this.user = username;
        this.password = password;
        this.connection = null;
        this.databasetype = databasetype;
        this.autoReconnect = autoReconnect;
    }

    @Override
    public Connection openConnection() {
        try {
            String Driver = "";

            Class.forName("com.mysql.cj.jdbc.Driver");
            Driver = "jdbc:mysql://";
            String Extra = "";

            if (autoReconnect)
            {
                Extra += "&autoReconnect=true";
            }


            connection = DriverManager.getConnection(Driver + this.hostname + "/" + this.database + "?serverTimezone=UTC&useLegacyDatetimeCode=false" + Extra, this.user, this.password);
            System.out.println("- Database Connection Started -");

        } catch (SQLException e) {
            System.out.println("ERROR: Could not connect to Database server! because: " + e.getMessage());
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            System.out.println("ERROR: JDBC Driver not found!");
        }
        return connection;
    }

    @Override
    public boolean checkConnection() {
        return connection != null;
    }

    @Override
    public Connection getConnection() {
        return connection;
    }

    @Override
    public void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                System.out.println("ERROR: Error closing the MySQL Connection!");
                e.printStackTrace();
            }
        }
    }

    public ResultSet querySQL(String query) {
        Connection c = null;

        if (checkConnection()) {
            c = getConnection();
        } else {
            c = openConnection();
        }

        Statement s = null;

        try {
            s = c.createStatement();
        } catch (SQLException e1) {
            e1.printStackTrace();
        }

        ResultSet ret = null;

        try {
            ret = s.executeQuery(query);
        } catch (SQLException e) {
            e.printStackTrace();
        }

        closeConnection();

        return ret;
    }

    public void updateSQL(String update) {

        Connection c = null;

        if (checkConnection()) {
            c = getConnection();
        } else {
            c = openConnection();
        }

        Statement s = null;

        try {
            s = c.createStatement();
            s.executeUpdate(update);
        } catch (SQLException e1) {
            e1.printStackTrace();
        }

        closeConnection();

    }

}