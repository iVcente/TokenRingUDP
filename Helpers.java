public class Helpers {
    public static boolean isMessageToken(String message) {
        return message.split(";").length == 1;
    }

    public static boolean isMessageForMe(String message, String hostAlias) {
        if (isMessageToken(message))
            return false;
        return message.split(";")[1].split(":")[2].equals(hostAlias);
    }

    public static boolean isMessageFromMe(String message, String hostAlias) {
        if (isMessageToken(message))
            return false;
        return message.split(";")[1].split(":")[1].equals(hostAlias);
    }

    public static String getErrorControlInMessage(String message) {
        if (isMessageToken(message))
            return "-1";
        return message.split(";")[1].split(":")[0];
    }
}
