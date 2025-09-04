package com.jbdc.db;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class StudentRepo {

    public void ensureTable() throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS sinhvien(
              ma  VARCHAR(10) PRIMARY KEY,
              ten VARCHAR(100) NOT NULL,
              sdt VARCHAR(15) NOT NULL
            )
            """;
        try (Connection c = Db.getConnection(); Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    public void seedIfEmpty() throws SQLException {
        try (Connection c = Db.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM sinhvien")) {
            rs.next();
            if (rs.getInt(1) == 0) {
                insert(new Student("SV001", "Nguyen Van A", "0901234567"));
                insert(new Student("SV002", "Tran Thi B",   "0912345678"));
                insert(new Student("SV003", "Le Van C",      "0923456789"));
            }
        }
    }

    public boolean insert(Student s) throws SQLException {
        String sql = "INSERT INTO sinhvien(ma,ten,sdt) VALUES(?,?,?)";
        try (Connection c = Db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, s.ma);
            ps.setString(2, s.ten);
            ps.setString(3, s.sdt);
            return ps.executeUpdate() == 1;
        }
    }

    public Student getByMa(String ma) throws SQLException {
        String sql = "SELECT ma,ten,sdt FROM sinhvien WHERE ma=?";
        try (Connection c = Db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ma);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return new Student(rs.getString(1), rs.getString(2), rs.getString(3));
                return null;
            }
        }
    }

    public List<Student> getAll() throws SQLException {
        String sql = "SELECT ma,ten,sdt FROM sinhvien ORDER BY ma";
        List<Student> list = new ArrayList<>();
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(new Student(rs.getString(1), rs.getString(2), rs.getString(3)));
        }
        return list;
    }

    public boolean deleteByMa(String ma) throws SQLException {
        String sql = "DELETE FROM sinhvien WHERE ma=?";
        try (Connection c = Db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ma);
            return ps.executeUpdate() == 1;
        }
    }
}
