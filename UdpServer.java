// Server-side

import java.net.*;
import java.io.*;

public class UdpServer implements Runnable {
    // private int timeout = 8000; // Timeout to close server socket in milliseconds
    private static int listeningPort; // UDP port on which the server will be listening
    private int bufferSize; // Server's bytes buffer size
    private byte[] buffer;
    private String tokenId;
    // Message fields
    private String recPacketId;
    private String recPacketErrorControl;
    private String recPacketOriginAlias;
    private String recPacketDestinationAlias;
    private String recPacketCrc;
    private String recPacketMessage;
    // Config file
    private String hostAlias;

    /* * * * * * * * * *
     * Public methods * 
     * * * * * * * * * */

    public UdpServer(String hostAlias, int bufferSize, int serverPort, String tokenId) {
        this.hostAlias = hostAlias;
        this.bufferSize = bufferSize;
        listeningPort = serverPort;
        this.tokenId = tokenId;
        buffer = new byte[bufferSize];
    }

    @Override
    public void run() {
        try {
            DatagramSocket serverSocket = new DatagramSocket(listeningPort);
            // serverSocket.setSoTimeout(timeout); // Timeout to close server socket

            while (!serverSocket.isClosed()) {
                // Wait for client
                DatagramPacket receivedPacket = new DatagramPacket(buffer, bufferSize);
                serverSocket.receive(receivedPacket);
                if (receivedPacket.getAddress() != null) {
                    // Client has sent a packet to server
                    inspectPacket(receivedPacket);
                }
            }
            serverSocket.close(); // Make sure it's closed
        }
        catch (SocketTimeoutException e) {
            System.out.println("\n----------------------------");
            System.out.println("| Server socket timed out! |");
            System.out.println("----------------------------");
        }
        catch(UnknownHostException e) {
            e.printStackTrace();
        }
        catch(IOException e) {
            e.printStackTrace();
        }
    }

    /* * * * * * * * * *
     * Private methods *
     * * * * * * * * * */

    private void inspectPacket(DatagramPacket receivedPacket) throws IOException {
        // Read message from client
        BufferedReader fromClient = new BufferedReader(
            new InputStreamReader(
                new ByteArrayInputStream(receivedPacket.getData())
            )
        );
        String message = fromClient.readLine();
        readMessage(message);

        // Packet contains token
        if (Helpers.isMessageToken(message)) {
            System.out.println("* Token has been received! *");
            UdpClient.setHasToken(true);
            UdpClient.releaseSemaphore();
            return;
        }

        // Packet contains message and its origin is me
        if (Helpers.isMessageFromMe(message, hostAlias)) {
            if (recPacketErrorControl.equals("ACK")) {
                System.out.println("* Message was received successfully by " + recPacketDestinationAlias + "!");
                UdpClient.releaseSemaphore();
            }
            else if (recPacketErrorControl.equals("NAK")) {
                System.out.println("Message has been received with errors by " + recPacketDestinationAlias);
                message = getMessageWithErrorControlUpdated("NAK");
                System.out.println("Sending message again...");
                UdpClient.sendMessage(message, recPacketDestinationAlias);
            }
            else { // maquinanaoexiste
                System.out.println("* Couldn't contact " + recPacketDestinationAlias + "! *");
                UdpClient.releaseSemaphore();
            }
        }
        // Packet contains message and its destination is me
        else if (Helpers.isMessageForMe(message, hostAlias)) {
            if (doesMessageHaveErrors()) {
                System.out.println("Message received from " + recPacketOriginAlias + " with errors");
                message = getMessageWithErrorControlUpdated("NAK");
                System.out.println("Requesting message to be sent again from " + recPacketOriginAlias);
                UdpClient.sendMessage(message, recPacketOriginAlias);
            }
            else {
                System.out.println("* New message from " + recPacketOriginAlias + "! The following message has been received: *");
                System.out.println("-----------------------------------------------------");
                System.out.println(recPacketMessage);
                System.out.println("-----------------------------------------------------");
                System.out.println("* Notifying " + recPacketOriginAlias + " that the message was received successfully... *\n");
                message = getMessageWithErrorControlUpdated("ACK");
                UdpClient.sendMessage(message, recPacketOriginAlias);
            }
        }
        // Packet contains message and it's not for me
        else {
            UdpClient.sendMessage(message, recPacketDestinationAlias);
        }
    }

    private void readMessage(String message) {
        String[] fields = message.split(";");

        if (fields.length == 1) { // Is token
            recPacketId = fields[0];
        }
        else {
            recPacketId = fields[0];
            fields = fields[1].split(":");
            recPacketErrorControl = fields[0];
            recPacketOriginAlias = fields[1];
            recPacketDestinationAlias = fields[2];
            recPacketCrc = fields[3];
            recPacketMessage = fields[4];
        }
    }

    private boolean doesMessageHaveErrors() {
        // TODO: Check CRC
        return !recPacketCrc.equals("1234");
    }

    private String getMessageWithErrorControlUpdated(String errorControl) {
        String message = recPacketId + ";";
        message += errorControl + ":";
        message += recPacketOriginAlias + ":";
        message += recPacketDestinationAlias + ":";
        message += recPacketCrc + ":";
        message += recPacketMessage;
        return message;
    }
}
