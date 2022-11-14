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
            Statement stat = dbConnection.createStatement();
            stat.executeUpdate("create table if not exists  users (user_id INTEGER PRIMARY KEY ," +
                    " name VARCHAR, password VARCHAR);");

        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private static void readCommands(BufferedReader reader, PrintWriter writer) throws IOException {
        while (true) {
            String command = reader.readLine();
            switch (command) {
                case "calcSum":
                    int numberA = Integer.valueOf(reader.readLine());
                    int numberB = Integer.valueOf(reader.readLine());

                    int sum = numberA + numberB;

                    writer.println(sum);
                    writer.flush();

                    break;
                case "serverTime":
                    writer.println(new Date().toString());
                    writer.flush();
                    break;
                case "register":

                    String name = reader.readLine();
                    String password = reader.readLine();

                    System.out.println("register - get name and password" + name + " -- " + password);

                    boolean result = registerInDm(name, password);
                    // TODO actual
                    writer.println(result);
                    writer.flush();

                    break;
            }
        }
    }

    private static boolean registerInDm(String name, String password) {
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
