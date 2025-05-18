import java.sql.SQLException;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;

/**
 * Sistema de votación para reseñas musicales.
 * Maneja votos "útil/no útil" y actualiza la interfaz.
 */
public class ReviewVoteSystem {
    private final VoteUpdateListener updateListener;

    /**
     * Interfaz para notificar actualizaciones de votos
     */
    public interface VoteUpdateListener {
        void onVoteSuccess();
        void onVoteFailure(String errorMessage);
    }

    /**
     * Constructor con dependencia inyectada
     * @param updateListener Listener para manejar actualizaciones de UI
     */
    public ReviewVoteSystem(VoteUpdateListener updateListener) {
        this.updateListener = updateListener;
    }

   /**
    * Procesa un voto para una reseña, utilizando solo el ID del usuario de reseñas
    * @param reviewId ID de la reseña
    * @param spotifyUserId Ignorado, se mantiene por compatibilidad
    * @param reviewUserId ID del usuario de reseñas que está votando
    * @param isUseful true si el voto es "útil", false si es "no útil"
    */
   public void handleVote(int reviewId, Integer spotifyUserId, Integer reviewUserId, boolean isUseful) {
       // Solo procesar si hay un usuario de reseñas válido
       if (reviewUserId == null) {
           if (updateListener != null) {
               updateListener.onVoteFailure("Debes iniciar sesión como usuario de reseñas para votar");
           }
           Platform.runLater(() -> showAlert("Error", "Debes iniciar sesión como usuario de reseñas para votar"));
           return;
       }

       new Thread(() -> {
           try {
               if (ReviewManager.alreadyVoted(reviewId, null, reviewUserId)) {
                   Platform.runLater(() ->
                           showAlert("Voto duplicado", "Ya has votado esta reseña"));
                   if (updateListener != null) {
                       updateListener.onVoteFailure("Ya has votado esta reseña");
                   }
                   return;
               }

               ReviewManager.voteReview(reviewId, null, reviewUserId, isUseful);

               Platform.runLater(() -> {
                   if (updateListener != null) {
                       updateListener.onVoteSuccess();
                   }
               });

           } catch (SQLException e) {
               Platform.runLater(() -> {
                   if (updateListener != null) {
                       updateListener.onVoteFailure(e.getMessage());
                   }
                   showAlert("Error", "No se pudo registrar el voto: " + e.getMessage());
               });
           }
       }).start();
   }

    /**
     * Muestra una alerta al usuario
     * @param title Título de la alerta
     * @param message Mensaje a mostrar
     */
    private void showAlert(String title, String message) {
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}