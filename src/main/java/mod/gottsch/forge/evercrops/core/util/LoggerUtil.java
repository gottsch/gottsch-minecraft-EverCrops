package mod.gottsch.forge.evercrops.core.util;

/**
 * @author by Mark Gottschling on 3/18/2025
 */
public class LoggerUtil {

    private LoggerUtil() {}

    public static String formatLogMessage(String level, String message) {

        String[] lines = message.split("\n");

        StringBuilder formattedMessage = new StringBuilder();
        formattedMessage.append("\n")
            .append("**************************************************\n")
            .append("* ").append(level.toUpperCase()).append("\n")
            .append("**************************************************\n");

        for (String line : lines) {
            formattedMessage.append("* ").append(line).append("\n");
        }

        formattedMessage.append("**************************************************");

        return formattedMessage.toString();
    }
}
