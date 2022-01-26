package cn.net.scp.nio.transform;

import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SplitStringForwarder extends AbstractForwarder<String, String> {

    static final Logger logger = LogManager.getLogger();

    private String buffer;
    private String delimiter;
    private int delimiterLength;

    /**
     * creates a new SplitStringForwarder
     * @param delimiter the delimiter for splitting strings
     */
    public SplitStringForwarder(String delimiter) {
        setDelimiterPrivate(delimiter);
    }

    @Override
    public synchronized void forward(String input) throws IOException {
        if (nextForwarder == null) {
            logger.error("nextForwarder == null -> data lost");

        } else {
            if ((buffer != null) && (buffer.length() > 0)) {
                splitString(buffer + input);
            } else {
                splitString(input);
            }
        }
    }

    /**
     * sets the delimiter
     * @param delimiter the delimiter for splitting strings
     */
    public synchronized void setDelimiter(String delimiter) {
        setDelimiterPrivate(delimiter);
    }

    private void setDelimiterPrivate(String delimiter) {
        this.delimiter = delimiter;
        delimiterLength = delimiter.length();
    }

    private void splitString(String string) throws IOException {
        int offset = 0;
        for (int delimiterIndex = string.indexOf(delimiter, offset); delimiterIndex != -1; delimiterIndex = string.indexOf(delimiter, offset)) {
            String token = string.substring(offset, delimiterIndex);
            offset = delimiterIndex + delimiterLength;
            nextForwarder.forward(token);
        }
        buffer = string.substring(offset);
    }

}
