package com.jbdc.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Db {
  static {
    try {
      Class.forName("com.mysql.cj.jdbc.Driver");
    } catch (ClassNotFoundException e) {
      throw new RuntimeException("MySQL driver not found", e);
    }
  }

  public static Connection getConnection() throws SQLException {
    String host = System.getenv().getOrDefault("DB_HOST");
    String port = System.getenv().getOrDefault("DB_PORT");
    String db = System.getenv().getOrDefault("DB_NAME");
    String user = System.getenv().getOrDefault("DB_USER");
    String pass = System.getenv().getOrDefault("DB_PASS");

    String url =
        "jdbc:mysql://" + host + ":" + port + "/" + db +
        "?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true";

    return DriverManager.getConnection(url, user, pass);
  }
}
