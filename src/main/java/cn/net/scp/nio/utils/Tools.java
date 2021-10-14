package cn.net.scp.nio.utils;

import java.nio.ByteBuffer;
import java.text.NumberFormat;
import java.util.ResourceBundle;
import org.apache.logging.log4j.Logger;

public class Tools {

    private static final NumberFormat numberFormat = NumberFormat.getInstance();
    private static final ResourceBundle STRINGS = ResourceBundle.getBundle("Strings");

    private Tools() {
    }

    public static ByteBuffer splitBuffer(ByteBuffer byteBuffer, int size) {
        int newPosition = byteBuffer.position() + size;
        ByteBuffer head = byteBuffer.duplicate();
        head.limit(newPosition);
        byteBuffer.position(newPosition);
        return head;
    }


    public static ByteBuffer copyBuffer(ByteBuffer input) {
        return copyBuffer(input, input.remaining());
    }


    public static ByteBuffer copyBuffer(ByteBuffer input, int size) {
        int remaining = input.remaining();
        if (remaining < size) {
            throw new IllegalArgumentException("size (" + size + ") was larger than remaining input (" + remaining + ")");
        }
        // allocate new buffer
        ByteBuffer copy = input.isDirect() ? ByteBuffer.allocateDirect(size) : ByteBuffer.allocate(size);

        // copy data from input to copy without modifying input
        // (restore position and limit after copying)
        int oldPosition = input.position();
        int oldLimit = input.limit();
        input.limit(oldPosition + size);
        copy.put(input);
        copy.flip();
        input.position(oldPosition);
        input.limit(oldLimit);

        return copy;
    }


    public static void handleStackTrace(Logger logger, Throwable throwable) {
        if (logger == null) {
            throwable.printStackTrace();
        } else {
            logger.error("handleStackTrace ", throwable);
        }
    }


    public static ByteBuffer enlargeBuffer(ByteBuffer byteBuffer, int newSize) {
        // allocate new buffer
        ByteBuffer newBuffer = byteBuffer.isDirect() ? ByteBuffer.allocateDirect(newSize) : ByteBuffer.allocate(newSize);
        // move data from old to new buffer
        byteBuffer.flip();
        newBuffer.put(byteBuffer);
        // leave newBuffer in "fill" mode
        return newBuffer;
    }

    public static ByteBuffer append(boolean direct, ByteBuffer destination, ByteBuffer... sources) {
        int remaining = 0;
        for (ByteBuffer source : sources) {
            remaining += source.remaining();
        }

        ByteBuffer returnBuffer;
        if (destination == null) {
            // create buffer
            if (direct) {
                returnBuffer = ByteBuffer.allocateDirect(remaining);
            } else {
                returnBuffer = ByteBuffer.allocate(remaining);
            }

        } else {
            // check if there is enough space left in destination
            returnBuffer = destination;
            returnBuffer.compact();
            if (returnBuffer.remaining() < remaining) {
                // we have to enlarge returnBuffer so that both the old and new
                // data fits into the new buffer
                int newCapacity = returnBuffer.position() + remaining;
                returnBuffer = enlargeBuffer(returnBuffer, newCapacity);
            }
        }

        for (ByteBuffer source : sources) {
            returnBuffer.put(source);
        }

        returnBuffer.flip();
        return returnBuffer;
    }


    public static String getBandwidthString(
        long bandwidth, int fractionDigits) {
        return getDataVolumeString(bandwidth, fractionDigits) + "/s";
    }


    public static String getDataVolumeString(long bytes, int fractionDigits) {
        if (bytes >= 1024) {
            numberFormat.setMaximumFractionDigits(fractionDigits);
            float kBytes = (float) bytes / 1024;
            if (kBytes >= 1024) {
                float mBytes = (float) bytes / 1048576;
                if (mBytes >= 1024) {
                    float gBytes = (float) bytes / 1073741824;
                    if (gBytes >= 1024) {
                        float tBytes = (float) bytes / 10995116277760f;
                        return numberFormat.format(tBytes) + " TiB";
                    }
                    return numberFormat.format(gBytes) + " GiB";
                }
                return numberFormat.format(mBytes) + " MiB";
            }
            return numberFormat.format(kBytes) + " KiB";
        }
        return numberFormat.format(bytes) + " " + STRINGS.getString("byte");
    }


    public static String toHex(ByteBuffer byteBuffer) {
        int position = byteBuffer.position();
        int remaining = byteBuffer.remaining();
        byte[] data = new byte[remaining];
        byteBuffer.get(data);
        byteBuffer.position(position);
        return toHex(data);
    }


    public static String toHex(byte[] data) {
        return toHex(data, data.length);
    }


    public static String toHex(byte[] data, int dataLength) {
        // allocate the right amount of memory
        int hexBlocks = dataLength / 16;
        if ((dataLength % 16) != 0) {
            hexBlocks++;
        }
        // 72 = 16*3 Bytes for every byte, + 7 Bytes whitespaces +
        // 16 bytes plain text + 1 byte newline
        int stringLength = hexBlocks * 72;
        StringBuilder hexString = new StringBuilder(stringLength);

        // start the dirty work
        int blockIndex = 0;
        while (blockIndex < dataLength) {
            // calculate some array pointers
            int blockLength = Math.min(16, dataLength - blockIndex);
            int stopIndex = blockIndex + blockLength;

            // print hex line
            for (int i = blockIndex; i < stopIndex; i++) {
                hexString.append(hexChar[(data[i] & 0xF0) >>> 4]);
                hexString.append(hexChar[data[i] & 0x0F]);
                hexString.append(' ');
            }

            // fill hex line if too short
            for (int i = blockLength; i < 16; i++) {
                hexString.append("   ");
            }

            // space between hex and plain
            hexString.append("       ");

            // print plain line
            for (int i = blockIndex; i < stopIndex; i++) {
                if ((data[i] > 31) && (data[i] < 127)) {
                    // ASCII goes from 32 to 126
                    hexString.append((char) data[i]);
                } else if ((data[i] > -97) && (data[i] < 0)) {
                    // ISO 8859-1 printable starts from 160 ("+96" because of
                    // signed byte values)
                    hexString.append(iso8859_1Char[data[i] + 96]);
                } else {
                    // data[i] is NOT printable, we substitute it with a "."
                    hexString.append('.');
                }
            }

            // append newline
            hexString.append('\n');

            // move index
            blockIndex += blockLength;
        }
        return hexString.toString();
    }

    // table to convert a nibble to a hex char.
    private static final char[] hexChar = {
        '0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
    };
    private static final char[] iso8859_1Char = {
        ' ', '¡', '¢', '£', '¤', '¥', '¦', '§',
        '¨', '©', 'ª', '«', '¬', '­', '®', '¯',
        '°', '±', '²', '³', '´', 'µ', '¶', '·',
        '¸', '¹', 'º', '»', '¼', '½', '¾', '¿',
        'À', 'Á', 'Â', 'Ã', 'Ä', 'Å', 'Æ', 'Ç',
        'È', 'É', 'Ê', 'Ë', 'Ì', 'Í', 'Î', 'Ï',
        'Ð', 'Ñ', 'Ò', 'Ó', 'Ô', 'Õ', 'Ö', '×',
        'Ø', 'Ù', 'Ú', 'Û', 'Ü', 'Ý', 'Þ', 'ß',
        'à', 'á', 'â', 'ã', 'ä', 'å', 'æ', 'ç',
        'è', 'é', 'ê', 'ë', 'ì', 'í', 'î', 'ï',
        'ð', 'ñ', 'ò', 'ó', 'ô', 'õ', 'ö', '÷',
        'ø', 'ù', 'ú', 'û', 'ü', 'ý', 'þ', 'ÿ'
    };
}
