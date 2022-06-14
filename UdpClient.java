// Client-side

import java.io.*;
import java.net.*;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.Semaphore;

public class UdpClient implements Runnable {
    private final String messageId = "2222";
    private final double errorInsertionRate = 0.4;
    private final boolean insertErrors = true;
    private static String tokenId;
    private static String messageToSend;
    private static String previousMessage;
    private static String destinationToSend;
    private static Semaphore semaphore;
    private Scanner in;
    // Config file
    private static String nextHostIp;
    private static int serverPort; // UDP port on which the server on next host will be listening
    private static String hostAlias;
    private static boolean hasToken;
    private static int nMessagesToSendWhileWithToken;
    private int nMessagesSentWhileWithToken = 0;
    // Client config and data
    private static byte[] buffer;
    private static Deque<MessageData> messageQueue;
    private static int bufferSize; // Client's bytes buffer size
    private int queueMaxSize = 10;
    
    public UdpClient(String nextHostIp, int serverPort, String hostAlias, int nMessagesToSendWhileWithToken, boolean hasToken, int bufferSize, String tokenId) {
        UdpClient.nextHostIp = nextHostIp;
        UdpClient.serverPort = serverPort;
        UdpClient.hostAlias = hostAlias;
        UdpClient.nMessagesToSendWhileWithToken = nMessagesToSendWhileWithToken;
        UdpClient.hasToken = hasToken;
        UdpClient.bufferSize = bufferSize;
        UdpClient.tokenId = tokenId;
        messageQueue = new LinkedList<MessageData>();
        buffer = new byte[bufferSize];
        in = new Scanner(System.in);
        semaphore = new Semaphore(0);
    }

    /* * * * * * * * * *
     * Public methods * 
     * * * * * * * * * */

    public static void removeMessageFromQueue() {
        messageQueue.pop();
    }

    public static void setHasToken(boolean isToken) {
        hasToken = isToken;
    }

    public static boolean getHasToken() {
        return hasToken;
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
            try {
                Thread.sleep(20);
                getUserInput();

                if (hasToken) {
                    if (hasMessageToSend() && canSendMoreMessages()) {
                        MessageData message = getMessageOnQueue();
                        
                        if (previousMessage != null && previousMessage.equals(message.message)) { // Make sure there's only one retransmission
                            System.out.println("* The transmission of the last message had failed. Sending it again one last time! *\n");
                            removeMessageFromQueue();
                        }
                        previousMessage = message.message;
                        messageToSend = formatMessage(message);
                        destinationToSend = message.destinationAlias;
                        sendPacket();
                        nMessagesSentWhileWithToken++;
                    }
                    else {
                        messageToSend = tokenId;
                        setHasToken(false);
                        nMessagesSentWhileWithToken = 0;
                        sendPacket();
                    }
                }
                semaphore.acquire();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static void resetPreviousMessage() {
        previousMessage = "";
    }

    /* * * * * * * * * *
     * Private methods *
     * * * * * * * * * */

    private static void sendPacket() {
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
                System.out.println("* Forwarded received packet to " + nextHostIp + ":" + serverPort + "! *\n");
            else if (errorControl.equals("maquinanaoexiste"))
                System.out.println("* Message to " + destinationToSend + " sent through " + nextHostIp + ":" + serverPort + "! *\n");
            
            // Close resources
            bytesInputStream.close();
            clientSocket.close();
        }
        catch(PortUnreachableException e) {
            System.err.println("\n**************************");
            System.err.println("* Server not available! *");
            System.err.println("**************************");
        }
        catch(UnknownHostException e) {
            e.printStackTrace();
        }
        catch(IOException e) {
            e.printStackTrace();
        }
    }

    private String insertErrorsInMessage(String message) {
        Random rnd = new Random();

        if (rnd.nextDouble() < errorInsertionRate) {
            StringBuilder messageWithError = new StringBuilder(message);
            for (int i = 0; i < messageWithError.length(); i++) {
                if (rnd.nextDouble() < 0.5) {
                    char c = (char)(rnd.nextInt(93) + '!');
                    messageWithError.setCharAt(i, c);
                }
            }
            return messageWithError.toString();
        }

        return message;
    }

    private boolean canSendMoreMessages() {
        return nMessagesToSendWhileWithToken - nMessagesSentWhileWithToken != 0;
    }

    private String formatMessage(MessageData messageToBeSent) {
        String message = messageId + ";";
        message += "maquinanaoexiste" + ":";
        message += hostAlias + ":"; // Origin alias
        message += messageToBeSent.destinationAlias + ":";
        message += Helpers.getCrcValue(messageToBeSent.message) + ":";

        if (insertErrors) {
            String messageWithErrors = insertErrorsInMessage(messageToBeSent.message);
            message += messageWithErrors;
        }
        else
            message += messageToBeSent.message;

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
                System.out.println("*** Message queue has reached its max limit! ***\n");
                break;
            }
            else if (messageQueue.size() == 0)
                System.out.println("> Would you like to add a message to the queue? [y/n]");
            else
                System.out.println("> Would you like to add another message to the queue? [y/n]");
            
            String input = in.nextLine();
            
            if (input.equals("n"))
                break;
            else if (input.equals("y"))
                System.out.println();
            else if (!input.equals("y")) {
                System.out.println();
                System.out.println("*** Please, answer with \"y\" or \"n\". ***\n");
                continue;
            }

            System.out.println("> Which host would you like to send your message? Type its alias.");
            String destinationAlias = in.nextLine();
            System.out.println();
            System.out.println("> What would you like to say to " + destinationAlias + "?");
            String message = in.nextLine();
            System.out.println();

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
