package com.messenger.server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.SecureRandom;
import java.sql.*;
import java.util.Date;
import java.util.Random;

public class ServerStarter {

    private static Connection dbConnection;

    public static void main(String[] args) {

        initDb();
        try {
            ServerSocket serverSocket = new ServerSocket(8080);
            System.out.println("server started");
            while (true) {
                try {
                    Socket socket = serverSocket.accept();

                    System.out.println("new client connected " + socket.getInetAddress());
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    PrintWriter writer = new PrintWriter(socket.getOutputStream());

                    readCommands(reader, writer);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void initDb() {
        try {
            System.out.println("Starting init db");
            Class.forName("org.sqlite.JDBC");
            dbConnection = DriverManager.getConnection("jdbc:sqlite:test.db");
            Statement statement = dbConnection.createStatement();
            statement.executeUpdate("create table if not exists  users (user_id INTEGER PRIMARY KEY ," +
                    " name VARCHAR, password VARCHAR);");

            statement.executeUpdate("create table if not exists  messages (message_id INTEGER PRIMARY KEY ," +
                    " from_user_id INTEGER, to_user_id VARCHAR, message TEXT);");

            statement.executeUpdate("create table if not exists  tokens (tokens_id INTEGER PRIMARY KEY ," +
                    " user_id INTEGER, auth_token VARCHAR);");

        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private static void readCommands(BufferedReader reader, PrintWriter writer) throws IOException {
        while (true) {
            String command = reader.readLine();
            switch (command) {
                case "serverTime": {
                    writer.println(new Date());
                    writer.flush();
                    break;
                }

                case "add": {
                    String name = reader.readLine();
                    String password = reader.readLine();

                    System.out.println("register - get name and password: " + name + " -- " + password);

                    boolean result = addUserInDb(name, password);

                    writer.println(result);
                    writer.flush();
                    break;
                }

                case "login": {
                    String login = reader.readLine();
                    String pass = reader.readLine();
                    String token = createAuthToken();
                    System.out.println("__________________");
                    System.out.println("login = " + login);
                    System.out.println("password = " + pass);
                    System.out.println("token = " + token);
                    Integer userId = getUserIdFromTable(login, pass);
                    if (userId == null) {
                        System.out.println("Invalid login or password!");
                        writer.println("Invalid login or password!");
                        writer.flush();
                    } else {
                        System.out.println("userId = " + userId);
                        System.out.println("------------------");
                        boolean tryCreateNewTokenInTable = tryInsertInToTokenTable(userId, token);

                        System.out.println("User with login " + login + " authorized ");

                        writer.println(tryCreateNewTokenInTable);
                        String userIdStr = userId.toString();
                        writer.println(userIdStr);
                        writer.println(token);
                        writer.flush();
                    }
                    break;
                }

                case "delete": {
                    String login = reader.readLine();
                    boolean deleteUser = deleteUserFromDb(login);
                    writer.println(deleteUser);
                    writer.flush();
                    System.out.println("User " + login + " deleted");
                    break;
                }

                case "send_message": {
                    writer.flush();
                    String userToSendMessage = reader.readLine();
                    if (!isUserExisted(userToSendMessage)) {
                        System.out.println("User not found");
                        boolean userExisted = false;
                        writer.println(userExisted);
                        writer.flush();
                        break;
                    }
                    writer.flush();
                    String fromUserIdStr = reader.readLine();
                    int fromUserId = Integer.parseInt(fromUserIdStr);
                    writer.flush();
                    String textMessage = reader.readLine();
                    writer.flush();
                    String token = reader.readLine();
                    if (checkedAuthToken(token)) {
                        int toUserId = getUserIdForSendMessage(userToSendMessage);
                        System.out.println("Message sent");
                        boolean sendMessage = addMessageToDb(fromUserId, toUserId, textMessage);
                        writer.println(sendMessage);
                        writer.flush();
                        System.out.println("User " + userToSendMessage + " received message");
                    }
                    else {
                        System.out.println("User not authorized!");
                        String answer = "User not authorized! Please enter login and password, and try again";
                        writer.println(answer);
                        writer.flush();
                    }
                    break;
                }
            }
        }
    }

    public static boolean checkedAuthToken (String token) {
        try {
            ResultSet resultSet = dbConnection.createStatement().executeQuery("select * from tokens where auth_token = \"" + token + "\"  ");
           boolean isExisted = resultSet.next();
            resultSet.close();
            return isExisted;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static int getUserIdForSendMessage (String userToSendMessage) {
        int toUserId = 0;
        try {
            ResultSet resultSet = dbConnection.createStatement()
                    .executeQuery("select * from users where name = \"" + userToSendMessage + "\"");
            while (resultSet.next()) {
                toUserId = resultSet.getInt(1);
            }
            resultSet.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return toUserId;
    }

    private static boolean addMessageToDb(Integer fromUserId, Integer toUserId, String textMessage) {
        String query = "INSERT INTO messages (from_user_id, to_user_id, message) VALUES (?, ?, ?)";
        try {
            PreparedStatement preparedStatement = dbConnection.prepareStatement(query);
            preparedStatement.setInt(1, fromUserId);
            preparedStatement.setInt(2, toUserId);
            preparedStatement.setString(3, textMessage);
            preparedStatement.executeUpdate();
            preparedStatement.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return true;
    }

    private static boolean deleteUserFromDb(String login) {
        try {
            dbConnection.createStatement().executeUpdate("delete from users where name = \"" + login + "\" ");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return true;
    }

    private static Integer getUserIdFromTable(String login, String pass) {
        Integer userId = null;
        try {
            ResultSet resultSet = dbConnection.createStatement()
                    .executeQuery("select * from users where name = \"" + login + "\" and password = \"" + pass + "\"");
            while (resultSet.next()) {
                userId = resultSet.getInt(1);
            }
            resultSet.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return userId;
    }

    private static boolean tryInsertInToTokenTable(Integer userId, String token) {
        String query = "INSERT INTO tokens (user_id, auth_token) VALUES (?, ?)";
        try {
            PreparedStatement preparedStatement = dbConnection.prepareStatement(query);
            preparedStatement.setInt(1, userId);
            preparedStatement.setString(2, token);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return true;
    }

    private static boolean addUserInDb(String name, String password) {
        if (isUserExisted(name)) {
            return false;
        }
        try {
            dbConnection.createStatement().execute(
                    "insert into users (name, password) values (\"" + name + "\",\"" + password + "\")"
            );

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return true;
    }

    private static boolean isUserExisted(String name) {
        try {
            ResultSet resultSet = dbConnection.createStatement()
                    .executeQuery("select * from users where name = \"" + name + "\"  ");

            boolean isExisted = resultSet.next();

            resultSet.close();

            return isExisted;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private static String createAuthToken() {
        String str = "abcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            int number = random.nextInt(36);
            sb.append(str.charAt(number));
        }
        return sb.toString();
    }
}
