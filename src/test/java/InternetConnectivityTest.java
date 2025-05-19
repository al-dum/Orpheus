/**
 * Simple test class to verify internet connectivity detection.
 */
public class InternetConnectivityTest {
    public static void main(String[] args) {
        System.out.println("Testing internet connectivity...");
        
        boolean isConnected = SpotifyClient.isInternetAvailable();
        
        System.out.println("Internet connectivity test result: " + (isConnected ? "CONNECTED" : "NOT CONNECTED"));
        
        if (!isConnected) {
            System.out.println("Please check your internet connection and try again.");
            System.out.println("Make sure your firewall is not blocking the application.");
        } else {
            System.out.println("Internet connection is working properly.");
        }
    }
}