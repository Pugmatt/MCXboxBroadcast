package com.rtm516.mcxboxbroadcast.bootstrap.standalone;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.rtm516.mcxboxbroadcast.core.BuildData;
import com.rtm516.mcxboxbroadcast.core.SessionInfo;
import com.rtm516.mcxboxbroadcast.core.SessionManager;
import com.rtm516.mcxboxbroadcast.core.configs.StandaloneConfig;
import com.rtm516.mcxboxbroadcast.core.exceptions.SessionCreationException;
import com.rtm516.mcxboxbroadcast.core.exceptions.SessionUpdateException;
import com.rtm516.mcxboxbroadcast.core.ping.PingUtil;
import com.rtm516.mcxboxbroadcast.core.storage.FileStorageManager;
import org.cloudburstmc.protocol.bedrock.BedrockPong;
import com.rtm516.mcxboxbroadcast.core.sql.Data;
import com.rtm516.mcxboxbroadcast.core.sql.DatabaseTypes;
import com.rtm516.mcxboxbroadcast.core.sql.MySQL;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class StandaloneMain {
    private static StandaloneConfig config;
    private static StandaloneLoggerImpl logger;
    private static SessionInfo sessionInfo;

    public static SessionManager sessionManager;

    private static Connection connection;


    public static void main(String[] args) throws Exception {
        logger = new StandaloneLoggerImpl(LoggerFactory.getLogger(StandaloneMain.class));

        logger.info("Starting MCXboxBroadcast Standalone " + BuildData.VERSION);

        String configFileName = "config.yml";
        File configFile = new File(configFileName);

        // Create the config file if it doesn't exist
        if (!configFile.exists()) {
            try (FileOutputStream fos = new FileOutputStream(configFileName)) {
                try (InputStream input = StandaloneMain.class.getClassLoader().getResourceAsStream(configFileName)) {
                    byte[] bytes = new byte[input.available()];

                    //noinspection ResultOfMethodCallIgnored
                    input.read(bytes);

                    fos.write(bytes);

                    fos.flush();
                }
            } catch (IOException e) {
                logger.error("Failed to create config", e);
                return;
            }
        }

        try {
            config = new ObjectMapper(new YAMLFactory()).readValue(configFile, StandaloneConfig.class);
        } catch (IOException e) {
            logger.error("Failed to load config", e);
            return;
        }

        logger.setDebug(config.debugLog());

        String hostname = "localhost";
        String database = "bedrock-connect";
        String username = "root";
        String password = "";

        HashMap<String, String> settings = new HashMap<>();

        for(String str : args) {
            if(str.indexOf("=") !=  -1 && str.indexOf("=") < str.length() - 1) {
                settings.put(str.substring(0, str.indexOf("=")), str.substring(str.indexOf("=") + 1));
            }
        }

        for (Map.Entry<String, String> setting : settings.entrySet()) {
            switch (setting.getKey().toLowerCase()) {
                case "db_host":
                    hostname = setting.getValue();
                    break;
                case "db_db":
                    database = setting.getValue();
                    break;
                case "db_user":
                    username = setting.getValue();
                    break;
                case "db_pass":
                    password = setting.getValue();
                    break;
            }
        }

        MySQL MySQL = new MySQL(hostname, database, username, password, DatabaseTypes.mysql, false);

        connection = MySQL.openConnection();

        SessionManager.data = new Data(connection);

        // Keep MySQL connection alive
        Timer timer = new Timer();
        TimerTask task = new TimerTask() {
            int sec;

            public void run() {
                try {
                    if (connection == null || connection.isClosed()) {
                        connection = MySQL.openConnection();
                    } else {
                        if (sec == 600) {
                            try {
                                ResultSet rs = connection
                                        .createStatement()
                                        .executeQuery(
                                                "SELECT 1");
                                rs.next();
                            } catch (SQLException e) {
                                // TODO Auto-generated
                                // catch block
                                e.printStackTrace();
                            }
                            sec = 0;
                        }
                    }
                } catch (SQLException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                sec++;
            }
        };
        timer.scheduleAtFixedRate(task, 0L, 60 * 1000);

        ArrayList<String> activePlayers = new ArrayList<>();
        try {
            ResultSet rs = connection
                    .createStatement()
                    .executeQuery("select * from friend_logins WHERE last_login > NOW() - INTERVAL 1 WEEK;");
            while (rs.next()) {
                activePlayers.add(rs.getString("xuid"));
            }
        } catch(Exception e) {
            activePlayers = null;
            e.printStackTrace();
            System.out.println("Error getting activePlayers. Won't run inactive purge this run.");
        }

        SessionManager.activePlayers = activePlayers;

        sessionManager = new SessionManager(new FileStorageManager("./cache"), logger);

        sessionInfo = config.session().sessionInfo();

        PingUtil.setWebPingEnabled(config.session().webQueryFallback());

        // Sync the session info from the server if needed
        updateSessionInfo(sessionInfo);

        createSession();

        logger.start();
    }

    public static void restart() {
        try {
            sessionManager.shutdown();

            sessionManager = new SessionManager(new FileStorageManager("./cache"), logger);

            createSession();
        } catch (SessionCreationException | SessionUpdateException e) {
            logger.error("Failed to restart session", e);
        }
    }

    private static void createSession() throws SessionCreationException, SessionUpdateException {
        sessionManager.restartCallback(StandaloneMain::restart);
        sessionManager.init(sessionInfo, config.friendSync());

        sessionManager.scheduledThread().scheduleWithFixedDelay(() -> {
            updateSessionInfo(sessionInfo);

            try {
                // Update the session
                sessionManager.updateSession(sessionInfo);
                if (config.suppressSessionUpdateInfo()) {
                    sessionManager.logger().debug("Updated session!");
                } else {
                    sessionManager.logger().info("Updated session!");
                }
            } catch (SessionUpdateException e) {
                sessionManager.logger().error("Failed to update session", e);
            }
        }, config.session().updateInterval(), config.session().updateInterval(), TimeUnit.SECONDS);
    }

    private static void updateSessionInfo(SessionInfo sessionInfo) {
        if (config.session().queryServer()) {
            try {
                InetSocketAddress addressToPing = new InetSocketAddress(sessionInfo.getIp(), sessionInfo.getPort());
                BedrockPong pong = PingUtil.ping(addressToPing, 1500, TimeUnit.MILLISECONDS).get();

                // Update the session information
                sessionInfo.setHostName(pong.motd());
                sessionInfo.setWorldName(pong.subMotd());
                sessionInfo.setVersion(pong.version());
                sessionInfo.setProtocol(pong.protocolVersion());
                sessionInfo.setPlayers(pong.playerCount());
                sessionInfo.setMaxPlayers(pong.maximumPlayerCount());
            } catch (InterruptedException | ExecutionException e) {
                sessionManager.logger().error("Failed to ping server", e);
            }
        }
    }
}
