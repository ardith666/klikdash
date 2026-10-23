package com.klikdash.app;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.klikdash.app.HomeActivity.AMBER;
import static com.klikdash.app.HomeActivity.BG;
import static com.klikdash.app.HomeActivity.DIM;
import static com.klikdash.app.HomeActivity.FAINT;
import static com.klikdash.app.HomeActivity.INK;
import static com.klikdash.app.HomeActivity.LINE;
import static com.klikdash.app.HomeActivity.PANEL;
import static com.klikdash.app.HomeActivity.TEXT;

/**
 * Hemat Daya: larang aplikasi pihak ketiga berjalan di latar (appops RUN_ANY_IN_BACKGROUND/RUN_IN_BACKGROUND
 * = ignore). Aplikasinya tidak dihapus dan tetap bisa dibuka. Perintah dijalankan lewat ADB perangkat sendiri.
 */
public class PowerActivity extends Activity {

    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT, WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    static final Locale ID = new Locale("id", "ID");

    /** Tidak pernah ditampilkan: dibutuhkan mobil atau navigasi tetap jalan di latar. */
    static final Set<String> PROTECTED = new HashSet<>(Arrays.asList(
            "com.zjinnova.zlink", "com.google.android.apps.maps", "com.waze",
            "com.google.android.gms", "com.android.vending"));

    static final class Item {
        String pkg, label;
        Drawable icon;
        boolean restricted, running;
    }

    float dp, sp;
    Typeface fHeavy, fBold, fMono, fMonoBold, fSerif;
    final List<Item> items = new ArrayList<>();
    ListView list;
    Adapter adapter;
    TextView count, message;
    boolean busy;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        dp = getResources().getDisplayMetrics().density;
        sp = getResources().getDisplayMetrics().scaledDensity;
        fHeavy = font("display.ttf", "'opsz' 72, 'wght' 800");
        fBold = font("display.ttf", "'opsz' 36, 'wght' 700");
        fMono = font("mono.ttf", "'wdth' 100, 'wght' 400");
        fMonoBold = font("mono.ttf", "'wdth' 100, 'wght' 700");
        fSerif = font("serif.ttf", null);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setFitsSystemWindows(true);
        root.setPadding(px(24), px(24), px(24), 0);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text("HEMAT DAYA", fHeavy, 40, TEXT));
        count = text("", fMono, 13, AMBER);
        count.setLetterSpacing(0.2f);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(WRAP, WRAP);
        cl.leftMargin = px(20);
        head.addView(count, cl);
        head.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        Key reset = new Key("KEMBALIKAN SEMUA", false, this::restoreAll);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(px(230), px(56));
        rl.rightMargin = px(12);
        head.addView(reset, rl);
        head.addView(new Key("TUTUP  ✕", false, this::finish), new LinearLayout.LayoutParams(px(168), px(56)));
        root.addView(head, new LinearLayout.LayoutParams(MATCH, px(64)));

        TextView sub = text("Larang aplikasi berjalan diam-diam di latar supaya headunit lebih ringan dan dingin. "
                + "Aplikasinya tetap terpasang dan bisa dibuka seperti biasa. Aplikasi sistem, navigasi, dan Zlink "
                + "tidak ditampilkan.", fSerif, 16, 0xFFD9D3C5);
        sub.setSingleLine(false);
        sub.setLineSpacing(0, 1.25f);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(MATCH, WRAP);
        sl.topMargin = px(12);
        sl.bottomMargin = px(16);
        root.addView(sub, sl);

        View rule = new View(this);
        rule.setBackgroundColor(LINE);
        root.addView(rule, new LinearLayout.LayoutParams(MATCH, px(1)));

        FrameLayout body = new FrameLayout(this);
        list = new ListView(this);
        list.setDivider(new ColorDrawable(LINE));
        list.setDividerHeight(px(1));
        list.setSelector(new ColorDrawable(0));
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list.setVerticalScrollBarEnabled(false);
        adapter = new Adapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> toggle(items.get(pos)));
        body.addView(list, MATCH, MATCH);
        message = text("", fMonoBold, 14, AMBER);
        message.setLetterSpacing(0.25f);
        message.setGravity(Gravity.CENTER);
        message.setSingleLine(false);
        message.setBackgroundColor(BG);
        body.addView(message, MATCH, MATCH);
        root.addView(body, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        setContentView(root);

        refresh();
    }

    // ---------------------------------------------------------------- data

    /** Aplikasi pihak ketiga yang bisa dibatasi, beserta status dari appops dan pidof. */
    void refresh() {
        busy = true;
        showMessage("MEMERIKSA APLIKASI…");
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            List<Item> found = new ArrayList<>();
            for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
                if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
                if (ai.packageName.equals(getPackageName()) || PROTECTED.contains(ai.packageName)) continue;
                if (ai.packageName.contains(".nwd.")) continue;
                Item it = new Item();
                it.pkg = ai.packageName;
                it.label = String.valueOf(ai.loadLabel(pm)).trim();
                it.icon = ai.loadIcon(pm);
                found.add(it);
            }
            StringBuilder cmd = new StringBuilder();
            for (Item it : found) {
                cmd.append("echo \"").append(it.pkg).append("|$(cmd appops get ").append(it.pkg)
                        .append(" RUN_ANY_IN_BACKGROUND)|$(pidof ").append(it.pkg).append(")\";");
            }
            String out;
            try {
                out = found.isEmpty() ? "" : Tweaks.exec(cmd.toString());
            } catch (Exception e) {
                runOnUiThread(() -> {
                    busy = false;
                    showMessage("BUTUH USB DEBUGGING\n\nAktifkan di Pengaturan › Opsi pengembang › USB debugging,\n"
                            + "lalu buka Hemat Daya lagi.");
                });
                return;
            }
            for (String line : out.split("\n")) {
                String[] f = line.trim().split("\\|", -1);
                if (f.length < 3) continue;
                for (Item it : found) {
                    if (!it.pkg.equals(f[0])) continue;
                    it.restricted = f[1].contains("ignore");
                    it.running = !f[2].trim().isEmpty();
                }
            }
            // Yang sedang jalan di latar dan belum dibatasi paling atas, lalu urut nama.
            found.sort((x, y) -> {
                int a = (x.running && !x.restricted) ? 0 : 1, c = (y.running && !y.restricted) ? 0 : 1;
                return a != c ? a - c : x.label.compareToIgnoreCase(y.label);
            });
            runOnUiThread(() -> {
                busy = false;
                items.clear();
                items.addAll(found);
                adapter.notifyDataSetChanged();
                updateCount();
                if (items.isEmpty()) showMessage("TIDAK ADA APLIKASI PIHAK KETIGA"); else message.setVisibility(View.GONE);
            });
        }, "power-refresh").start();
    }

    void toggle(Item it) {
        if (busy) return;
        busy = true;
        String p = it.pkg;
        String cmd = it.restricted
                ? "cmd appops set " + p + " RUN_ANY_IN_BACKGROUND allow; cmd appops set " + p + " RUN_IN_BACKGROUND allow"
                : "cmd appops set " + p + " RUN_ANY_IN_BACKGROUND ignore; cmd appops set " + p + " RUN_IN_BACKGROUND ignore; "
                  + "am force-stop " + p;
        run(cmd);
    }

    void restoreAll() {
        if (busy) return;
        StringBuilder cmd = new StringBuilder();
        for (Item it : items) {
            if (!it.restricted) continue;
            cmd.append("cmd appops set ").append(it.pkg).append(" RUN_ANY_IN_BACKGROUND allow; cmd appops set ")
                    .append(it.pkg).append(" RUN_IN_BACKGROUND allow; ");
        }
        if (cmd.length() == 0) return;
        busy = true;
        run(cmd.toString());
    }

    void run(String cmd) {
        new Thread(() -> {
            try {
                Tweaks.exec(cmd);
            } catch (Exception ignored) { }
            runOnUiThread(this::refresh);
        }, "power-apply").start();
    }

    void updateCount() {
        int r = 0;
        for (Item it : items) if (it.restricted) r++;
        count.setText(items.size() + " APLIKASI  ·  " + r + " DIBATASI");
    }

    void showMessage(String s) {
        message.setText(s);
        message.setVisibility(View.VISIBLE);
    }

    // ---------------------------------------------------------------- helpers

    Typeface font(String file, String axes) {
        try {
            Typeface.Builder b = new Typeface.Builder(getAssets(), file);
            if (axes != null) b.setFontVariationSettings(axes);
            Typeface t = b.build();
            if (t != null) return t;
        } catch (Exception ignored) { }
        return Typeface.DEFAULT;
    }

    TextView text(String s, Typeface tf, float size, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTypeface(tf);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        return t;
    }

    int px(float v) { return Math.round(v * dp); }

    float sp(float v) { return v * sp; }

    // ---------------------------------------------------------------- views

    /** Tombol teks; amber bila {@code on}. */
    class Key extends View {
        final String label;
        final boolean on;
        boolean down;
        final Paint fill = new Paint(), stroke = new Paint(Paint.ANTI_ALIAS_FLAG), p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Key(String label, boolean on, Runnable click) {
            super(PowerActivity.this);
            this.label = label;
            this.on = on;
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(px(1));
            stroke.setColor(LINE);
            p.setTypeface(fMonoBold);
            p.setTextSize(sp(13));
            p.setLetterSpacing(0.2f);
            p.setTextAlign(Paint.Align.CENTER);
            setClickable(true);
            setOnClickListener(v -> click.run());
        }

        @Override protected void drawableStateChanged() {
            super.drawableStateChanged();
            if (isPressed() != down) { down = isPressed(); invalidate(); }
        }

        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            fill.setColor(down ? AMBER : on ? AMBER : PANEL);
            c.drawRect(0, 0, w, h, fill);
            if (!down && !on) {
                float s = stroke.getStrokeWidth() / 2f;
                c.drawRect(s, s, w - s, h - s, stroke);
            }
            p.setColor(down || on ? INK : TEXT);
            Paint.FontMetrics fm = p.getFontMetrics();
            c.drawText(label, w / 2f, h / 2f - (fm.ascent + fm.descent) / 2f, p);
        }
    }

    /** Satu baris aplikasi: ikon, nama, status, dan sakelar di kanan. Ketuk di mana saja untuk mengubah. */
    class Row extends View {
        Item item;
        boolean down;
        final Paint fill = new Paint(), stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        final TextPaint name = new TextPaint(Paint.ANTI_ALIAS_FLAG), status = new TextPaint(Paint.ANTI_ALIAS_FLAG),
                sw = new TextPaint(Paint.ANTI_ALIAS_FLAG);

        Row() {
            super(PowerActivity.this);
            name.setTypeface(fBold);
            name.setTextSize(sp(24));
            name.setLetterSpacing(0.03f);
            status.setTypeface(fMono);
            status.setTextSize(sp(10));
            status.setLetterSpacing(0.15f);
            sw.setTypeface(fMonoBold);
            sw.setTextSize(sp(12));
            sw.setLetterSpacing(0.2f);
            sw.setTextAlign(Paint.Align.CENTER);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(px(1));
            stroke.setColor(LINE);
            setLayoutParams(new AbsListView.LayoutParams(MATCH, px(84)));
        }

        @Override protected void drawableStateChanged() {
            super.drawableStateChanged();
            if (isPressed() != down) { down = isPressed(); invalidate(); }
        }

        @Override protected void onDraw(Canvas c) {
            if (item == null) return;
            int w = getWidth(), h = getHeight(), icon = px(48), x = px(16);
            if (down) {
                fill.setColor(PANEL);
                c.drawRect(0, 0, w, h, fill);
            }
            item.icon.setBounds(x, (h - icon) / 2, x + icon, (h + icon) / 2);
            item.icon.draw(c);

            int sww = px(180), swh = px(48), swx = w - px(16) - sww, swy = (h - swh) / 2;
            fill.setColor(item.restricted ? AMBER : PANEL);
            c.drawRect(swx, swy, swx + sww, swy + swh, fill);
            if (!item.restricted) {
                float s = stroke.getStrokeWidth() / 2f;
                c.drawRect(swx + s, swy + s, swx + sww - s, swy + swh - s, stroke);
            }
            sw.setColor(item.restricted ? INK : TEXT);
            Paint.FontMetrics fm = sw.getFontMetrics();
            c.drawText(item.restricted ? "DIBATASI" : "BATASI", swx + sww / 2f, h / 2f - (fm.ascent + fm.descent) / 2f, sw);

            float tx = x + icon + px(20), avail = swx - tx - px(20);
            name.setColor(TEXT);
            CharSequence n = TextUtils.ellipsize(item.label.toUpperCase(ID), name, avail, TextUtils.TruncateAt.END);
            c.drawText(n, 0, n.length(), tx, px(40), name);
            String st = item.restricted ? "TIDAK BISA JALAN DI LATAR"
                    : item.running ? "SEDANG BERJALAN DI LATAR" : "BOLEH JALAN DI LATAR";
            status.setColor(item.running && !item.restricted ? AMBER : item.restricted ? DIM : FAINT);
            CharSequence ss = TextUtils.ellipsize(st + "  ·  " + item.pkg, status, avail, TextUtils.TruncateAt.END);
            c.drawText(ss, 0, ss.length(), tx, px(62), status);
        }
    }

    class Adapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int i) { return items.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override public View getView(int i, View v, ViewGroup parent) {
            Row r = v instanceof Row ? (Row) v : new Row();
            r.item = items.get(i);
            r.invalidate();
            return r;
        }
    }
}
