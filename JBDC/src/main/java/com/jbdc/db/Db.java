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
    String host = System.getenv().getOrDefault("DB_HOST", "103.221.222.68");
    String port = System.getenv().getOrDefault("DB_PORT", "3306");
    String db = System.getenv().getOrDefault("DB_NAME", "tqishhwy_zalo");
    String user = System.getenv().getOrDefault("DB_USER", "tqishhwy_zalo");
    String pass = System.getenv().getOrDefault("DB_PASS", "tqishhwy_zalo");

    String url =
        "jdbc:mysql://" + host + ":" + port + "/" + db +
        "?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true";

    return DriverManager.getConnection(url, user, pass);
  }
}
