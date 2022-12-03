package com.messenger.server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.SecureRandom;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

@SuppressWarnings({"SqlNoDataSourceInspection", "ForLoopReplaceableByForEach", "InfiniteLoopStatement", "resource"})
public class ServerStarter {

    private static Connection dbConnection;
    private static Socket socket;

    public static void main(String[] args) {

        initDb();
        try {
            ServerSocket serverSocket = new ServerSocket(8080);
            System.out.println("server started");
            while (true) {
                try {
                    socket = serverSocket.accept();

                    new Thread(() -> {
                        System.out.println("new client connected " + socket.getInetAddress());

                        try {
                            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                            PrintWriter writer = new PrintWriter(socket.getOutputStream());

                            readCommands(reader, writer);
                        } catch (Throwable e) {
                            e.printStackTrace();
                            System.err.println(" ! Finish handle input connection with error " + e);
                        }
                    }).start();


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
                    " from_user_id INTEGER, to_user_id INTEGER, message TEXT, date INTEGER);");

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
                case "/serverTime" -> {
                    writer.println(getDate());
                    writer.flush();
                }
                case "/register" -> {
                    String login = reader.readLine();
                    String password = reader.readLine();
                    System.out.println("register - get name and password: " + login + " -- " + password);
                    boolean result = addUserInDb(login, password);
                    writer.println(result);
                    writer.flush();
                }
                case "/login" -> {
                    String login = reader.readLine();
                    String password = reader.readLine();
                    String token = createAuthToken();
                    System.out.println("__________________");
                    System.out.println("login = " + login);
                    System.out.println("password = " + password);
                    System.out.println("token = " + token);
                    Integer userId = getUserIdFromTable(login, password);
                    if (userId == null) {
                        System.out.println("Invalid login or password!");
                        String answer = "Invalid login or password!";
                        writer.println(answer);
                        writer.flush();
                    } else {
                        System.out.println("userId = " + userId);
                        System.out.println("------------------");
                        boolean tryCreateNewTokenInTableResult = insertTokenInDb(userId, token);
                        System.out.println("User with login " + login + " authorized ");
                        writer.println(tryCreateNewTokenInTableResult);
                        String userIdStr = userId.toString();
                        writer.println(userIdStr);
                        writer.println(token);
                        writer.flush();
                    }
                }
                case "/sendMessage" -> {
                    // params
                    String userIdToSendMessageStr = reader.readLine();
                    int userIdToSendMessage = Integer.parseInt(userIdToSendMessageStr);
                    String textMessage = reader.readLine();
                    String token = reader.readLine();
                    System.out.println("read user message and token" + userIdToSendMessage + "  " + textMessage + ", " + token);

                    // body
                    int fromUserId = getUserIdFromSendMessage(token);
                    if (!isUserExisted(userIdToSendMessage)) {
                        System.out.println("User not found");
                        boolean userExisted = false;
                        writer.println(userExisted);
                        writer.flush();
                        break;
                    }
                    if (checkedAuthToken(token)) {
                        int toUserId = getUserIdForSendMessage(userIdToSendMessage);
                        System.out.println("Message sent");
                        boolean sendMessage = addMessageToDb(fromUserId, toUserId, textMessage, System.currentTimeMillis());
                        writer.println(sendMessage);
                        writer.flush();
                        System.out.println("User " + userIdToSendMessageStr + " received message");
                    } else {
                        System.out.println("User not authorized!");
                        String answer = "User not authorized! Please enter login and password, and try again";
                        writer.println(answer);
                        writer.flush();
                    }
                }
                case "/readMessages" -> {
                    long sinceDate = Long.parseLong(reader.readLine());
                    String token = reader.readLine();
                    System.out.println("read messages sinceDate token " + sinceDate + " " + token);
                    Integer userId = getUserIdByAuthToken(token);
                    System.out.println("userId = " + userId);
                    if (userId != null) {
                        writer.println(true);
                        ArrayList<Message> messages = getMessages(userId, sinceDate);
                        writer.println(messages.size());
                        for (int i = 0; i < messages.size(); i++) {
                            Message message = messages.get(i);
                            writer.println(message.messageId);
                            writer.println(message.fromUserId);
                            writer.println(message.toUserId);
                            writer.println(message.message);
                            writer.println(message.date);
                        }
                        writer.flush();
                    } else {
                        writer.println(false);
                        String answer = "User not found";
                        writer.println(answer);
                        writer.flush();
                    }
                }
                case "/getUserById" -> {
                    String userIdStr = reader.readLine();
                    int userId = Integer.parseInt(userIdStr);

                    String token = reader.readLine();
                    System.out.println("Token is " + checkedAuthToken(token));

                    if (checkedAuthToken(token)) {
                        String getUsernameById = getUserNameById(userId);
                        System.out.println("Username: " + getUsernameById);
                        if (getUsernameById != null) {
                            writer.println(getUserNameById(userId));
                            writer.flush();
                        } else {
                            String answer = "User not found";
                            writer.println(answer);
                            writer.flush();
                        }
                    }
                }
                case "/getUserByLogin" -> {
                    String login = reader.readLine();
                    String token = reader.readLine();
                    if (checkedAuthToken(token)) {
                        String getUserNameByLogin = getUserNameByLogin(login);
                        System.out.println("Username: " + getUserNameByLogin);
                        System.out.println("User Id: " + getUserIdByLogin(login));
                        if (getUserNameByLogin != null) {
                            writer.println(getUserNameByLogin(login));
                            writer.println(getUserIdByLogin(login));
                            writer.flush();
                        } else {
                            String answer = "User not found";
                            writer.println(answer);
                            writer.flush();
                        }
                    }
                }
            }
        }
    }

    private static Integer getUserIdByLogin(String login) {
        Integer userId = null;
        String query = "select user_id from users where name = ?";
        try {
            PreparedStatement preparedStatement = dbConnection.prepareStatement(query);
            preparedStatement.setString(1,login);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                userId = resultSet.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return userId;
    }

    private static ArrayList<Message> getMessages(int toUserId, long date) {
        try {
            ArrayList<Message> result = new ArrayList<>();
            String query = "select * from messages where (to_user_id = \"" + toUserId + "\" or from_user_id = \"" + toUserId + "\") and date >= " + date + "";
            ResultSet resultSet = dbConnection.createStatement()
                    .executeQuery(query);
            while (resultSet.next()) {
                Message m = new Message();
                m.messageId = resultSet.getInt("message_id");
                m.fromUserId = resultSet.getInt("from_user_id");
                m.toUserId = resultSet.getInt("to_user_id");
                m.message = resultSet.getString("message");
                m.date = resultSet.getLong("date");
                result.add(m);
            }
            resultSet.close();
            return result;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static int getUserIdFromSendMessage(String token) {
        int fromUserId = 0;
        try {
            String query = "select user_id from tokens where auth_token = \"" + token + "\"";
            ResultSet resultSet = dbConnection.createStatement()
                    .executeQuery(query);
            while (resultSet.next()) {
                fromUserId = resultSet.getInt(1);
            }
            resultSet.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return fromUserId;

    }

    private static String getUserNameByLogin(String userName) {
        String userNameByLogin = null;
        try {
            String query = "select name from users where name = \"" + userName + "\"";
            ResultSet resultSet = dbConnection.createStatement()
                    .executeQuery(query);
            while (resultSet.next()) {
                userNameByLogin = resultSet.getString(1);
            }
            resultSet.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return userNameByLogin;
    }

    private static String getUserNameById(int userId) {
        String userNameById = null;
        try {
            String query = "select name from users where user_id = " + userId;
            ResultSet resultSet = dbConnection.createStatement()
                    .executeQuery(query);
            while (resultSet.next()) {
                userNameById = resultSet.getString(1);
            }
            resultSet.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return userNameById;
    }

    private static Integer getUserIdByAuthToken(String token) {
        Integer getUserByToken = null;
        String query = "select user_id from tokens where auth_token = ?";
        try {
            PreparedStatement preparedStatement = dbConnection.prepareStatement(query);
            preparedStatement.setString(1, token);
            ResultSet resultSet = preparedStatement
                    .executeQuery();
            while (resultSet.next()) {
                getUserByToken = resultSet.getInt(1);
            }
            resultSet.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return getUserByToken;
    }

    private static boolean checkedAuthToken(String token) {
        try {
            String query = "select * from tokens where auth_token = \"" + token + "\"";
            ResultSet resultSet = dbConnection.createStatement().executeQuery(query);
            boolean isExisted = resultSet.next();
            resultSet.close();
            return isExisted;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static int getUserIdForSendMessage(int userIdToSendMessage) {
        int toUserId = 0;
        try {
            String query = "select * from users where user_id = " + userIdToSendMessage;
            ResultSet resultSet = dbConnection.createStatement()
                    .executeQuery(query);
            while (resultSet.next()) {
                toUserId = resultSet.getInt(1);
            }
            resultSet.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return toUserId;
    }

    private static boolean addMessageToDb(int fromUserId, Integer toUserId, String textMessage, long date) {
        String query = "INSERT INTO messages (from_user_id, to_user_id, message, date) VALUES (?, ?, ?, ?)";
        try {
            PreparedStatement preparedStatement = dbConnection.prepareStatement(query);
            preparedStatement.setInt(1, fromUserId);
            preparedStatement.setInt(2, toUserId);
            preparedStatement.setString(3, textMessage);
            preparedStatement.setLong(4, date);
            preparedStatement.executeUpdate();
            preparedStatement.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return true;
    }

    private static Integer getUserIdFromTable(String login, String pass) {
        Integer userId = null;
        try {
            String query = "select * from users where name = \"" + login + "\" and password = \"" + pass + "\"";
            ResultSet resultSet = dbConnection.createStatement()
                    .executeQuery(query);
            while (resultSet.next()) {
                userId = resultSet.getInt(1);
            }
            resultSet.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return userId;
    }

    private static boolean insertTokenInDb(Integer userId, String token) {
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
            String query = "insert into users (name, password) values (\"" + name + "\",\"" + password + "\")";
            dbConnection.createStatement()
                    .execute(query);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return true;
    }

    private static boolean isUserExisted(int userId) {
        try {
            String query = "select * from users where user_id =" + userId;
            ResultSet resultSet = dbConnection.createStatement()
                    .executeQuery(query);
            boolean isExisted = resultSet.next();
            resultSet.close();
            return isExisted;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean isUserExisted(String name) {
        try {
            String query = "select * from users where name = \"" + name + "\"";
            ResultSet resultSet = dbConnection.createStatement()
                    .executeQuery(query);
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

    private static String getDate() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss z");
        return dateFormat.format(new Date());
    }
}
