package se.fk.data.modell.json;

import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.Locale;

public class DigestUtils {
    private static final Logger log = LoggerFactory.getLogger(DigestUtils.class);

    private static final MessageDigest sha256;

    static {
        try {
            sha256 = MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static byte[] computeDigest(
            Object bean,
            ObjectMapper mapper
    ) {
        byte[] json = mapper.writeValueAsBytes(bean);
        return sha256.digest(json);
    }


    private static final char[] HEX = "0123456789abcdef".toCharArray();
    // Light→dark ramp for ASCII bar
    private static final char[] RAMP = " .:-=+*#%@".toCharArray();

    /** Hex string, grouped every {@code group} bytes (2*group hex chars) with {@code sep} between groups. */
    public static String hexGrouped(byte[] digest, int group, String sep, boolean upperCase) {
        if (digest == null) {
            return "";
        }

        if (group <= 0) group = 2;
        StringBuilder sb = new StringBuilder(digest.length * 2 + digest.length / group);
        int n = 0;
        for (byte b : digest) {
            int v = b & 0xFF;
            char hi = HEX[v >>> 4];
            char lo = HEX[v & 0x0F];
            if (upperCase) {
                hi = Character.toUpperCase(hi);
                lo = Character.toUpperCase(lo);
            }
            sb.append(hi).append(lo);
            n++;
            if (n % group == 0 && n < digest.length) sb.append(sep);
        }
        return sb.toString();
    }

    /** ASCII bar using a density ramp (one char per byte). */
    public static String asciiBar(byte[] digest) {
        if (digest == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder(digest.length);
        for (byte b : digest) {
            int v = b & 0xFF;
            int idx = (int) Math.round((v / 255.0) * (RAMP.length - 1));
            sb.append(RAMP[idx]);
        }
        return sb.toString();
    }

    /** 5×5 mirrored identicon; uses ANSI color if enabled. Each “pixel” is two block chars wide. */
    public static String identicon5x5(byte[] digest, boolean ansiColor) {
        if (digest == null) {
            return "";
        }

        // Foreground color from first 3 bytes
        int r = digest[0] & 0xFF, g = digest[1] & 0xFF, b = digest[2] & 0xFF;
        String fg = ansiColor ? bgTruecolor(r, g, b) : "";
        String reset = ansiColor ? "\u001B[0m" : "";

        boolean[][] grid = new boolean[5][5];
        // Use 15 bits (3 bytes) to fill left 3 columns, mirror into right 2
        int bitCursor = 0;
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 3; col++) {
                int byteIdx = 3 + (bitCursor >> 3);
                int bitIdx  = bitCursor & 7;
                boolean on = ((digest[byteIdx] >> bitIdx) & 1) == 1;
                grid[row][col] = on;
                grid[row][4 - col] = on; // mirror
                bitCursor++;
            }
        }

        StringBuilder out = new StringBuilder();
        String onCell  = fg + "██" + reset;
        String offCell = ansiColor ? "\u001B[48;2;255;255;255m  \u001B[0m" : "  ";
        for (int rIdx = 0; rIdx < 5; rIdx++) {
            for (int cIdx = 0; cIdx < 5; cIdx++) {
                out.append(grid[rIdx][cIdx] ? onCell : offCell);
            }
            out.append('\n');
        }
        return out.toString();
    }

    /** Color “swatch” grid (4×8) from the 32 digest bytes. Nice quick-look fingerprint. */
    public static String colorGrid(byte[] digest) {
        if (digest == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        // consume bytes in triples; if not enough, wrap
        int i = 0;
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 8; col++) {
                int r = digest[i % digest.length] & 0xFF; i++;
                int g = digest[i % digest.length] & 0xFF; i++;
                int b = digest[i % digest.length] & 0xFF; i++;
                sb.append(bgTruecolor(r, g, b)).append("  ").append("\u001B[0m");
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /** Truecolor ANSI background escape */
    private static String bgTruecolor(int r, int g, int b) {
        return String.format(Locale.ROOT, "\u001B[48;2;%d;%d;%dm", r, g, b);
    }

    /** Is ANSI enabled (very simple heuristic). */
    public static boolean ansiEnabled() {
        String term = System.getenv("TERM");
        return System.console() != null && term != null && !term.equalsIgnoreCase("dumb");
    }
}
