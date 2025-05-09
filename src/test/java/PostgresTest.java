import java.sql.*;

public class PostgresTest {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://localhost:5432/spotify_auth";
        String user = "postgres"; // o tu usuario de sistema
        String password = "";     // si no configuraste una contraseña, déjalo vacío

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("✅ Conexión exitosa a PostgreSQL");
        } catch (SQLException e) {
            System.out.println("❌ Error al conectar:");
            e.printStackTrace();
        }
    }
}

