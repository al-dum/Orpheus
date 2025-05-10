import java.sql.*;

public class TestDB {
    public static void main(String[] args) {
        try (Connection conn = DriverManager.getConnection(
                "jdbc:postgresql://localhost:5432/spotify_auth?currentSchema=public",
                "postgres",
                "")) {
            System.out.println("✅ Conexión exitosa a PostgreSQL");

            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(
                    "SELECT table_schema, table_name FROM information_schema.tables WHERE table_schema = 'public'"
            );
            while (rs.next()) {
                System.out.println(rs.getString("table_schema") + "." + rs.getString("table_name"));
            }


        } catch (SQLException e) {
            e.printStackTrace();
        }

    }
}