package com.ordanexa.fidoreader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;

public final class TokenSerialTest {
    private static int checks;
    private static void check(boolean condition) {
        checks++;
        if (!condition) throw new AssertionError("Serial check " + checks);
    }

    private static byte[] frame(int flags, int sequence, byte[] payload) {
        byte[] frame = new byte[64];
        frame[0] = 0x21;
        frame[1] = (byte) (flags | sequence);
        frame[2] = (byte) payload.length;
        System.arraycopy(payload, 0, frame, 3, payload.length);
        return frame;
    }

    private static byte[] response(String serial) {
        byte[] value = serial.getBytes(StandardCharsets.US_ASCII);
        byte[] response = new byte[value.length + 4];
        response[0] = (byte) 0xd1;
        response[1] = (byte) value.length;
        System.arraycopy(value, 0, response, 2, value.length);
        response[response.length - 2] = (byte) 0x90;
        return response;
    }

    private static final class Reports implements TokenSerial.Reports {
        final Queue<byte[]> frames = new ArrayDeque<>();
        int sends;
        Reports(byte[]... values) { frames.addAll(Arrays.asList(values)); }
        public void send(byte[] report) {
            // Independently transcribed from the vendor's worked USB example.
            int[] prefix = {0x21, 0, 25, 0x80, 0x33, 0, 0, 0, 0, 0x12, 0xd1, 0x10};
            check(++sends == 1);
            check(report.length == 64);
            for (int i = 0; i < prefix.length; i++) check((report[i] & 255) == prefix[i]);
            for (int i = prefix.length; i < report.length; i++) check(report[i] == 0);
        }
        public byte[] receive() throws IOException {
            if (frames.isEmpty()) throw new IOException("No more simulated reports");
            return frames.remove();
        }
    }

    private static void rejects(byte[]... frames) throws Exception {
        Reports reports = new Reports(frames);
        try { TokenSerial.read(reports); }
        catch (IOException expected) { check(reports.sends == 1); return; }
        throw new AssertionError("Accepted invalid serial response");
    }

    public static void main(String[] args) throws Exception {
        byte[] valid = response("1234567890");
        check(TokenSerial.read(new Reports(frame(0, 0, valid))).equals("token2-serial:1234567890"));
        check(TokenSerial.read(new Reports(frame(0xc0, 7, new byte[0]), frame(0, 0, response("aBcDeF12"))))
                .equals("token2-serial:abcdef12"));
        String longSerial = "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef";
        byte[] fragmented = response(longSerial);
        check(TokenSerial.read(new Reports(frame(0x20, 0, Arrays.copyOfRange(fragmented, 0, 61)),
                frame(0, 1, Arrays.copyOfRange(fragmented, 61, fragmented.length))))
                .equals("token2-serial:" + longSerial));
        rejects(frame(0, 1, valid));
        rejects(frame(0x10, 0, valid));
        rejects(frame(0, 0, new byte[0]));
        rejects(frame(0x20, 0, new byte[]{1}), frame(0, 0, valid));
        byte[] badHeader = frame(0, 0, valid); badHeader[0] = 0x22; rejects(badHeader);
        byte[] badLength = frame(0, 0, valid); badLength[2] = 62; rejects(badLength);
        rejects(new byte[]{0x21, 0, 5, 1});
        byte[] badStatus = valid.clone(); badStatus[badStatus.length - 2] = 0x6d; rejects(frame(0, 0, badStatus));
        byte[] badTag = valid.clone(); badTag[0] = (byte) 0xd2; rejects(frame(0, 0, badTag));
        byte[] badTlvLength = valid.clone(); badTlvLength[1] = 64; rejects(frame(0, 0, badTlvLength));
        for (String serial : new String[]{"", "1", "12x4", "0000", "FFFF"}) rejects(frame(0, 0, response(serial)));
        byte[][] polling = new byte[64][];
        Arrays.fill(polling, frame(0xc0, 0, new byte[0]));
        rejects(polling);
        System.out.println("PASS " + checks + " serial checks");
    }
}
