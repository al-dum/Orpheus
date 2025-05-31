import org.mindrot.jbcrypt.BCrypt;

public class PasswordUtil {
    /**
     * Genera un hash seguro de una contraseña utilizando el algoritmo BCrypt.
     *
     * @param password La contraseña en texto plano a hashear.
     * @return El hash de la contraseña generado.
     */
    public static String hashPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt());
    }

    /**
     * Verifica si una contraseña en texto plano coincide con su hash almacenado.
     *
     * @param password La contraseña en texto plano a verificar.
     * @param hashed   El hash de la contraseña almacenado.
     * @return true si la contraseña coincide con el hash, false en caso contrario o si ocurre un error.
     */
    public static boolean checkPassword(String password, String hashed) {
        try {
            return BCrypt.checkpw(password, hashed);
        } catch (Exception e) {
            return false;
        }
    }
}