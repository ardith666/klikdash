package com.klikdash.app;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Penyetelan sistem yang hilang tiap reboot (matikan ksmd) dan kembali ke home bila Zlink muncul sendiri.
 * Perintahnya butuh hak sistem, jadi dijalankan lewat ADB perangkat itu sendiri (adbd lokal di 127.0.0.1)
 * tanpa mengubah partisi sistem. Hanya bekerja bila ADB aktif di headunit; bila tidak, diam-diam dilewati.
 */
final class Tweaks {
    static final String KSM_RUN = "/sys/kernel/mm/ksm/run";
    static final String KSM_OFF = "echo 0 > " + KSM_RUN;
    /** Tekan HOME hanya bila Zlink sedang di depan (dicek di perangkat, lewat shell ADB). */
    static final String HIDE_ZLINK = "dumpsys activity activities | grep mResumedActivity | grep -q com.zjinnova"
            + " && input keyevent KEYCODE_HOME";

    private static final int CNXN = 0x4e584e43, OPEN = 0x4e45504f, OKAY = 0x59414b4f,
            WRTE = 0x45545257, CLSE = 0x45534c43;

    private Tweaks() { }

    /** Sekali jalan di latar; gagal diam-diam (mis. adbd belum siap). */
    static void shellAsync(String cmd) {
        new Thread(() -> {
            try { shell(cmd); } catch (IOException ignored) { }
        }, "tweaks-cmd").start();
    }

    /** Coba hingga ~2 menit setelah boot (adbd TCP bisa menyala belakangan). */
    static void applyAsync() {
        new Thread(() -> {
            for (int i = 0; i < 12; i++) {
                if ("0".equals(read(KSM_RUN))) return;
                try {
                    shell(KSM_OFF);
                    return;
                } catch (IOException ignored) { }
                try {
                    Thread.sleep(10_000);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "tweaks").start();
    }

    private static String read(String path) {
        try (BufferedReader r = new BufferedReader(new FileReader(path))) {
            String s = r.readLine();
            return s == null ? null : s.trim();
        } catch (IOException e) {
            return null; // SELinux bisa melarang app membaca sysfs; perintahnya idempoten, jadi tetap dijalankan
        }
    }

    private static void shell(String cmd) throws IOException {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", 5555), 3000);
            s.setSoTimeout(5000);
            DataInputStream in = new DataInputStream(s.getInputStream());
            OutputStream out = s.getOutputStream();

            send(out, CNXN, 0x01000000, 4096, "host::klikdash\0");
            if (recv(in)[0] != CNXN) throw new IOException("adbd meminta autentikasi");

            send(out, OPEN, 1, 0, "shell:" + cmd + "\0");
            while (true) {
                int[] m = recv(in);
                if (m[0] == WRTE) {
                    send(out, OKAY, 1, m[1], "");
                } else if (m[0] == CLSE) {
                    send(out, CLSE, 1, m[1], "");
                    return;
                }
            }
        }
    }

    private static void send(OutputStream out, int cmd, int arg0, int arg1, String data) throws IOException {
        byte[] d = data.getBytes(StandardCharsets.UTF_8);
        int sum = 0;
        for (byte b : d) sum += b & 0xff;
        ByteBuffer h = ByteBuffer.allocate(24 + d.length).order(ByteOrder.LITTLE_ENDIAN);
        h.putInt(cmd).putInt(arg0).putInt(arg1).putInt(d.length).putInt(sum).putInt(~cmd).put(d);
        out.write(h.array());
        out.flush();
    }

    /** {command, arg0, arg1}; payload dibaca lalu dibuang. */
    private static int[] recv(DataInputStream in) throws IOException {
        byte[] raw = new byte[24];
        in.readFully(raw);
        ByteBuffer h = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        int cmd = h.getInt(), a0 = h.getInt(), a1 = h.getInt(), len = h.getInt();
        in.readFully(new byte[len]);
        return new int[]{cmd, a0, a1};
    }
}
