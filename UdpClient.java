// Client-side

import java.io.*;
import java.net.*;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Scanner;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class UdpClient implements Runnable {
    private static String tokenId;
    private static boolean hasToken;
    private static String messageToSend;
    private static String destinationToSend;
    private static Semaphore semaphore;
    private String messageId = "2222";
    private Scanner in;
    // Config file
    private static String nextHostIp;
    private static int serverPort; // UDP port on which the server on next host will be listening
    private static String hostAlias;
    private int nMessagesToSendWhileWithToken;
    private int nMessagesSentWhileWithToken;
    // Client config and data
    private static byte[] buffer;
    private static Queue<MessageData> messageQueue;
    private static int bufferSize; // Client's bytes buffer size
    private int queueMaxSize = 10;
    
    public UdpClient(String nextHostIp, String hostAlias, boolean hasToken, int bufferSize, int serverPort, String tokenId) {
        UdpClient.nextHostIp = nextHostIp;
        UdpClient.hostAlias = hostAlias;
        UdpClient.hasToken = hasToken;
        UdpClient.bufferSize = bufferSize;
        UdpClient.serverPort = serverPort;
        UdpClient.tokenId = tokenId;
        messageQueue = new LinkedList<MessageData>();
        buffer = new byte[bufferSize];
        in = new Scanner(System.in);
        semaphore = new Semaphore(0);
    }

    /* * * * * * * * * *
     * Public methods * 
     * * * * * * * * * */

    public static void setHasToken(boolean isToken) {
        hasToken = isToken;
    }

    public static void releaseSemaphore() {
        semaphore.release();
    }

    public static void sendMessage(String message, String destinationAlias) {
        messageToSend = message;
        destinationToSend = destinationAlias;
        sendPacket();
    }

    @Override
    public void run() {
        while (true) {
            getUserInput();

            if (hasToken) {
                if (hasMessageToSend()) {
                    MessageData message = getMessageOnQueue();
                    messageToSend = formatMessage(message);
                    destinationToSend = message.destinationAlias;
                    sendPacket();
                }
                else {
                    messageToSend = tokenId;
                    setHasToken(false);
                    sendPacket();
                }
            }

            try {
                semaphore.acquire();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            messageQueue.poll();
        }
    }

    /* * * * * * * * * *
     * Private methods *
     * * * * * * * * * */

    private static void sendPacket() {
        // if (!hasToken)
        //     return;
        
        // if (!hasMessageToSend()) {
        //     if (hasToken)
        //         messageToSend = tokenId;
        //     else
        //         return;
        // }
        
        try {
            DatagramSocket clientSocket = new DatagramSocket();
            // Make sure packets can only be sent to this address.
            // Not really a way to establish a "connection" the same way TCP does, 
            // but a way to prevent sending or receiving packets to/from other addresses.
            // UDP is connectionless
            clientSocket.connect(InetAddress.getByName(nextHostIp), serverPort); // Can throw PortUnreachableException exception
            
            // Send packet
            byte[] messageToBeSentInBytes = messageToSend.getBytes();
            InputStream bytesInputStream = new ByteArrayInputStream(messageToBeSentInBytes);
            
            while (bytesInputStream.read(buffer) > 0) { // Bytes to send greater than 0
                DatagramPacket packet = new DatagramPacket(buffer, bufferSize);
                clientSocket.send(packet);
                buffer = new byte[bufferSize]; // Clear the buffer
            }

            String errorControl = Helpers.getErrorControlInMessage(messageToSend);

            if (Helpers.isMessageToken(messageToSend))
                System.out.println("* Token sent to " + nextHostIp + "! *\n");
            else if (!Helpers.isMessageForMe(messageToSend, hostAlias) && !Helpers.isMessageFromMe(messageToSend, hostAlias))
                System.out.println("* Forwarded received packet to " + nextHostIp + ":" + serverPort + "! *");
            else if (errorControl.equals("maquinanaoexiste"))
                System.out.println("* Message to " + destinationToSend + " sent through " + nextHostIp + ":" + serverPort + "! *");
            
            // Close resources
            bytesInputStream.close();
            clientSocket.close();
        }
        catch(PortUnreachableException e) {
            System.err.println("\n--------------------------");
            System.err.println("| Server not available! |");
            System.err.println("--------------------------");
        }
        catch(UnknownHostException e) {
            e.printStackTrace();
        }
        catch(IOException e) {
            e.printStackTrace();
        }
    }

    private String formatMessage(MessageData messageToBeSent) {
        // if (nMessagesToSendWhileWithToken - nMessagesSentWhileWithToken == 0) {
        //     nMessagesSentWhileWithToken = 0;
        //     return tokenId;
        // }

        String message = messageId + ";";
        message += "maquinanaoexiste" + ":";
        message += hostAlias + ":"; // Origin alias
        message += messageToBeSent.destinationAlias + ":";
        message += "1234" + ":"; // CRC
        message += messageToBeSent.message;

        // nMessagesSentWhileWithToken++;

        return message;
    }
    
    private MessageData getMessageOnQueue() {
        return messageQueue.peek();
    }
    
    private static boolean hasMessageToSend() {
        return messageQueue.size() > 0;
    }
    
    private boolean isQueueFull() {
        return messageQueue.size() >= queueMaxSize;
    }

    private void getUserInput() {        
        while (true) {
            if (isQueueFull()) {
                System.out.println("\n*** Message queue has reached its max limit! ***");
                break;
            }
            else if (messageQueue.size() == 0)
                System.out.println("\n> Would you like to add a message to the queue? [y/n]");
            else
                System.out.println("\n> Would you like to add another message to the  queue? [y/n]");
            
            String input = in.nextLine();
            
            if (input.equals("n"))
                break;
            if (!input.equals("y")) {
                System.out.println("*** Please, answer with \"y\" or \"n\". ***");
                continue;
            }

            System.out.println("\n> Which host would you like to send your message? Type its alias.");
            String destinationAlias = in.nextLine();
            System.out.println("\n> What would you like to say to " + destinationAlias + "?");
            String message = in.nextLine();

            MessageData messageData = new MessageData(destinationAlias, message);
            messageQueue.add(messageData);
        }
        
        System.out.println();
    }

    private class MessageData {
        public String destinationAlias;
        public String message;
    
        public MessageData(String destinationAlias, String message) {
            this.destinationAlias = destinationAlias;
            this.message = message;
        }
    }
}
