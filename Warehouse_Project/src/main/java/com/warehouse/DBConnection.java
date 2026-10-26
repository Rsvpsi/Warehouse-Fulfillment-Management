package com.warehouse;

import java.sql.Connection;
import java.sql.DriverManager;

public class DBConnection {

    public static Connection getConnection() {

        try {

            Class.forName("com.mysql.cj.jdbc.Driver");

            Connection connection = DriverManager.getConnection(
                    System.getenv().getOrDefault(
                            "WAREHOUSE_DB_URL",
                            "jdbc:mysql://localhost:3306/warehouse_db"
                    ),
                    System.getenv().getOrDefault("WAREHOUSE_DB_USER", "root"),
                    System.getenv().getOrDefault("WAREHOUSE_DB_PASSWORD", "")
            );

            System.out.println("DATABASE CONNECTION SUCCESSFUL!");

            return connection;

        } catch (Exception e) {

            System.out.println("DATABASE CONNECTION FAILED!");

            e.printStackTrace();

            throw new RuntimeException(
                    "Database connection failed: " + e.getMessage(),
                    e
            );
        }
    }
}