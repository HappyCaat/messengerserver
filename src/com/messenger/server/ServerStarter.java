package com.messenger.server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.*;
import java.util.Date;

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
                    " user VARCHAR, message TEXT);");

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

                    System.out.println("User with login " + login + "authorized");

                    boolean answer = getUserFromDb(login, pass);
                    writer.println(answer);
                    writer.flush();
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

                case "message": {
                    String userToSendMessage = reader.readLine();
                    String textMessage = reader.readLine();
                    boolean sendMessage = addMessageToDb(userToSendMessage, textMessage);
                    writer.println(sendMessage);
                    writer.flush();
                    System.out.println("User with username " + userToSendMessage + " received message");
                    break;
                }

            }
        }
    }

    private static boolean addMessageToDb(String userToSendMessage, String textMessage) {
        try {
            dbConnection.createStatement().execute(
                    "insert into messages (user, message) values (\"" + userToSendMessage + "\",\"" + textMessage + "\")"
            );
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

    private static boolean getUserFromDb(String login, String pass) {
        try {
            dbConnection.createStatement().executeQuery("select name, password from users where name = \"" + login + "\" and password = \"" + pass + "\"");
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
}
