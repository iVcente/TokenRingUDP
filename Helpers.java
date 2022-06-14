import java.util.zip.CRC32;

public class Helpers {
    public static boolean isMessageToken(String message) {
        return message.split(";").length == 1;
    }

    public static boolean isMessageFromMe(String message, String hostAlias) {
        if (isMessageToken(message))
            return false;
        return message.split(";")[1].split(":")[1].equals(hostAlias);
    }

    public static boolean isMessageForMe(String message, String hostAlias) {
        if (isMessageToken(message))
            return false;
        return message.split(";")[1].split(":")[2].equals(hostAlias);
    }

    public static boolean isMessageBroadcast(String message) {
        return message.split(";")[1].split(":")[2].equalsIgnoreCase("Todos");
    }

    public static String getErrorControlInMessage(String message) {
        if (isMessageToken(message))
            return "-1";
        return message.split(";")[1].split(":")[0];
    }

    public static long getCrcValue(String message) {
        CRC32 crc = new CRC32();
        crc.update(message.getBytes());
        return crc.getValue();
    }

    public static void prettyPrintMessage(String message, boolean msgWithErrors) {
        char headerChar = msgWithErrors ? '*' : '-';
        char auxChar = msgWithErrors ? '*' : '|';
        StringBuilder header = new StringBuilder(message.length());

        for (int i = 0; i < message.length() + 4; i++)
            header.append(headerChar);

        System.out.println("\t" + header.toString());
        System.out.println("\t" + auxChar + " " + message + " " + auxChar);
        System.out.println("\t" + header.toString());
    }
}
