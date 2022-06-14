import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

class Main {
    private static int bufferSize = 1024;
    private static int serverPort = 9999;
    private static String tokenId = "1111";
    // Config file
    private static String nextHostIp;
    private static String hostAlias;
    private static int nMessagesToSendWhileWithToken; // ?
    private static boolean hasToken;

    public static void main(String[] args) throws IllegalThreadStateException {
        parseConfig(args[0]);
        
        UdpClient client = new UdpClient(nextHostIp, serverPort, hostAlias, nMessagesToSendWhileWithToken, hasToken, bufferSize, tokenId);
        UdpServer server = new UdpServer(hostAlias, bufferSize, serverPort, tokenId);

        Thread clientThread = new Thread(client);
        Thread serverThread = new Thread(server);

        clientThread.start();
        serverThread.start();
    }

    private static void parseConfig(String configFilePath) {
        try (BufferedReader buffReader = new BufferedReader(new FileReader(configFilePath))){
            // Get destination IP and server listening port
            String[] splitLine = buffReader.readLine().split(":");
            nextHostIp = splitLine[0];
            serverPort = Integer.parseInt(splitLine[1]);

            // Get host alias
            hostAlias = buffReader.readLine();

            // Get how many message can be sent while in possession of token
            nMessagesToSendWhileWithToken = Integer.parseInt(buffReader.readLine());

            // Check if host has initial token
            hasToken = Boolean.parseBoolean(buffReader.readLine());

            buffReader.close();
        }
        catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }
}
