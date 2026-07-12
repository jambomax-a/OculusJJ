package com.oculus.jj.repository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Simple SQLite connection manager for Horos persistence.
 */
public class SqliteService {
    private static Connection connection = null;
    private static String currentDbPath = "horos_metadata.db";

    public static synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            String url = "jdbc:sqlite:" + currentDbPath;
            connection = DriverManager.getConnection(url);
        }
        return connection;
    }

    public static void setDatabasePath(String path) {
        currentDbPath = path;
    }

    /**
     * Initializes the database by creating necessary tables.
     */
    public static void initializeDatabase() {
        try (Connection conn = getConnection()) {
            java.sql.Statement stmt = conn.createStatement();
            // Create Study table with comprehensive fields
            stmt.execute("CREATE TABLE IF NOT EXISTS studies (" +
                    "studyInstanceUID TEXT PRIMARY KEY, " +
                    "patientUID TEXT, " +
                    "patientName TEXT, " +
                    "patientID TEXT, " +
                    "accessionNumber TEXT, " +
                    "studyDate TIMESTAMP, " +
                    "dateAdded TIMESTAMP, " +
                    "modality TEXT, " +
                    "numberOfImages INTEGER, " +
                    "studyName TEXT, " +
                    "institutionName TEXT, " +
                    "performingPhysician TEXT, " +
                    "referringPhysician TEXT, " +
                    "reportURL TEXT)");

            // Create Series table with comprehensive fields
            stmt.execute("CREATE TABLE IF NOT EXISTS series (" +
                    "seriesInstanceUID TEXT PRIMARY KEY, " +
                    "studyInstanceUID TEXT, " +
                    "seriesDescription TEXT, " +
                    "modality TEXT, " +
                    "numberOfImages INTEGER, " +
                    "seriesNumber INTEGER, " +
                    "seriesName TEXT");

            // Create Image table with comprehensive fields
            stmt.execute("CREATE TABLE IF NOT EXISTS images (" +
                    "sopInstanceUID TEXT PRIMARY KEY, " +
                    "seriesInstanceUID TEXT, " +
                    "filePath TEXT, " +
                    "instanceNumber INTEGER, " +
                    "sliceLocation REAL, " +
                    "imagePositionPatient TEXT)");
            conn.commit();
        } catch (SQLException e) {
            // エラーはログに記録するか、適切にエラーハンドリングを行う（コンソールへの直接出力は避ける）
        }
    }
}
