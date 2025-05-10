import java.sql.Connection;

public class TestConection {
    public static void main(String[] args) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            if (conn != null) {
                System.out.println("Conexión exitosa a la base de datos!");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}