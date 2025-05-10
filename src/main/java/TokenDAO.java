import ch.qos.logback.core.subst.Token;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class TokenDAO {
    public void guardarTokens(String accessToken, String refreshToken, int expiresIn) {
        String sql = "INSERT INTO spotify_tokens (access_token, refresh_token, expires_in) VALUES (?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, accessToken);
            stmt.setString(2, refreshToken);
            stmt.setInt(3, expiresIn);

            stmt.executeUpdate();
            System.out.println("Tokens guardados correctamente.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

//    public Token obtenerTokenMasReciente() {
//        String sql = "SELECT * FROM spotify_tokens ORDER BY created_at DESC LIMIT 1";
//
//        try (Connection conn = DatabaseConnection.getConnection();
//             PreparedStatement stmt = conn.prepareStatement(sql);
//             ResultSet rs = stmt.executeQuery()) {
//
//            if (rs.next()) {
//                Token token = new Token();
//                token.setAccessToken(rs.getString("access_token"));
//                token.setRefreshToken(rs.getString("refresh_token"));
//                token.setExpiresIn(rs.getInt("expires_in"));
//                token.setCreatedAt(rs.getTimestamp("created_at"));
//                return token;
//            }
//
//        } catch (SQLException e) {
//            e.printStackTrace();
//        }

//        return null;
    }
//}