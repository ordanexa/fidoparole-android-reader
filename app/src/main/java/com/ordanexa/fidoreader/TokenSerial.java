package com.ordanexa.fidoreader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** Read-only Token2 serial command, using unnumbered 64-byte HID feature reports. */
final class TokenSerial {
    interface Reports {
        void send(byte[] report) throws Exception;
        byte[] receive() throws Exception;
    }

    static String read(Reports reports) throws Exception {
        // Vendor reference: extended APDU 80 33 00 00 00 00 12 D1 10 + 16 zero bytes.
        byte[] request = new byte[64];
        request[0] = 0x21;
        request[2] = 25;
        request[3] = (byte) 0x80;
        request[4] = 0x33;
        request[9] = 18;
        request[10] = (byte) 0xd1;
        request[11] = 16;
        reports.send(request);

        ByteArrayOutputStream response = new ByteArrayOutputStream();
        int sequence = 0;
        for (int polls = 0; polls < 64; polls++) {
            byte[] report = reports.receive();
            if (report.length < 3 || report.length > 64 || report[0] != 0x21) {
                throw new IOException("Invalid Token2 serial report");
            }
            int flags = report[1] & 0xf0;
            if (flags == 0xc0) continue;
            int length = report[2] & 0xff;
            if ((flags != 0 && flags != 0x20) || (report[1] & 15) != (sequence & 15)
                    || length == 0 || length > 61 || length > report.length - 3
                    || response.size() + length > 128) {
                throw new IOException("Invalid Token2 serial framing");
            }
            sequence++;
            response.write(report, 3, length);
            if (flags == 0) return parse(response.toByteArray());
        }
        throw new IOException("Token2 serial query did not finish");
    }

    private static String parse(byte[] response) throws IOException {
        if (response.length < 6 || response[response.length - 2] != (byte) 0x90
                || response[response.length - 1] != 0 || response[0] != (byte) 0xd1) {
            throw new IOException("Token2 serial query unavailable");
        }
        int length = response[1] & 0xff;
        if (length < 2 || length > 64 || (length & 1) != 0 || response.length != length + 4) {
            throw new IOException("Invalid Token2 serial length");
        }
        StringBuilder serial = new StringBuilder(length);
        boolean allZero = true, allOnes = true;
        for (int i = 0; i < length; i++) {
            int value = response[i + 2] & 0xff;
            if (value >= 'A' && value <= 'F') value += 'a' - 'A';
            if (!((value >= '0' && value <= '9') || (value >= 'a' && value <= 'f'))) {
                throw new IOException("Invalid Token2 serial encoding");
            }
            serial.append((char) value);
            allZero &= value == '0';
            allOnes &= value == 'f';
        }
        if (allZero || allOnes) throw new IOException("Empty Token2 serial number");
        return "token2-serial:" + serial;
    }
}
