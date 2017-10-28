package chaosunicorn.com.chaosuniqorn;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.UUID;

public class Helpers {

    public static String convertInputStreamToString(InputStream inputStream) throws IOException {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        String line = "";
        String result = "";
        while ((line = bufferedReader.readLine()) != null)
            result += line;

        inputStream.close();
        return result;
    }

    public static byte[] UuidToByteArray(final UUID uuid) {
        final String hex = uuid.toString().replace("-", "");
        final int length = hex.length();
        final byte[] result = new byte[length / 2];

        for (int i = 0; i < length; i += 2) {
            result[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i + 1), 16));
        }

        return result;
    }

    public static byte[] integerToByteArray(final int value) {
        final byte[] result = new byte[2];
        result[0] = (byte) (value / 256);
        result[1] = (byte) (value % 256);

        return result;
    }
}
