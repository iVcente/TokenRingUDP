// Server-side

import java.net.*;
import java.io.*;

public class UdpServer implements Runnable {
    // private final int timeout = 8000; // Timeout to close server socket in milliseconds
    private int bufferSize; // Server's bytes buffer size
    private byte[] buffer;
    // Message fields
    private String recPacketId;
    private String recPacketErrorControl;
    private String recPacketOriginAlias;
    private String recPacketDestinationAlias;
    private String recPacketCrc;
    private String recPacketMessage;
    // Config file
    private String hostAlias;
    private static int listeningPort; // UDP port on which the server will be listening

    /* * * * * * * * * *
     * Public methods * 
     * * * * * * * * * */

    public UdpServer(String hostAlias, int bufferSize, int serverPort, String tokenId) {
        this.hostAlias = hostAlias;
        this.bufferSize = bufferSize;
        listeningPort = serverPort;
        buffer = new byte[bufferSize];
    }

    @Override
    public void run() {
        System.out.println("|===== Listening on port " + listeningPort + "... =====|\n");
        try {
            DatagramSocket serverSocket = new DatagramSocket(listeningPort);
            // serverSocket.setSoTimeout(timeout); // Timeout to close server socket

            while (!serverSocket.isClosed()) {
                // Wait for client
                DatagramPacket receivedPacket = new DatagramPacket(buffer, bufferSize);
                serverSocket.receive(receivedPacket);
                if (receivedPacket.getAddress() != null) {
                    // Client has sent a packet to server
                    Thread.sleep(50);
                    inspectPacket(receivedPacket);
                }
            }
            serverSocket.close(); // Make sure it's closed
        }
        catch(SocketTimeoutException e) {
            System.out.println("\n****************************");
            System.out.println("* Server socket timed out! *");
            System.out.println("****************************");
        }
        catch(UnknownHostException e) {
            e.printStackTrace();
        }
        catch(IOException e) {
            e.printStackTrace();
        } 
        catch(InterruptedException e) {
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
            if (UdpClient.getHasToken()) {
                System.out.println("\n******************************************************");
                System.out.println("* ERROR: There's more than one token on the network! *");
                System.out.println("******************************************************");
                System.exit(1);
            }
            System.out.println("* Token has been received! *\n");
            UdpClient.setHasToken(true);
            UdpClient.releaseSemaphore();
            return;
        }

        // Packet contains message and its origin is me
        if (Helpers.isMessageFromMe(message, hostAlias)) {
            if (recPacketErrorControl.equals("ACK")) {
                System.out.println("* Message was received successfully by " + recPacketDestinationAlias + "!\n");
                UdpClient.resetPreviousMessage();
                UdpClient.removeMessageFromQueue();
            }
            else if (recPacketErrorControl.equals("NAK")) {
                System.out.println("* Message has been received with error(s) by " + recPacketDestinationAlias + "! *\n");
            }
            else if (recPacketDestinationAlias.equals(hostAlias)) {
                System.out.println("* Message sent to myself! *\n");
                UdpClient.removeMessageFromQueue();
            }
            else { // maquinanaoexiste
                System.out.println("* Couldn't contact " + recPacketDestinationAlias + "! *\n");
                UdpClient.removeMessageFromQueue();
            }
            UdpClient.releaseSemaphore();
        }
        // Packet contains message and its destination is me
        else if (Helpers.isMessageForMe(message, hostAlias)) {
            if (doesMessageHaveErrors()) {
                System.out.println("* Message received from " + recPacketOriginAlias + " with errors: *");
                Helpers.prettyPrintMessage(recPacketMessage, true);
                message = getMessageWithErrorControlUpdated("NAK");
                System.out.println("* Requesting message to be sent again from " + recPacketOriginAlias + " *\n");
                UdpClient.sendMessage(message, recPacketOriginAlias);
            }
            else {
                System.out.println("* New message from " + recPacketOriginAlias + "! The following message has been received: *");
                Helpers.prettyPrintMessage(recPacketMessage, false);
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
            recPacketMessage = fields[4].trim();
        }
    }

    private boolean doesMessageHaveErrors() {
        return Helpers.getCrcValue(recPacketMessage) != Long.parseLong(recPacketCrc);
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
