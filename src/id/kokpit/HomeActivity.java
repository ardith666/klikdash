package id.kokpit;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ActivityManager;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.UserManager;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/** KlikDash: launcher ringan untuk headunit 1280x720. Tanpa animasi, tanpa library. */
public class HomeActivity extends Activity {

    static int BG = 0xFF0B0B0A, PANEL = 0xFF151513, LINE = 0xFF2B2A26, TEXT = 0xFFECE6D8,
            DIM = 0xFF8A8478, FAINT = 0xFF4A4740, AMBER = 0xFFFFB000, INK = 0xFF0B0B0A;

    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    /** Slot bawaan: komponen + label. Bisa diganti dengan tekan lama. */
    static final String[][] DEFAULTS = {
            {"com.google.android.apps.maps/com.google.android.maps.MapsActivity", "MAPS"},
            {"com.waze/.FreeMapAppActivity", "WAZE"},
            {"com.zjinnova.zlink/com.zjinnova.android.zlink.features.main.MainActivity", "ZLINK"},
            {"com.google.android.youtube/.app.honeycomb.Shell$HomeActivity", "YOUTUBE"},
            {"com.nwd.radio/.home_horizontalActivity", "RADIO"},
            {"com.nwd.android.phone/.home_horizontalActivity", "TELEPON"},
            {"com.nwd.android.video.ui/.home_horizontalActivity", "VIDEO"},
            {"id.kokpit/.QuranActivity", "AL-QUR'AN"},
            {"com.nwd.carkit360camera/.home_horizontalActivity", "KAMERA 360"},
            {"com.android.car.setting/.home_horizontalActivity", "PENGATURAN"},
            {null, null}, // slot bebas: ketuk untuk memilih aplikasi
    };
    static final int SLOTS = DEFAULTS.length;
    /** Grid home: slot + ubin "Semua", 4 kolom. Baris pertama (navigasi & hiburan) bernomor amber. */
    static final int COLS = 4, ROWS = (SLOTS + 1 + COLS - 1) / COLS;

    static final Locale ID = new Locale("id", "ID");

    static class App {
        ComponentName cn;
        String label;
        Drawable icon;
    }

    float dp, sp;
    Typeface fHeavy, fBold, fMed, fMono, fMonoBold;
    ColorFilter mono;
    ColorFilter pressed;
    SharedPreferences prefs;
    Theme theme;
    boolean themeLight;
    FrameLayout rootView;
    ImageView themeImage;
    View themeScrim;
    boolean colorIcons;

    final ComponentName[] slotCn = new ComponentName[SLOTS];
    final String[] slotLabel = new String[SLOTS];
    final Tile[] tiles = new Tile[SLOTS + 1];
    final List<App> apps = new ArrayList<>();
    final Map<String, App> appByKey = new HashMap<>();
    final Map<String, App> appByPkg = new HashMap<>();

    final SimpleDateFormat fmtClock = new SimpleDateFormat("HH:mm", Locale.US);
    final SimpleDateFormat fmtDay = new SimpleDateFormat("EEEE", ID);
    final SimpleDateFormat fmtDate = new SimpleDateFormat("d MMMM yyyy", ID);

    // Lokasi & jadwal sholat. Tanpa GPS, dipakai kota perkiraan dari zona waktu.
    double lat, lon;
    String city;
    boolean locApprox;
    int[] pray, prayTomorrow;
    int prayDayKey = -1;
    LocationManager lm;

    View home;
    VolView vol;
    SpeedView speed;
    float speedKmh;
    boolean speedFix;
    long speedAt, speedLastMs;
    double speedLat, speedLon;
    TextView volPopup;
    String volKey;
    ContentObserver volObs;
    LinearLayout drawer;
    TextView clock, day, date, drawerTitle, drawerCount, tag;
    long cpuTotal, cpuIdle;

    // Beban CPU/RAM + suhu headunit; dibaca tiap 5 detik selama home tampil.
    static int HOT = 0xFFFF5A3C;
    final String cpuZone = findCpuZone();
    final Runnable perfTick = new Runnable() {
        @Override public void run() {
            updatePerf();
            tag.postDelayed(this, 5000);
        }
    };

    static String readLine(String path) {
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(path))) {
            String s = r.readLine();
            return s == null ? null : s.trim();
        } catch (Exception e) {
            return null;
        }
    }

    static String findCpuZone() {
        java.io.File[] zones = new java.io.File("/sys/class/thermal").listFiles();
        if (zones != null) {
            for (java.io.File z : zones) {
                String type = readLine(new java.io.File(z, "type").getPath());
                if (type != null && type.contains("cpu")) return z.getPath() + "/temp";
            }
        }
        return "/sys/class/thermal/thermal_zone0/temp";
    }

    int tempC() {
        String s = readLine(cpuZone);
        if (s == null) return -1;
        try {
            int c = Integer.parseInt(s.trim());
            return c > 1000 ? Math.round(c / 1000f) : c;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    void updatePerf() {
        int mem = 0;
        try {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            ((ActivityManager) getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(mi);
            if (mi.totalMem > 0) mem = Math.round(100f * (mi.totalMem - mi.availMem) / mi.totalMem);
        } catch (Exception ignored) { }
        int cpu = 0;
        String s = readLine("/proc/stat");
        if (s != null) {
            String[] f = s.trim().split("\\s+");
            long total = 0, idle = 0;
            for (int i = 1; i < f.length; i++) {
                try { total += Long.parseLong(f[i]); } catch (NumberFormatException ignored) { }
            }
            if (f.length > 5) {
                try { idle = Long.parseLong(f[4]) + Long.parseLong(f[5]); } catch (NumberFormatException ignored) { }
            }
            if (cpuTotal > 0 && total > cpuTotal) {
                cpu = (int) Math.round(100.0 * (1.0 - (double) (idle - cpuIdle) / (total - cpuTotal)));
                cpu = Math.max(0, Math.min(100, cpu));
            }
            cpuTotal = total;
            cpuIdle = idle;
        }
        if (speed != null) speed.set(speedKmh, speedFix && SystemClock.elapsedRealtime() - speedAt < 5000);
        if (tag == null) return;
        int c = tempC();
        String line = "ARDITH666 \u00b7 MEM " + mem + "% \u00b7 CPU " + cpu + "%";
        if (c >= 0) line += " " + c + "\u00b0C";
        SpannableString ss = new SpannableString(line);
        if (c >= 0) {
            int i = line.indexOf(c + "\u00b0C");
            if (i >= 0) ss.setSpan(new ForegroundColorSpan(c >= 85 ? HOT : c >= 70 ? AMBER : DIM),
                    i, line.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        tag.setText(ss);
    }
    Gauge gauge;
    BtWidget btWidget;
    PrayerView prayerView;
    GridView grid;
    AppAdapter adapter;
    int pickSlot = -1;

    final BroadcastReceiver timeRx = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { updateTime(); }
    };

    final BroadcastReceiver btRx = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (btWidget == null) return;
            String a = i.getAction();
            if ("com.nwd.btmusic.playinfo.changed".equals(a)) {
                btWidget.meta(i.getStringExtra("track"), i.getStringExtra("artist"));
            } else if ("com.bt.ACTION_BT_MUSIC_PLAY".equals(a)) {
                btWidget.play(true);
            } else if ("com.bt.ACTION_BT_MUSIC_PAUSE".equals(a)) {
                btWidget.play(false);
            }
        }
    };

    final LocationListener locRx = new LocationListener() {
        @Override public void onLocationChanged(Location l) { onLocation(l); }
        @Override public void onStatusChanged(String p, int s, Bundle b) { }
        @Override public void onProviderEnabled(String p) { }
        @Override public void onProviderDisabled(String p) { }
    };

    final LocationListener speedRx = new LocationListener() {
        @Override public void onLocationChanged(Location l) { onSpeed(l); }
        @Override public void onStatusChanged(String p, int s, Bundle b) { }
        @Override public void onProviderEnabled(String p) { }
        @Override public void onProviderDisabled(String p) {
            speedFix = false;
            if (speed != null) speed.set(0, false);
        }
    };

    /** Kecepatan dari GPS; l.getSpeed() (Doppler) dipakai bila ada, sisanya hitung jarak/waktu. */
    void onSpeed(Location l) {
        float kmh = 0;
        if (l.hasSpeed()) {
            kmh = l.getSpeed() * 3.6f;
        } else if (speedLastMs > 0) {
            long dt = l.getTime() - speedLastMs;
            if (dt > 0) {
                float[] d = new float[1];
                Location.distanceBetween(speedLat, speedLon, l.getLatitude(), l.getLongitude(), d);
                kmh = d[0] / (dt / 1000f) * 3.6f;
            }
        }
        if (kmh < 3) kmh = 0;
        speedKmh = kmh;
        speedFix = true;
        speedAt = SystemClock.elapsedRealtime();
        speedLastMs = l.getTime();
        speedLat = l.getLatitude();
        speedLon = l.getLongitude();
        if (speed != null) speed.set(kmh, true);
    }

    final BroadcastReceiver pkgRx = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            loadApps();
            adapter.notifyDataSetChanged();
            for (Tile t : tiles) if (t != null) t.invalidate();
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        dp = getResources().getDisplayMetrics().density;
        sp = getResources().getDisplayMetrics().scaledDensity;
        // Direct boot: saat boot penyimpanan biasa (CE) masih terkunci beberapa detik, dan firmware hanya bisa
        // memilih launcher yang directBootAware. Maka pengaturan disimpan di penyimpanan DE.
        prefs = openPrefs();
        themeLight = prefs.getBoolean("themeLight", false);
        theme = Theme.get(prefs.getInt("theme", 1));
        applyTheme();

        // Ikon berwarna normal; jadi amber saat ditekan (beri glow/warna aksen).
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0f);
        cm.postConcat(new ColorMatrix(new float[]{
                1f, 0, 0, 0, 0,
                0, .94f, 0, 0, 0,
                0, 0, .82f, 0, 0,
                0, 0, 0, 1f, 0}));
        mono = new ColorMatrixColorFilter(cm);
        pressed = new PorterDuffColorFilter(AMBER, PorterDuff.Mode.SRC_ATOP);

        colorIcons = prefs.getBoolean("colorIcons", true);
        loadSlots();
        loadApps();
        loadLocation();

        rootView = new FrameLayout(this);
        FrameLayout root = rootView;
        root.setOnLongClickListener(v -> {
            colorIcons = !colorIcons;
            prefs.edit().putBoolean("colorIcons", colorIcons).apply();
            Toast.makeText(this, colorIcons ? "Ikon: berwarna" : "Ikon: monokrom", Toast.LENGTH_SHORT).show();
            loadApps();
            for (Tile t : tiles) if (t != null) t.invalidate();
            return true;
        });
        root.setFitsSystemWindows(true);
        root.setBackgroundColor(BG);
        themeImage = new ImageView(this);
        themeImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(themeImage, MATCH, MATCH);
        themeScrim = new View(this);
        root.addView(themeScrim, MATCH, MATCH);
        home = buildHome();
        drawer = buildDrawer();
        drawer.setVisibility(View.GONE);
        root.addView(home, MATCH, MATCH);
        root.addView(drawer, MATCH, MATCH);
        setContentView(root);
        loadThemeBackground();

        volPopup = text("", fMonoBold, 22, AMBER);
        volPopup.setPadding(px(26), px(12), px(26), px(12));
        GradientDrawable vpBg = new GradientDrawable();
        vpBg.setColor(PANEL);
        vpBg.setStroke(px(1), LINE);
        volPopup.setBackground(vpBg);
        volPopup.setVisibility(View.GONE);
        volPopup.setClickable(false);
        root.addView(volPopup, new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER));
        updateVolume(false);

        IntentFilter tf = new IntentFilter(Intent.ACTION_TIME_TICK);
        tf.addAction(Intent.ACTION_TIME_CHANGED);
        tf.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(timeRx, tf);

        IntentFilter pf = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pf.addDataScheme("package");
        registerReceiver(pkgRx, pf);
        IntentFilter wf = new IntentFilter(Intent.ACTION_SCREEN_ON);
        wf.addAction("com.nwd.ACTION_AUTO_SLEEP_WAKE_STATE");
        wf.addAction("com.nwd.android.ACTION_SYSTEM_AUTO_WAKEUP");
        registerReceiver(wakeRx, wf);
        if (!getSystemService(UserManager.class).isUserUnlocked()) {
            registerReceiver(unlockRx, new IntentFilter(Intent.ACTION_USER_UNLOCKED));
        }
        IntentFilter bf = new IntentFilter("com.nwd.btmusic.playinfo.changed");
        bf.addAction("com.bt.ACTION_BT_MUSIC_PLAY");
        bf.addAction("com.bt.ACTION_BT_MUSIC_PAUSE");
        registerReceiver(btRx, bf);
        volObs = new ContentObserver(null) {
            @Override public void onChange(boolean self, Uri uri) {
                String k = uri == null ? null : uri.getLastPathSegment();
                if (k == null) return;
                volKey = k;
                runOnUiThread(() -> {
                    if ("mcu_max_volume".equals(volKey)) updateVolume(false);
                    else updateVolume(true);
                });
            }
        };
        for (String k : new String[]{"mcu_system_volume", "mcu_bt_volume", "mcu_gps_volume", "mcu_max_volume"})
            getContentResolver().registerContentObserver(Settings.System.getUriFor(k), false, volObs);

        applyLocationExtras(getIntent());
        updateTime();
        startLocation();
        prefs.edit().remove("boot").remove("bootId").apply(); // sisa versi sebelumnya
        Tweaks.applyAsync();
    }

    static final int REQ_IMAGE = 701, REQ_STORAGE = 702;

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (code == REQ_STORAGE) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) pickThemeImage();
            else Toast.makeText(this, "Izin storage diperlukan untuk gambar tema", Toast.LENGTH_LONG).show();
            return;
        }
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startLocation();
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != REQ_IMAGE || result != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        File old = ThemeImages.find(this, theme.id, themeLight);
        if (old == null) {
            importThemeImage(uri);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Timpa gambar tema?")
                .setMessage(old.getName())
                .setPositiveButton("TIMPA", (d, w) -> importThemeImage(uri))
                .setNegativeButton("Batal", null)
                .show();
    }

    void importThemeImage(Uri uri) {
        try {
            ThemeImages.importImage(this, uri, theme.id, themeLight);
            loadThemeBackground();
            Toast.makeText(this, "Gambar tema tersimpan", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Gagal menyimpan gambar", Toast.LENGTH_LONG).show();
        }
    }

    void pickThemeImage() {
        if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
            return;
        }
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("image/*");
        startActivityForResult(i, REQ_IMAGE);
    }

    void loadThemeBackground() {
        if (themeImage == null) return;
        final int id = theme.id;
        final boolean light = themeLight;
        new Thread(() -> {
            Bitmap b = ThemeImages.load(this, id, light);
            runOnUiThread(() -> {
                if (isFinishing() || themeImage == null) return;
                themeImage.setImageBitmap(b);
                themeImage.setVisibility(b == null ? View.GONE : View.VISIBLE);
                themeScrim.setBackgroundColor(b == null ? 0 : theme.scrim(themeLight));
            });
        }, "theme-bg").start();
    }

    void showThemeFolder() {
        String path = ThemeImages.folder(this).getAbsolutePath();
        new AlertDialog.Builder(this)
                .setTitle("FOLDER GAMBAR")
                .setMessage(path + "\n\nNN-dark.jpg / NN-light.jpg")
                .setNeutralButton("SALIN PATH", (d, w) -> {
                    ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("KlikDash themes", path));
                    Toast.makeText(this, "Path disalin", Toast.LENGTH_SHORT).show();
                })
                .setPositiveButton("TUTUP", null)
                .show();
    }

    final Runnable onQuran = () -> { for (Tile t : tiles) if (t != null) t.invalidate(); };

    @Override protected void onResume() {
        super.onResume();
        QuranService.listeners.add(onQuran);
        onQuran.run();
        updateTime();
        updateVolume(false);
        if (lm != null) try { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, speedRx); } catch (Exception ignored) { }
        prayerView.startTicking();
        tag.removeCallbacks(perfTick);
        perfTick.run();
    }

    @Override protected void onPause() {
        if (lm != null) try { lm.removeUpdates(speedRx); } catch (Exception ignored) { }
        prayerView.stopTicking();
        tag.removeCallbacks(perfTick);
        QuranService.listeners.remove(onQuran);
        super.onPause();
    }

    /** true bila aplikasi di depan dibuka lewat KlikDash (ubin/daftar), bukan muncul sendiri. */
    boolean userLaunch;
    /** Awal jendela "baru menyala": boot (0) atau saat bangun dari tidur (ACC on). */
    long wakeAt;

    /** Headunit umumnya tidur saat ACC off, bukan mati; bangun = ACC on. */
    final BroadcastReceiver wakeRx = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { wakeAt = SystemClock.elapsedRealtime(); }
    };

    @Override protected void onStop() {
        super.onStop();
        // Zlink membuka tampilannya sendiri saat CarPlay nirkabel tersambung setelah headunit menyala. Dalam
        // 3 menit pertama, kembalikan ke home; sesi CarPlay tetap jalan di latar dan bisa dibuka dari ubin Zlink.
        if (!userLaunch && SystemClock.elapsedRealtime() - wakeAt < 180_000) {
            home.postDelayed(() -> Tweaks.shellAsync(Tweaks.HIDE_ZLINK), 1500);
        }
        userLaunch = false;
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        applyLocationExtras(intent);
        if (drawer.getVisibility() == View.VISIBLE) closeDrawer();
    }

    @Override public void onBackPressed() {
        if (drawer.getVisibility() == View.VISIBLE) closeDrawer();
    }

    @Override protected void onDestroy() {
        if (lm != null) lm.removeUpdates(locRx);
        unregisterReceiver(timeRx);
        unregisterReceiver(pkgRx);
        unregisterReceiver(wakeRx);
        unregisterReceiver(btRx);
        if (volObs != null) getContentResolver().unregisterContentObserver(volObs);
        try { unregisterReceiver(unlockRx); } catch (IllegalArgumentException ignored) { }
        super.onDestroy();
    }

    // ---------------------------------------------------------------- data

    /** Prefs di penyimpanan DE; pindahkan sekali dari CE (versi lama) begitu CE sudah terbuka. */
    static SharedPreferences openPrefs(Context c) {
        Context de = c.createDeviceProtectedStorageContext();
        java.io.File df = new java.io.File(de.getDataDir(), "shared_prefs/kokpit.xml");
        if (!df.exists() && c.getSystemService(UserManager.class).isUserUnlocked()) {
            de.moveSharedPreferencesFrom(c, "kokpit");
        }
        return de.getSharedPreferences("kokpit", MODE_PRIVATE);
    }

    SharedPreferences openPrefs() {
        return openPrefs(this);
    }

    static void applyPalette(Theme t, boolean light) {
        Theme.P p = t.palette(light);
        BG = p.bg;
        PANEL = Theme.withAlpha(p.panel, t.panelAlpha);
        LINE = p.line;
        TEXT = p.text;
        DIM = p.dim;
        FAINT = p.faint;
        AMBER = p.amber;
        INK = p.ink;
        HOT = p.hot;
    }

    void applyTheme() {
        applyPalette(theme, themeLight);
        fHeavy = font(theme.display, "'opsz' 72, 'wght' 800");
        fBold = font(theme.display, "'opsz' 36, 'wght' 700");
        fMed = font(theme.display, "'opsz' 36, 'wght' 500");
        fMono = font(theme.mono, "'wdth' 100, 'wght' 400");
        fMonoBold = font(theme.mono, "'wdth' 100, 'wght' 700");
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().setBackgroundDrawable(new ColorDrawable(BG));
        if (Build.VERSION.SDK_INT >= 23) {
            int flags = getWindow().getDecorView().getSystemUiVisibility();
            if (themeLight) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            else flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }

    /** Setelah CE terbuka: aplikasi non-directBoot (mis. Maps, Al-Qur'an) baru terlihat oleh PackageManager. */
    final BroadcastReceiver unlockRx = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            prefs = openPrefs();
            loadSlots();
            loadLocation();
            prayDayKey = -1;
            loadApps();
            adapter.notifyDataSetChanged();
            for (Tile t : tiles) if (t != null) t.invalidate();
            updateTime();
        }
    };

    void loadSlots() {
        for (int i = 0; i < SLOTS; i++) {
            String s = prefs.getString("s" + i, null);
            if (s == null) {
                slotCn[i] = DEFAULTS[i][0] != null ? ComponentName.unflattenFromString(DEFAULTS[i][0]) : null;
                slotLabel[i] = DEFAULTS[i][1];
            } else {
                slotCn[i] = s.isEmpty() ? null : ComponentName.unflattenFromString(s); // "" = slot kosong
                slotLabel[i] = prefs.getString("l" + i, null);
            }
        }
    }

    /** {@code label} null: pakai nama aplikasi. */
    void saveSlot(int i, ComponentName cn, String label) {
        slotCn[i] = cn;
        slotLabel[i] = label;
        SharedPreferences.Editor e = prefs.edit().putString("s" + i, cn != null ? cn.flattenToString() : "");
        if (label != null) e.putString("l" + i, label); else e.remove("l" + i);
        e.apply();
    }

    void swapSlots(int a, int b) {
        ComponentName ca = slotCn[a];
        String la = slotLabel[a];
        saveSlot(a, slotCn[b], slotLabel[b]);
        saveSlot(b, ca, la);
        tiles[a].invalidate();
        tiles[b].invalidate();
    }

    void loadApps() {
        apps.clear();
        appByKey.clear();
        appByPkg.clear();
        PackageManager pm = getPackageManager();
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo r : pm.queryIntentActivities(q, 0)) {
            if (HomeActivity.class.getName().equals(r.activityInfo.name)) continue;
            if (r.activityInfo.name.endsWith("WallpaperChooser")) continue; // ikon wallpaper tidak dipakai
            App a = new App();
            a.cn = new ComponentName(r.activityInfo.packageName, r.activityInfo.name);
            a.label = String.valueOf(r.loadLabel(pm)).trim();
            a.icon = r.loadIcon(pm).mutate();
            apps.add(a);
            appByKey.put(a.cn.flattenToString(), a);
            if (!appByPkg.containsKey(a.cn.getPackageName())) appByPkg.put(a.cn.getPackageName(), a);
        }
        Collections.sort(apps, (x, y) -> x.label.compareToIgnoreCase(y.label));
    }

    App appFor(int slot) {
        ComponentName cn = slotCn[slot];
        if (cn == null) return null;
        App a = appByKey.get(cn.flattenToString());
        return a != null ? a : appByPkg.get(cn.getPackageName());
    }

    void launch(ComponentName cn) {
        if (cn.getClassName().endsWith("com.nwd.screenoff.ScreenOffActivity")) { screenOff(); return; }
        userLaunch = true;
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(cn)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(i);
        } catch (Exception e) {
            Intent li = getPackageManager().getLaunchIntentForPackage(cn.getPackageName());
            try {
                if (li != null) { startActivity(li); return; }
            } catch (Exception ignored) { }
            Toast.makeText(this, "Tidak bisa membuka aplikasi", Toast.LENGTH_SHORT).show();
        }
    }

    /** Protokol MCU yang sama dengan tombol Screen Off launcher bawaan (key 58). */
    void screenOff() {
        byte[] p = new byte[9];
        p[0] = (byte) 0xF0;
        p[1] = 6;
        p[2] = 1;
        p[3] = 1;
        p[7] = 58;
        byte sum = 0;
        for (int i = 1; i < p.length - 1; i++) sum = (byte) (sum + p[i]);
        p[8] = sum;
        sendBroadcast(new Intent("com.nwd.action.EMU_RECEIVER_MCU_PROTOCAL").putExtra("protocal", p));
    }

    // ---------------------------------------------------------------- lokasi & sholat

    void loadLocation() {
        String la = prefs.getString("lat", null), lo = prefs.getString("lon", null);
        if (la != null && lo != null) {
            lat = Double.parseDouble(la);
            lon = Double.parseDouble(lo);
            city = prefs.getString("city", null);
            locApprox = false;
            return;
        }
        // Belum pernah dapat lokasi: tebak ibu kota zona waktu.
        String tz = TimeZone.getDefault().getID();
        if (tz.equals("Asia/Makassar")) { lat = -5.1477; lon = 119.4327; city = "MAKASSAR"; }
        else if (tz.equals("Asia/Jayapura")) { lat = -2.5337; lon = 140.7181; city = "JAYAPURA"; }
        else if (tz.equals("Asia/Pontianak")) { lat = -0.0263; lon = 109.3425; city = "PONTIANAK"; }
        else { lat = -6.2088; lon = 106.8456; city = "JAKARTA"; }
        locApprox = true;
    }

    void setLocation(double la, double lo, String name) {
        lat = la;
        lon = lo;
        city = name;
        locApprox = false;
        SharedPreferences.Editor e = prefs.edit().putString("lat", String.valueOf(la)).putString("lon", String.valueOf(lo));
        if (name != null) e.putString("city", name); else e.remove("city");
        e.apply();
        prayDayKey = -1;
        updateTime();
    }

    /** Set lokasi manual lewat adb: am start -n id.kokpit/.HomeActivity --es lat -6.1754 --es lon 106.8272 --es city Jakarta */
    void applyLocationExtras(Intent i) {
        Bundle x = i != null ? i.getExtras() : null;
        if (x == null || !x.containsKey("lat") || !x.containsKey("lon")) return;
        try {
            double la = Double.parseDouble(String.valueOf(x.get("lat")));
            double lo = Double.parseDouble(String.valueOf(x.get("lon")));
            String name = x.getString("city");
            setLocation(la, lo, name != null ? name.toUpperCase(ID) : null);
        } catch (NumberFormatException ignored) { }
    }

    void startLocation() {
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
            return;
        }
        lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        Location best = null;
        for (String p : lm.getProviders(false)) {
            try {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            } catch (Exception ignored) { }
        }
        if (best != null) onLocation(best);
        // GPS headunit sudah selalu aktif (dipakai layanan NWD); passive cukup menumpang, tanpa beban tambahan.
        try {
            lm.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 60_000, 1000, locRx);
        } catch (Exception ignored) { }
    }

    void onLocation(Location l) {
        float[] d = new float[1];
        Location.distanceBetween(lat, lon, l.getLatitude(), l.getLongitude(), d);
        if (!locApprox && d[0] < 3000) return; // pindah < 3 km: jadwal praktis sama
        final double la = l.getLatitude(), lo = l.getLongitude();
        // Masih dalam 15 km dari lokasi tersimpan: nama kota lama tetap dipakai (geocoder tidak selalu ada).
        setLocation(la, lo, !locApprox && d[0] < 15000 ? city : null);
        if (!Geocoder.isPresent()) return;
        new Thread(() -> {
            try {
                List<Address> r = new Geocoder(this, ID).getFromLocation(la, lo, 1);
                if (r == null || r.isEmpty()) return;
                Address a = r.get(0);
                String n = a.getLocality() != null ? a.getLocality() : a.getSubAdminArea();
                if (n == null) return;
                final String name = n.replaceFirst("^(Kecamatan|Kota|Kabupaten|Kab\\.)\\s+", "").toUpperCase(ID);
                runOnUiThread(() -> {
                    if (lat == la && lon == lo) setLocation(la, lo, name);
                });
            } catch (Exception ignored) { }
        }, "geocode").start();
    }

    void computePrayer(Calendar now) {
        int key = now.get(Calendar.YEAR) * 1000 + now.get(Calendar.DAY_OF_YEAR);
        if (key == prayDayKey) return;
        prayDayKey = key;
        pray = PrayerTimes.compute(now, lat, lon);
        Calendar t = (Calendar) now.clone();
        t.add(Calendar.DAY_OF_MONTH, 1);
        prayTomorrow = PrayerTimes.compute(t, lat, lon);
    }

    /** Indeks waktu sholat berikutnya, atau -1 jika berikutnya Subuh besok. */
    int nextPrayer(int minuteOfDay) {
        for (int i = 0; i < pray.length; i++) if (pray[i] > minuteOfDay) return i;
        return -1;
    }

    final Runnable hideVol = new Runnable() {
        @Override public void run() {
            if (volPopup == null) return;
            volPopup.animate().alpha(0f).setDuration(250).withEndAction(() -> volPopup.setVisibility(View.GONE)).start();
        }
    };

    int mcu(String key, int def) {
        try { return Settings.System.getInt(getContentResolver(), key, def); } catch (Exception e) { return def; }
    }

    void updateVolume(boolean popup) {
        int m = Math.max(1, mcu("mcu_max_volume", 40));
        String key = volKey;
        if (key == null || "mcu_max_volume".equals(key))
            key = btWidget != null && btWidget.playing ? "mcu_bt_volume" : "mcu_system_volume";
        int v = Math.max(0, Math.min(m, mcu(key, 0)));
        if (vol != null) vol.set(v, m);
        if (!popup || volPopup == null) return;
        volPopup.setText("VOL " + v);
        volPopup.setVisibility(View.VISIBLE);
        volPopup.setAlpha(1f);
        volPopup.bringToFront();
        volPopup.removeCallbacks(hideVol);
        volPopup.postDelayed(hideVol, 1200);
    }

    void updateTime() {
        if (clock == null) return;
        computePrayer(Calendar.getInstance());
        if (prayerView != null) prayerView.invalidate();
        TimeZone tz = TimeZone.getDefault();
        fmtClock.setTimeZone(tz);
        fmtDay.setTimeZone(tz);
        fmtDate.setTimeZone(tz);
        Date now = new Date();
        clock.setText(fmtClock.format(now));
        day.setText(fmtDay.format(now).toUpperCase(ID));
        date.setText(fmtDate.format(now).toUpperCase(ID));
        gauge.invalidate();
    }

    // ---------------------------------------------------------------- layout

    View buildHome() {
        for (int i = 0; i < tiles.length; i++) tiles[i] = null;
        speed = null;
        if (theme.id == 5) return buildSirkuit();
        if (theme.id == 10) return buildArkade();
        return buildInstrument();
    }

    View buildInstrument() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int pad = px(24);
        row.setPadding(pad, pad, pad, pad);
        row.setBackground(new CropMarks());

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        tag = text("ARDITH666", fMono, 12, DIM);
        tag.setLetterSpacing(0.15f);
        top.addView(tag, new LinearLayout.LayoutParams(0, WRAP, 1f));
        left.addView(top);
        left.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));

        clock = text("00:00", fHeavy, 140, TEXT);
        clock.getPaint().setFakeBoldText(true);
        clock.setLetterSpacing(-0.01f);
        left.addView(clock);

        LinearLayout barRow = new LinearLayout(this);
        barRow.setOrientation(LinearLayout.HORIZONTAL);
        barRow.setGravity(Gravity.CENTER_VERTICAL);
        View bar = new View(this);
        bar.setBackgroundColor(AMBER);
        barRow.addView(bar, new LinearLayout.LayoutParams(px(84), px(6)));
        vol = new VolView();
        LinearLayout.LayoutParams vvl = new LinearLayout.LayoutParams(px(60), px(24));
        vvl.leftMargin = px(16);
        barRow.addView(vol, vvl);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(WRAP, WRAP);
        bl.topMargin = px(14);
        bl.bottomMargin = px(22);
        left.addView(barRow, bl);

        day = text("", fMonoBold, 18, AMBER);
        day.setLetterSpacing(0.3f);
        left.addView(day);

        date = text("", fMed, 36, TEXT);
        date.setLetterSpacing(0.03f);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(WRAP, WRAP);
        dl.topMargin = px(6);
        left.addView(date, dl);

        left.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));
        prayerView = new PrayerView();
        left.addView(prayerView, new LinearLayout.LayoutParams(MATCH, px(104)));
        gauge = new Gauge();
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(MATCH, px(56));
        gl.topMargin = px(18);
        left.addView(gauge, gl);

        btWidget = new BtWidget();
        FrameLayout leftBox = new FrameLayout(this);
        leftBox.addView(left, new FrameLayout.LayoutParams(MATCH, MATCH));
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(px(243), px(275), Gravity.TOP | Gravity.RIGHT);
        bp.topMargin = px(38);
        leftBox.addView(btWidget, bp);
        leftBox.post(() -> {
            bp.topMargin = clock.getTop() + px(15);
            btWidget.setLayoutParams(bp);
        });
        row.addView(leftBox, new LinearLayout.LayoutParams(px(420), MATCH));

        View div = new View(this);
        div.setBackgroundColor(LINE);
        LinearLayout.LayoutParams vl = new LinearLayout.LayoutParams(px(1), MATCH);
        vl.leftMargin = px(28);
        vl.rightMargin = px(28);
        row.addView(div, vl);

        LinearLayout g = new LinearLayout(this);
        g.setOrientation(LinearLayout.VERTICAL);
        int gap = px(12);
        for (int r = 0; r < ROWS; r++) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < COLS; c++) {
                int idx = r * COLS + c;
                if (idx > SLOTS) break;
                if (r == 0 && c < 2) {
                    if (c == 0) {
                        speed = new SpeedView();
                        line.addView(speed, new LinearLayout.LayoutParams(0, MATCH, 2f));
                    }
                    continue;
                }
                Tile t = new Tile(idx);
                tiles[idx] = t;
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, MATCH, 1f);
                if (c > 0) lp.leftMargin = gap;
                line.addView(t, lp);
            }
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(MATCH, 0, 1f);
            if (r > 0) rl.topMargin = gap;
            g.addView(line, rl);
        }
        row.addView(g, new LinearLayout.LayoutParams(0, MATCH, 1f));
        return row;
    }

    LinearLayout vbox() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        return v;
    }

    LinearLayout hbox() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.HORIZONTAL);
        return v;
    }

    void homeFrame(View v, int pad) {
        int p = px(pad);
        v.setPadding(p, p, p, p);
        v.setBackground(new CropMarks());
    }

    TextView tagView() {
        tag = text("ARDITH666", fMono, 12, DIM);
        tag.setLetterSpacing(0.15f);
        return tag;
    }

    LinearLayout timeBlock(float clockSize, boolean center, boolean showBar, boolean showDate) {
        LinearLayout v = vbox();
        if (center) v.setGravity(Gravity.CENTER_HORIZONTAL);
        clock = text("00:00", fHeavy, clockSize, TEXT);
        clock.getPaint().setFakeBoldText(true);
        clock.setLetterSpacing(-0.01f);
        if (center) clock.setGravity(Gravity.CENTER);
        v.addView(clock);
        if (showBar) {
            LinearLayout barRow = hbox();
            barRow.setGravity(Gravity.CENTER_VERTICAL);
            View bar = new View(this);
            bar.setBackgroundColor(AMBER);
            barRow.addView(bar, new LinearLayout.LayoutParams(px(72), px(5)));
            vol = new VolView();
            LinearLayout.LayoutParams vvl = new LinearLayout.LayoutParams(px(60), px(24));
            vvl.leftMargin = px(12);
            barRow.addView(vol, vvl);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(WRAP, WRAP);
            bl.topMargin = px(8);
            bl.bottomMargin = px(8);
            v.addView(barRow, bl);
        } else if (vol == null) {
            vol = new VolView();
        }
        day = text("", fMonoBold, 18, AMBER);
        day.setLetterSpacing(0.3f);
        if (center) day.setGravity(Gravity.CENTER);
        v.addView(day);
        date = text("", fMed, showDate ? 36 : 24, TEXT);
        date.setLetterSpacing(0.03f);
        if (center) date.setGravity(Gravity.CENTER);
        if (!showDate) date.setVisibility(View.GONE);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(WRAP, WRAP);
        dl.topMargin = px(4);
        v.addView(date, dl);
        return v;
    }

    LinearLayout speedBlock(int w, int h) {
        LinearLayout v = vbox();
        speed = new SpeedView();
        v.addView(speed, new LinearLayout.LayoutParams(w, h));
        return v;
    }

    LinearLayout btBlock(int w, int h, int gravity) {
        LinearLayout v = vbox();
        v.setGravity(gravity);
        btWidget = new BtWidget();
        v.addView(btWidget, new LinearLayout.LayoutParams(w, h));
        return v;
    }

    LinearLayout prayerBlock(int h) {
        LinearLayout v = vbox();
        prayerView = new PrayerView();
        v.addView(prayerView, new LinearLayout.LayoutParams(MATCH, h));
        return v;
    }

    LinearLayout gaugeBlock(int h) {
        LinearLayout v = vbox();
        gauge = new Gauge();
        v.addView(gauge, new LinearLayout.LayoutParams(MATCH, h));
        return v;
    }

    LinearLayout tileGrid(int cols, int rows, int gapDp, boolean speedInside) {
        LinearLayout g = vbox();
        int gap = px(gapDp);
        for (int r = 0; r < rows; r++) {
            LinearLayout line = hbox();
            for (int c = 0; c < cols; c++) {
                if (speedInside && r == 0 && c < 2) {
                    if (c == 0) {
                        speed = new SpeedView();
                        line.addView(speed, new LinearLayout.LayoutParams(0, MATCH, 2f));
                    }
                    continue;
                }
                int idx = r * cols + c;
                if (idx > SLOTS) break;
                Tile t = new Tile(idx);
                tiles[idx] = t;
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, MATCH, 1f);
                if (c > 0) lp.leftMargin = gap;
                line.addView(t, lp);
            }
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(MATCH, 0, 1f);
            if (r > 0) rl.topMargin = gap;
            g.addView(line, rl);
        }
        return g;
    }

    View dividerV(int marginDp) {
        View v = new View(this);
        v.setBackgroundColor(LINE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(1), MATCH);
        lp.leftMargin = px(marginDp);
        lp.rightMargin = px(marginDp);
        v.setLayoutParams(lp);
        return v;
    }

    View dividerH(int marginDp) {
        View v = new View(this);
        v.setBackgroundColor(LINE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, px(1));
        lp.topMargin = px(marginDp);
        lp.bottomMargin = px(marginDp);
        v.setLayoutParams(lp);
        return v;
    }

    View buildEditorial() {
        LinearLayout root = vbox();
        homeFrame(root, 28);

        LinearLayout main = hbox();
        LinearLayout left = vbox();
        left.addView(tagView(), new LinearLayout.LayoutParams(MATCH, WRAP));
        left.addView(timeBlock(92, false, true, true), new LinearLayout.LayoutParams(MATCH, WRAP));
        left.addView(prayerBlock(104), new LinearLayout.LayoutParams(MATCH, px(104)));
        left.addView(gaugeBlock(56), new LinearLayout.LayoutParams(MATCH, px(56)));
        left.addView(btBlock(px(240), px(150), Gravity.BOTTOM | Gravity.LEFT));
        main.addView(left, new LinearLayout.LayoutParams(px(380), MATCH));
        main.addView(dividerV(18));
        main.addView(tileGrid(3, 4, 12, false), new LinearLayout.LayoutParams(0, MATCH, 1f));
        root.addView(main, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        return root;
    }

    View buildMonolit() {
        LinearLayout root = hbox();
        homeFrame(root, 32);

        LinearLayout left = vbox();
        LinearLayout top = hbox();
        top.addView(tagView(), new LinearLayout.LayoutParams(0, WRAP, 1f));
        top.addView(speedBlock(px(120), px(70)));
        left.addView(top, new LinearLayout.LayoutParams(MATCH, WRAP));
        left.addView(timeBlock(96, false, false, true), new LinearLayout.LayoutParams(MATCH, WRAP));
        left.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));
        left.addView(btBlock(px(240), px(150), Gravity.BOTTOM | Gravity.LEFT));
        left.addView(prayerBlock(80), new LinearLayout.LayoutParams(MATCH, px(80)));
        left.addView(gaugeBlock(40), new LinearLayout.LayoutParams(MATCH, px(40)));
        root.addView(left, new LinearLayout.LayoutParams(px(310), MATCH));
        root.addView(dividerV(22));
        root.addView(tileGrid(3, 4, 16, false), new LinearLayout.LayoutParams(0, MATCH, 1f));
        return root;
    }

    View buildTerminal() {
        LinearLayout root = vbox();
        homeFrame(root, 16);

        LinearLayout top = hbox();
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(tagView(), new LinearLayout.LayoutParams(0, WRAP, 1f));
        top.addView(timeBlock(56, false, false, false));
        top.addView(speedBlock(px(110), px(70)));
        root.addView(top, new LinearLayout.LayoutParams(MATCH, WRAP));

        root.addView(tileGrid(3, 4, 8, false), new LinearLayout.LayoutParams(MATCH, 0, 1f));

        LinearLayout bottom = hbox();
        bottom.addView(prayerBlock(80), new LinearLayout.LayoutParams(0, px(80), 1f));
        bottom.addView(gaugeBlock(40), new LinearLayout.LayoutParams(px(300), px(40)));
        root.addView(bottom, new LinearLayout.LayoutParams(MATCH, WRAP));
        return root;
    }

    View buildSirkuit() {
        LinearLayout root = vbox();
        homeFrame(root, 20);

        LinearLayout top = hbox();
        top.addView(tagView(), new LinearLayout.LayoutParams(0, WRAP, 1f));
        top.addView(timeBlock(92, false, false, true));
        btWidget = new BtWidget(true);
        top.addView(btWidget, new LinearLayout.LayoutParams(px(360), px(92)));
        root.addView(top, new LinearLayout.LayoutParams(MATCH, WRAP));

        LinearLayout main = hbox();
        LinearLayout left = vbox();
        left.addView(speedBlock(MATCH, px(200)), new LinearLayout.LayoutParams(MATCH, px(200)));
        left.addView(prayerBlock(100), new LinearLayout.LayoutParams(MATCH, px(100)));
        left.addView(gaugeBlock(50), new LinearLayout.LayoutParams(MATCH, px(50)));
        main.addView(left, new LinearLayout.LayoutParams(px(300), MATCH));
        main.addView(dividerV(16));
        main.addView(tileGrid(4, 3, 12, false), new LinearLayout.LayoutParams(0, MATCH, 1f));
        root.addView(main, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        return root;
    }

    View buildRuang() {
        LinearLayout root = vbox();
        homeFrame(root, 24);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        LinearLayout top = hbox();
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(tagView(), new LinearLayout.LayoutParams(0, WRAP, 1f));
        top.addView(timeBlock(90, false, false, true));
        top.addView(btBlock(px(190), px(140), Gravity.TOP | Gravity.RIGHT));
        root.addView(top, new LinearLayout.LayoutParams(MATCH, WRAP));

        root.addView(tileGrid(4, 3, 10, false), new LinearLayout.LayoutParams(MATCH, 0, 1f));

        LinearLayout bottom = hbox();
        bottom.addView(prayerBlock(60), new LinearLayout.LayoutParams(0, px(60), 1f));
        bottom.addView(gaugeBlock(30), new LinearLayout.LayoutParams(px(300), px(30)));
        root.addView(bottom, new LinearLayout.LayoutParams(MATCH, WRAP));
        return root;
    }

    View buildDasbor() {
        LinearLayout root = vbox();
        homeFrame(root, 24);

        LinearLayout head = hbox();
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(tagView(), new LinearLayout.LayoutParams(0, WRAP, 1f));
        head.addView(timeBlock(70, false, false, true));
        head.addView(speedBlock(px(130), px(80)));
        root.addView(head, new LinearLayout.LayoutParams(MATCH, WRAP));

        LinearLayout cards = hbox();
        cards.addView(prayerBlock(90), new LinearLayout.LayoutParams(0, px(90), 1f));
        cards.addView(btBlock(px(260), px(120), Gravity.CENTER));
        cards.addView(gaugeBlock(90), new LinearLayout.LayoutParams(0, px(90), 1f));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(MATCH, WRAP);
        cl.topMargin = px(10);
        root.addView(cards, cl);

        root.addView(tileGrid(4, 3, 10, false), new LinearLayout.LayoutParams(MATCH, 0, 1f));
        return root;
    }

    View buildPoster() {
        LinearLayout root = vbox();
        homeFrame(root, 28);

        LinearLayout main = hbox();
        LinearLayout left = vbox();
        left.addView(tagView(), new LinearLayout.LayoutParams(MATCH, WRAP));
        left.addView(timeBlock(92, false, true, true), new LinearLayout.LayoutParams(MATCH, WRAP));
        left.addView(prayerBlock(70), new LinearLayout.LayoutParams(MATCH, px(70)));
        left.addView(gaugeBlock(36), new LinearLayout.LayoutParams(MATCH, px(36)));
        left.addView(speedBlock(MATCH, px(50)), new LinearLayout.LayoutParams(MATCH, px(50)));
        left.addView(btBlock(px(240), px(150), Gravity.BOTTOM | Gravity.LEFT));
        main.addView(left, new LinearLayout.LayoutParams(px(380), MATCH));
        main.addView(dividerV(18));
        main.addView(tileGrid(3, 4, 10, false), new LinearLayout.LayoutParams(0, MATCH, 1f));
        root.addView(main, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        return root;
    }

    View buildNordik() {
        LinearLayout root = vbox();
        homeFrame(root, 36);

        LinearLayout top = hbox();
        top.addView(tagView(), new LinearLayout.LayoutParams(0, WRAP, 1f));
        top.addView(btBlock(px(220), px(160), Gravity.TOP | Gravity.RIGHT));
        root.addView(top, new LinearLayout.LayoutParams(MATCH, WRAP));

        LinearLayout main = hbox();
        LinearLayout left = vbox();
        left.addView(timeBlock(80, false, false, true), new LinearLayout.LayoutParams(MATCH, WRAP));
        left.addView(prayerBlock(70), new LinearLayout.LayoutParams(MATCH, px(70)));
        left.addView(gaugeBlock(36), new LinearLayout.LayoutParams(MATCH, px(36)));
        left.addView(speedBlock(MATCH, px(54)), new LinearLayout.LayoutParams(MATCH, px(54)));
        main.addView(left, new LinearLayout.LayoutParams(px(330), MATCH));
        main.addView(tileGrid(4, 3, 16, false), new LinearLayout.LayoutParams(0, MATCH, 1f));
        root.addView(main, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        return root;
    }

    View buildArkade() {
        LinearLayout root = vbox();
        homeFrame(root, 16);

        LinearLayout top = hbox();
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(tagView(), new LinearLayout.LayoutParams(0, WRAP, 1f));
        top.addView(timeBlock(64, false, false, false));
        top.addView(speedBlock(px(110), px(70)));
        root.addView(top, new LinearLayout.LayoutParams(MATCH, WRAP));

        LinearLayout main = hbox();
        LinearLayout left = vbox();
        btWidget = new BtWidget(true);
        left.addView(btWidget, new LinearLayout.LayoutParams(MATCH, px(92)));
        prayerView = new PrayerView();
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(MATCH, px(90));
        pl.topMargin = px(12);
        left.addView(prayerView, pl);
        gauge = new Gauge();
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(MATCH, px(38));
        gl.topMargin = px(12);
        left.addView(gauge, gl);
        main.addView(left, new LinearLayout.LayoutParams(px(300), MATCH));
        main.addView(tileGrid(4, 3, 8, false), new LinearLayout.LayoutParams(0, MATCH, 1f));
        root.addView(main, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        return root;
    }

    LinearLayout buildDrawer() {
        LinearLayout d = new LinearLayout(this);
        d.setOrientation(LinearLayout.VERTICAL);
        d.setBackgroundColor(Theme.withAlpha(BG, 0xF2));
        int pad = px(24);
        d.setPadding(pad, pad, pad, 0);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        drawerTitle = text("SEMUA APLIKASI", fHeavy, 40, TEXT);
        drawerTitle.setEllipsize(TextUtils.TruncateAt.END);
        head.addView(drawerTitle, new LinearLayout.LayoutParams(0, WRAP, 1f));
        drawerCount = text("", fMono, 13, AMBER);
        drawerCount.setLetterSpacing(0.2f);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(WRAP, WRAP);
        cl.leftMargin = px(20);
        head.addView(drawerCount, cl);

        Button tema = new Button("TEMA");
        tema.setOnClickListener(v -> showThemePicker());
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(px(120), px(56));
        tl.rightMargin = px(12);
        head.addView(tema, tl);

        Button close = new Button("TUTUP  ✕");
        close.setOnClickListener(v -> closeDrawer());
        head.addView(close, new LinearLayout.LayoutParams(px(168), px(56)));
        d.addView(head, new LinearLayout.LayoutParams(MATCH, px(64)));

        View rule = new View(this);
        rule.setBackgroundColor(LINE);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(MATCH, px(1));
        rl.topMargin = px(16);
        d.addView(rule, rl);

        grid = new GridView(this);
        grid.setNumColumns(7);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setHorizontalSpacing(px(12));
        grid.setVerticalSpacing(px(12));
        grid.setSelector(new ColorDrawable(0));
        grid.setOverScrollMode(View.OVER_SCROLL_NEVER);
        grid.setVerticalScrollBarEnabled(false);
        grid.setPadding(0, px(16), 0, px(24));
        grid.setClipToPadding(false);
        adapter = new AppAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((p, v, pos, id) -> onPick(apps.get(pos)));
        grid.setOnItemLongClickListener((p, v, pos, id) -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", apps.get(pos).cn.getPackageName(), null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        });
        d.addView(grid, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        return d;
    }

    void showThemePicker() {
        Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = vbox();
        int p = px(24);
        root.setPadding(p, p, p, p);
        root.setBackgroundColor(BG);

        LinearLayout head = hbox();
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text("TEMA", fHeavy, 30, TEXT), new LinearLayout.LayoutParams(0, WRAP, 1f));
        Button mode = new Button(themeLight ? "MODE: TERANG" : "MODE: GELAP");
        mode.setOnClickListener(v -> {
            prefs.edit().putBoolean("themeLight", !themeLight).apply();
            d.dismiss();
            recreate();
        });
        head.addView(mode, new LinearLayout.LayoutParams(px(190), px(52)));
        root.addView(head, new LinearLayout.LayoutParams(MATCH, WRAP));

        GridView gv = new GridView(this);
        gv.setNumColumns(5);
        gv.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        gv.setHorizontalSpacing(px(10));
        gv.setVerticalSpacing(px(10));
        gv.setSelector(new ColorDrawable(0));
        gv.setVerticalScrollBarEnabled(false);
        gv.setAdapter(new ThemeAdapter());
        gv.setOnItemClickListener((parent, view, pos, id) -> {
            prefs.edit().putInt("theme", Theme.THEMES[pos].id).apply();
            d.dismiss();
            recreate();
        });
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(MATCH, px(230));
        gl.topMargin = px(18);
        root.addView(gv, gl);

        LinearLayout actions = hbox();
        Button gambar = new Button("GAMBAR");
        gambar.setOnClickListener(v -> {
            d.dismiss();
            pickThemeImage();
        });
        Button folder = new Button("FOLDER");
        folder.setOnClickListener(v -> showThemeFolder());
        Button reload = new Button("MUAT ULANG");
        reload.setOnClickListener(v -> loadThemeBackground());
        actions.addView(gambar, new LinearLayout.LayoutParams(0, px(52), 1f));
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(0, px(52), 1f);
        al.leftMargin = px(8);
        actions.addView(folder, al);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(0, px(52), 1f);
        rl.leftMargin = px(8);
        actions.addView(reload, rl);
        LinearLayout.LayoutParams al2 = new LinearLayout.LayoutParams(MATCH, WRAP);
        al2.topMargin = px(14);
        root.addView(actions, al2);

        d.setContentView(root);
        d.show();
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(BG));
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.92f);
            w.setLayout(width, WRAP);
        }
    }

    class ThemeAdapter extends BaseAdapter {
        @Override public int getCount() { return Theme.THEMES.length; }
        @Override public Object getItem(int i) { return Theme.THEMES[i]; }
        @Override public long getItemId(int i) { return Theme.THEMES[i].id; }

        @Override public View getView(int i, View old, ViewGroup parent) {
            ThemeCard c = old instanceof ThemeCard ? (ThemeCard) old : new ThemeCard();
            c.theme = Theme.THEMES[i];
            c.selected = c.theme.id == theme.id;
            if (old == null) c.setLayoutParams(new AbsListView.LayoutParams(MATCH, px(110)));
            c.invalidate();
            return c;
        }
    }

    class ThemeCard extends View {
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint swatch = new Paint();
        final TextPaint label = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        Theme theme;
        boolean selected;

        ThemeCard() {
            super(HomeActivity.this);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(px(1));
            label.setTypeface(fMonoBold);
            label.setTextSize(sp(11));
            label.setLetterSpacing(0.08f);
        }

        @Override protected void onDraw(Canvas c) {
            if (theme == null) return;
            int w = getWidth(), h = getHeight();
            Theme.P p = theme.palette(themeLight);
            fill.setColor(selected ? Theme.withAlpha(p.amber, 0x24) : Theme.withAlpha(p.panel, 0xFF));
            c.drawRoundRect(px(1), px(1), w - px(1), h - px(1), px(5), px(5), fill);
            stroke.setColor(selected ? p.amber : p.line);
            c.drawRoundRect(px(1), px(1), w - px(1), h - px(1), px(5), px(5), stroke);
            label.setColor(selected ? p.amber : p.text);
            c.drawText(theme.name, px(10), px(24), label);

            int y = h - px(46), cell = px(20), gap = px(6), x = px(10);
            swatch.setColor(p.bg);
            c.drawRect(x, y, x + cell, y + cell, swatch);
            x += cell + gap;
            swatch.setColor(Theme.withAlpha(p.panel, 0xFF));
            c.drawRect(x, y, x + cell, y + cell, swatch);
            x += cell + gap;
            swatch.setColor(p.amber);
            c.drawRect(x, y, x + cell, y + cell, swatch);
            x += cell + gap;
            swatch.setColor(p.text);
            c.drawRect(x, y, x + cell, y + cell, swatch);
        }
    }

    void openDrawer(int slot) {
        pickSlot = slot;
        drawerTitle.setText(slot >= 0
                ? String.format(Locale.US, "PILIH UNTUK SLOT %02d", slot - 1)
                : "SEMUA APLIKASI");
        drawerCount.setText(apps.size() + " APLIKASI");
        grid.setSelection(0);
        home.setVisibility(View.GONE);
        drawer.setVisibility(View.VISIBLE);
    }

    void closeDrawer() {
        pickSlot = -1;
        drawer.setVisibility(View.GONE);
        home.setVisibility(View.VISIBLE);
    }

    void onPick(App a) {
        if (pickSlot >= 0) {
            saveSlot(pickSlot, a.cn, null);
            tiles[pickSlot].invalidate();
        } else {
            launch(a.cn);
        }
        closeDrawer();
    }

    // ---------------------------------------------------------------- helpers

    Typeface font(String file, String axes) {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                Typeface t = new Typeface.Builder(getAssets(), file).setFontVariationSettings(axes).build();
                if (t != null) return t;
            } else {
                return Typeface.createFromAsset(getAssets(), file);
            }
        } catch (Exception ignored) { }
        return Typeface.DEFAULT_BOLD;
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

    void drawIcon(Canvas c, Drawable d, int left, int top, int size, boolean color) {
        d.setBounds(left, top, left + size, top + size);
        if (color) {
            d.setColorFilter(pressed);
            d.setAlpha(255);
        } else {
            if (colorIcons) {
                d.setColorFilter(null);
            } else {
                d.setColorFilter(mono);
            }
            d.setAlpha(225);
        }
        d.draw(c);
    }

    // ---------------------------------------------------------------- views

    /** Panel datar yang berubah amber saat ditekan. Semua digambar sendiri: satu View per tombol. */
    abstract class Panel extends View {
        final Paint fill = new Paint();
        final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint edge = new Paint();
        final Paint hi = new Paint();
        boolean down;

        Panel() {
            super(HomeActivity.this);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(px(1));
            stroke.setColor(LINE);
            hi.setStyle(Paint.Style.STROKE);
            hi.setStrokeWidth(px(1));
        }

        @Override protected void drawableStateChanged() {
            super.drawableStateChanged();
            if (isPressed() != down) {
                down = isPressed();
                invalidate();
            }
        }

        void drawFrame(Canvas c) {
            int w = getWidth(), h = getHeight(), style = theme.frame;
            if (down) {
                fill.setColor(theme.id == 10 ? 0xFF00E5FF : AMBER);
                c.drawRect(0, 0, w, h, fill);
                return;
            }
            if (style == Theme.FRAME_PAPER || style == Theme.FRAME_EXTRUDED) {
                int off = style == Theme.FRAME_EXTRUDED ? px(5) : px(3);
                edge.setColor(Theme.withAlpha(INK, style == Theme.FRAME_EXTRUDED ? 0xC0 : 0x66));
                c.drawRect(off, off, w, h, edge);
            }
            fill.setColor(style == Theme.FRAME_GLASS ? Theme.withAlpha(PANEL, 0xE6) : PANEL);
            c.drawRect(0, 0, w, h, fill);

            float s = stroke.getStrokeWidth() / 2f;
            stroke.setColor((style == Theme.FRAME_GLASS ? Theme.withAlpha(TEXT, 0x33) : (theme.id == 10 ? 0xFF00E5FF : LINE)));
            c.drawRect(s, s, w - s, h - s, stroke);

            if (style == Theme.FRAME_RAISED) {
                hi.setColor(Theme.withAlpha(TEXT, 0x24));
                edge.setColor(Theme.withAlpha(INK, 0x80));
                c.drawLine(0, 0, w, 0, hi);
                c.drawLine(0, 0, 0, h, hi);
                c.drawLine(0, h - px(2), w, h - px(2), edge);
                c.drawLine(w - px(2), 0, w - px(2), h, edge);
            } else if (style == Theme.FRAME_BEVEL) {
                hi.setColor(Theme.withAlpha(TEXT, 0x18));
                c.drawRect(px(3), px(3), w - px(3), h - px(3), hi);
            } else if (style == Theme.FRAME_GLASS) {
                hi.setColor(Theme.withAlpha(TEXT, 0x40));
                c.drawLine(px(2), px(2), w - px(2), px(2), hi);
            } else if (style == Theme.FRAME_EXTRUDED) {
                edge.setColor(theme.id == 10 ? 0xFF00E5FF : (theme.id == 5 ? 0xFFFF8A3D : AMBER));
                c.drawRect(0, h - px(3), w, h, edge);
            }
        }
    }

    class Tile extends Panel {
        final int idx;
        final TextPaint label = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        final TextPaint small = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        final Paint dot = new Paint();

        Tile(int idx) {
            this.idx = idx;
            label.setTypeface(fBold);
            label.setTextSize(sp(30));
            label.setLetterSpacing(0.04f);
            small.setTypeface(fMono);
            small.setTextSize(sp(11));
            small.setLetterSpacing(0.15f);
            setClickable(true);
            setOnClickListener(v -> {
                if (idx == SLOTS) { openDrawer(-1); return; }
                App a = appFor(idx);
                if (a == null) openDrawer(idx); else launch(a.cn);
            });
            if (idx < SLOTS) {
                // Tekan lama: menu ganti/hapus; slot kosong bisa diisi lewat ketukan.
                setOnLongClickListener(v -> {
                    v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    App a = appFor(idx);
                    if (a == null) { openDrawer(idx); return true; }
                    new AlertDialog.Builder(HomeActivity.this)
                            .setTitle(String.format(Locale.US, "SLOT %02d \u00b7 %s", idx - 1, a.label.toUpperCase(ID)))
                            .setItems(new CharSequence[]{"Ganti aplikasi", "Hapus ikon"}, (d, which) -> {
                                if (which == 0) {
                                    openDrawer(idx);
                                } else {
                                    saveSlot(idx, null, null);
                                    tiles[idx].invalidate();
                                }
                            })
                            .setNegativeButton("Batal", null)
                            .show();
                    return true;
                });
            }
        }

        @Override protected void onDraw(Canvas c) {
            drawFrame(c);
            int w = getWidth(), h = getHeight();
            int m = px(16);
            boolean all = idx == SLOTS;
            App a = all ? null : appFor(idx);

            // Slot 01-02 dipakai widget kecepatan; ubin aplikasi mulai dari 01 lagi.
            int num = idx - 1;
            small.setColor(down ? INK : (num <= 2 ? AMBER : TEXT));
            c.drawText(String.format(Locale.US, "%02d", num), m, m + sp(11), small);

            int icon = px(60);
            String name, sub = null;
            if (all) {
                int cell = px(14), g = px(7), x0 = w - m - (cell * 3 + g * 2);
                dot.setColor(down ? INK : TEXT);
                for (int i = 0; i < 9; i++) {
                    int x = x0 + (i % 3) * (cell + g), y = m + (i / 3) * (cell + g);
                    if (i == 4 && !down) dot.setColor(AMBER);
                    c.drawRect(x, y, x + cell, y + cell, dot);
                    if (i == 4) dot.setColor(down ? INK : TEXT);
                }
                name = "SEMUA";
                sub = "APLIKASI  →";
            } else if (a != null) {
                drawIcon(c, a.icon, w - m - icon, m, icon, down);
                name = slotLabel[idx] != null ? slotLabel[idx] : a.label.toUpperCase(ID);
                if (QuranActivity.class.getName().equals(a.cn.getClassName()) && QuranService.playing) {
                    sub = String.format(Locale.US, "▶ %d : %d", QuranService.surah, QuranService.ayah);
                }
            } else {
                name = slotLabel[idx] != null ? slotLabel[idx] : "KOSONG";
                sub = "KETUK UNTUK ISI";
            }

            float base = h - m;
            if (sub != null) {
                small.setColor(down ? INK : DIM);
                CharSequence ss = TextUtils.ellipsize(sub, small, w - m * 2, TextUtils.TruncateAt.END);
                c.drawText(ss, 0, ss.length(), m, base, small);
                base -= sp(11) + px(10);
            }
            label.setColor(down ? INK : (a == null && !all ? FAINT : TEXT));
            // Label panjang dikecilkan dulu (sampai 22sp), baru dipotong kalau masih tidak muat.
            float avail = w - m * 2;
            label.setTextSize(sp(30));
            float tw = label.measureText(name);
            if (tw > avail) label.setTextSize(Math.max(sp(22), sp(30) * avail / tw));
            CharSequence s = TextUtils.ellipsize(name, label, avail, TextUtils.TruncateAt.END);
            c.drawText(s, 0, s.length(), m, base, label);
        }
    }

    class Button extends Panel {
        final String caption;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Button(String caption) {
            this.caption = caption;
            p.setTypeface(fMonoBold);
            p.setTextSize(sp(13));
            p.setLetterSpacing(0.2f);
            p.setTextAlign(Paint.Align.CENTER);
            setClickable(true);
        }

        @Override protected void onDraw(Canvas c) {
            drawFrame(c);
            p.setColor(down ? INK : TEXT);
            Paint.FontMetrics fm = p.getFontMetrics();
            c.drawText(caption, getWidth() / 2f, getHeight() / 2f - (fm.ascent + fm.descent) / 2f, p);
        }
    }

    class Cell extends Panel {
        App app;
        final TextPaint label = new TextPaint(Paint.ANTI_ALIAS_FLAG);

        Cell() {
            label.setTypeface(fBold);
            label.setTextSize(sp(20));
            label.setLetterSpacing(0.04f);
            label.setTextAlign(Paint.Align.CENTER);
        }

        @Override protected void onDraw(Canvas c) {
            drawFrame(c);
            if (app == null) return;
            int w = getWidth(), h = getHeight(), icon = px(64);
            drawIcon(c, app.icon, (w - icon) / 2, px(22), icon, down);
            label.setColor(down ? INK : TEXT);
            CharSequence s = TextUtils.ellipsize(app.label.toUpperCase(ID), label, w - px(20), TextUtils.TruncateAt.END);
            c.drawText(s, 0, s.length(), w / 2f, h - px(20), label);
        }
    }

    class AppAdapter extends BaseAdapter {
        @Override public int getCount() { return apps.size(); }
        @Override public Object getItem(int i) { return apps.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override public View getView(int i, View v, ViewGroup parent) {
            Cell c = v instanceof Cell ? (Cell) v : new Cell();
            if (v == null) c.setLayoutParams(new AbsListView.LayoutParams(MATCH, px(150)));
            c.app = apps.get(i);
            c.invalidate();
            return c;
        }
    }

    /** Ikon speaker + angka volume media, sebaris dengan garis aksen di bawah jam. */
    /** Widget kecepatan GPS untuk slot 01-02; satuan km/jam. */
    class SpeedView extends View {
        final Paint panel = new Paint();
        final Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint big = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint unit = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint gps = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint volBar = new Paint(Paint.ANTI_ALIAS_FLAG);
        float kmh;
        boolean fix;

        SpeedView() {
            super(HomeActivity.this);
            frame.setStyle(Paint.Style.STROKE);
            frame.setStrokeWidth(px(1));
            big.setTypeface(fHeavy);
            big.setFakeBoldText(true);
            big.setStyle(Paint.Style.FILL_AND_STROKE);
            big.setStrokeWidth(px(1));
            big.setStrokeJoin(Paint.Join.ROUND);
            big.setTextAlign(Paint.Align.CENTER);
            unit.setTypeface(fMonoBold);
            unit.setTextAlign(Paint.Align.CENTER);
            unit.setLetterSpacing(0.2f);
            gps.setTypeface(fMonoBold);
            gps.setLetterSpacing(0.15f);
        }

        void set(float v, boolean hasFix) {
            kmh = v;
            fix = hasFix;
            invalidate();
        }


        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            panel.setColor(PANEL);
            c.drawRect(0, 0, w, h, panel);
            frame.setColor(theme.id == 10 ? 0xFF00E5FF : LINE);
            c.drawRect(0.5f, 0.5f, w - 0.5f, h - 0.5f, frame);

            gps.setTextSize(sp(10));
            int accent = theme.id == 10 ? 0xFF00E5FF : AMBER;
            gps.setColor(fix ? accent : DIM);
            c.drawText(fix ? "GPS" : "NO GPS", px(12), px(18), gps);

            int v = Math.round(kmh);
            int color;
            if (!fix || v < 1) color = 0xFF5C5850;        // diam / berhenti: abu gelap
            else if (v < 80) color = 0xFFFFFFFF;          // jalan aman: putih
            else if (v <= 100) color = 0xFFFFC400;        // 80-100: kuning
            else if (v <= 120) color = 0xFFFF8A00;        // 100-120: warning
            else if (v < 150) color = 0xFFFF4A2E;         // 120-149: warning keras
            else color = 0xFFFF1A1A;                      // >=150: merah bahaya
            big.setTextSize(sp(64));
            big.setColor(color);
            c.drawText(fix ? String.valueOf(v) : "--", w / 2f, h * 0.66f, big);

            unit.setTextSize(sp(12));
            unit.setColor(accent);
            c.drawText("KM/H", w / 2f, h - px(12), unit);

            int barX = w - px(22), barW = px(10);
            volBar.setColor((accent & 0x00FFFFFF) | 0x40000000);
            int barTop = px(22), barBot = h - px(22);
            volBar.setStyle(Paint.Style.FILL);
            volBar.setColor((color & 0x00FFFFFF) | 0x40000000);
            c.drawRoundRect(barX, barTop, barX + barW, barBot, px(2), px(2), volBar);
            float frac = Math.min(1f, Math.max(0f, fix ? kmh / 150f : 0f));
            int fillTop = barBot - Math.round((barBot - barTop) * frac);
            if (fillTop < barBot) {
                volBar.setColor(color);
                c.drawRoundRect(barX, fillTop, barX + barW, barBot, px(2), px(2), volBar);
            }
        }
    }

    class VolView extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int v, max = 15;

        VolView() {
            super(HomeActivity.this);
            p.setTypeface(fMonoBold);
            p.setTextSize(sp(12));
        }

        void set(int value, int maximum) {
            v = value;
            max = Math.max(1, maximum);
            invalidate();
        }

        @Override protected void onMeasure(int w, int h) {
            setMeasuredDimension(px(60), px(24));
        }

        @Override protected void onDraw(Canvas c) {
            int cy = getHeight() / 2;
            p.setStyle(Paint.Style.FILL);
            p.setColor(v > 0 ? AMBER : DIM);
            Path sp = new Path();
            sp.moveTo(px(2), cy - px(4));
            sp.lineTo(px(7), cy - px(4));
            sp.lineTo(px(13), cy - px(9));
            sp.lineTo(px(13), cy + px(9));
            sp.lineTo(px(7), cy + px(4));
            sp.lineTo(px(2), cy + px(4));
            sp.close();
            c.drawPath(sp, p);
            if (v > 0) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(px(1.5f));
                p.setColor(AMBER);
                RectF arc = new RectF(px(10), cy - px(7), px(22), cy + px(7));
                c.drawArc(arc, -45, 90, false, p);
                if (v > max / 2) {
                    arc.set(px(10), cy - px(11), px(30), cy + px(11));
                    c.drawArc(arc, -45, 90, false, p);
                }
            }
            p.setStyle(Paint.Style.FILL);
            p.setColor(v > 0 ? TEXT : DIM);
            Paint.FontMetrics fm = p.getFontMetrics();
            c.drawText(String.valueOf(v), px(35), cy - (fm.ascent + fm.descent) / 2f, p);
        }
    }

    /** Kartu pemutar BT: sampul, judul/penyanyi, dan kontrol. Sampul dicari daring lalu di-cache. */
    class BtWidget extends View {
        final Paint panel = new Paint();
        final Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint disc = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint knob = new Paint(Paint.ANTI_ALIAS_FLAG);
        final TextPaint title = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        final TextPaint sub = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        final TextPaint ini = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        final Rect rPrev = new Rect(), rPlay = new Rect(), rNext = new Rect();
        final Rect src = new Rect(), dst = new Rect();
        final int[] palette = {0xFF3A4C44, 0xFF4C3C2E, 0xFF334757, 0xFF4C3542, 0xFF414A32};
        String track = "", artist = "";
        Bitmap art;
        boolean playing;
        int held, seq;
        boolean horizontal;

        BtWidget() {
            this(false);
        }

        BtWidget(boolean horizontal) {
            super(HomeActivity.this);
            this.horizontal = horizontal;
            frame.setStyle(Paint.Style.STROKE);
            frame.setStrokeWidth(px(1));
            frame.setColor(LINE);
            title.setTypeface(fHeavy);
            title.setTextSize(sp(19));
            title.setFakeBoldText(true);
            title.setColor(AMBER);
            title.setTextAlign(Paint.Align.CENTER);
            sub.setTypeface(fMonoBold);
            sub.setTextSize(sp(12));
            sub.setFakeBoldText(true);
            sub.setLetterSpacing(0.08f);
            sub.setColor(AMBER);
            sub.setTextAlign(Paint.Align.CENTER);
            ini.setTypeface(fHeavy);
            ini.setTextSize(sp(44));
            ini.setTextAlign(Paint.Align.CENTER);
            track = prefs.getString("btTrack", "");
            artist = prefs.getString("btArtist", "");
            track = clean(track, artist);
            playing = prefs.getBoolean("btPlaying", false);
            if (!track.isEmpty() || !artist.isEmpty()) load(++seq);
        }

        String clean(String t, String a) {
            if (t == null) return "";
            t = t.trim();
            a = a == null ? "" : a.trim();
            if (!a.isEmpty() && t.toLowerCase().endsWith(a.toLowerCase()))
                t = t.substring(0, t.length() - a.length()).trim();
            return t;
        }

        void meta(String t, String a) {
            artist = a == null ? "" : a.trim();
            track = clean(t, artist);
            art = null;
            seq++;
            prefs.edit().putString("btTrack", track).putString("btArtist", artist).apply();
            invalidate();
            load(seq);
        }

        void play(boolean p) {
            if (playing == p) return;
            playing = p;
            prefs.edit().putBoolean("btPlaying", p).apply();
            invalidate();
        }

        int at(float x, float y) {
            if (rPrev.contains((int) x, (int) y)) return 2;
            if (rPlay.contains((int) x, (int) y)) return 1;
            if (rNext.contains((int) x, (int) y)) return 3;
            return 0;
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            int hit = at(e.getX(), e.getY());
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                held = hit;
                invalidate();
                return hit != 0;
            }
            if (e.getAction() == MotionEvent.ACTION_UP) {
                int was = held;
                held = 0;
                invalidate();
                if (was != 0 && was == hit) {
                    sendBroadcast(new Intent("com.nwd.ACTION_PLAY_BTMUSIC_CMD").putExtra("extra_command", was));
                }
                return true;
            }
            if (e.getAction() == MotionEvent.ACTION_CANCEL) {
                held = 0;
                invalidate();
            }
            return hit != 0;
        }

        void load(final int want) {
            final String t = track, a = artist;
            if (t.isEmpty() && a.isEmpty()) return;
            new Thread(() -> {
                Bitmap b = cover(t, a);
                if (b == null) return;
                runOnUiThread(() -> {
                    if (want == seq) {
                        art = b;
                        invalidate();
                    }
                });
            }, "bt-cover").start();
        }

        Bitmap cover(String t, String a) {
            try {
                File dir = new File(getContext().getFilesDir(), "btart");
                if (!dir.exists()) dir.mkdirs();
                File f = new File(dir, hash(a + "\n" + t) + ".jpg");
                if (f.exists()) {
                    Bitmap b = BitmapFactory.decodeFile(f.getPath());
                    if (b != null) return b;
                }
                String q = URLEncoder.encode((a + " " + t).trim(), "UTF-8");
                byte[] body = http("https://itunes.apple.com/search?term=" + q + "&entity=song&limit=1");
                JSONArray r = new JSONObject(new String(body, "UTF-8")).optJSONArray("results");
                if (r == null || r.length() == 0) return null;
                String u = r.getJSONObject(0).optString("artworkUrl100", "");
                if (u.isEmpty()) return null;
                byte[] img = http(u.replace("100x100", "300x300"));
                Bitmap b = BitmapFactory.decodeByteArray(img, 0, img.length);
                if (b != null) {
                    try (FileOutputStream o = new FileOutputStream(f)) {
                        o.write(img);
                    }
                }
                return b;
            } catch (Exception e) {
                return null;
            }
        }

        byte[] http(String u) throws Exception {
            HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
            c.setConnectTimeout(6000);
            c.setReadTimeout(9000);
            c.setRequestProperty("User-Agent", "KlikDash/1.0");
            try {
                InputStream in = c.getInputStream();
                ByteArrayOutputStream o = new ByteArrayOutputStream();
                byte[] b = new byte[8192];
                int n;
                while ((n = in.read(b)) > 0) o.write(b, 0, n);
                in.close();
                return o.toByteArray();
            } finally {
                c.disconnect();
            }
        }

        String hash(String s) throws Exception {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes("UTF-8"));
            StringBuilder b = new StringBuilder();
            for (byte x : d) b.append(Integer.toHexString((x & 0xff) + 0x100).substring(1));
            return b.toString();
        }

        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight(), pad = px(8);
            panel.setColor(PANEL);
            c.drawRect(0, 0, w, h, panel);
            float st = frame.getStrokeWidth() / 2f;
            c.drawRect(st, st, w - st, h - st, frame);

            if (horizontal) {
                int cs = Math.min(h - pad * 2, px(76));
                int cx = pad, cy = (h - cs) / 2;
                dst.set(cx, cy, cx + cs, cy + cs);
                if (art != null) {
                    int iw = art.getWidth(), ih = art.getHeight(), side = Math.min(iw, ih);
                    src.set((iw - side) / 2, (ih - side) / 2, (iw + side) / 2, (ih + side) / 2);
                    c.drawBitmap(art, src, dst, null);
                } else {
                    int col = track.isEmpty() ? PANEL : palette[(track.hashCode() & 0x7fffffff) % palette.length];
                    disc.setStyle(Paint.Style.FILL);
                    disc.setColor(col);
                    c.drawRect(dst, disc);
                    disc.setStyle(Paint.Style.STROKE);
                    disc.setStrokeWidth(px(1));
                    disc.setColor(track.isEmpty() ? LINE : DIM);
                    c.drawRect(dst, disc);
                    ini.setColor(track.isEmpty() ? FAINT : TEXT);
                    Paint.FontMetrics fm = ini.getFontMetrics();
                    String ch = track.isEmpty() ? "BT" : track.substring(0, 1).toUpperCase(ID);
                    c.drawText(ch, cx + cs / 2f, cy + cs / 2f - (fm.ascent + fm.descent) / 2f, ini);
                }

                float tx = cx + cs + px(14);
                float ty = cy + px(20);
                CharSequence t = TextUtils.ellipsize(track.isEmpty() ? "BT MUSIC" : track, title, w - tx - px(150), TextUtils.TruncateAt.END);
                title.setColor(track.isEmpty() ? FAINT : (theme.id == 5 ? AMBER : AMBER));
                c.drawText(t, 0, t.length(), tx, ty, title);
                String a = artist.isEmpty() ? (track.isEmpty() ? "HUBUNGKAN PONSEL" : "") : artist;
                CharSequence as = TextUtils.ellipsize(a, sub, w - tx - px(150), TextUtils.TruncateAt.END);
                sub.setColor(artist.isEmpty() ? DIM : AMBER);
                c.drawText(as, 0, as.length(), tx, ty + px(18), sub);

                int bw = px(64), bh = px(44);
                int by = (h - bh) / 2;
                int gap = px(6);
                int x = w - pad - (bw * 3 + gap * 2);
                rPrev.set(x, by, x + bw, by + bh);
                x += bw + gap;
                rPlay.set(x, by, x + bw, by + bh);
                x += bw + gap;
                rNext.set(x, by, x + bw, by + bh);
                key(c, rPrev, 2, held == 2);
                key(c, rPlay, playing ? 3 : 1, held == 1);
                key(c, rNext, 4, held == 3);
                return;
            }

            int cs = Math.min(w - pad * 2, px(128));
            int cx = (w - cs) / 2, cy = pad;
            dst.set(cx, cy, cx + cs, cy + cs);
            if (art != null) {
                int iw = art.getWidth(), ih = art.getHeight(), side = Math.min(iw, ih);
                src.set((iw - side) / 2, (ih - side) / 2, (iw + side) / 2, (ih + side) / 2);
                c.drawBitmap(art, src, dst, null);
            } else {
                int col = track.isEmpty() ? PANEL : palette[(track.hashCode() & 0x7fffffff) % palette.length];
                disc.setStyle(Paint.Style.FILL);
                disc.setColor(col);
                c.drawRect(dst, disc);
                disc.setStyle(Paint.Style.STROKE);
                disc.setStrokeWidth(px(1));
                disc.setColor(track.isEmpty() ? LINE : DIM);
                c.drawRect(dst, disc);
                ini.setColor(track.isEmpty() ? FAINT : TEXT);
                Paint.FontMetrics fm = ini.getFontMetrics();
                String ch = track.isEmpty() ? "BT" : track.substring(0, 1).toUpperCase(ID);
                c.drawText(ch, cx + cs / 2f, cy + cs / 2f - (fm.ascent + fm.descent) / 2f, ini);
            }

            float ty = cy + cs + px(28);
            CharSequence t = TextUtils.ellipsize(track.isEmpty() ? "BT MUSIC" : track, title, w - pad * 2, TextUtils.TruncateAt.END);
            title.setColor(track.isEmpty() ? FAINT : AMBER);
            c.drawText(t, 0, t.length(), w / 2f, ty, title);
            String a = artist.isEmpty() ? (track.isEmpty() ? "HUBUNGKAN PONSEL" : "") : artist;
            CharSequence as = TextUtils.ellipsize(a, sub, w - pad * 2, TextUtils.TruncateAt.END);
            sub.setColor(artist.isEmpty() ? DIM : AMBER);
            c.drawText(as, 0, as.length(), w / 2f, ty + px(20), sub);

            int gap = px(6), bh = px(60);
            int bw = (w - pad * 2 - gap * 2) / 3;
            int by = h - pad - bh;
            rPrev.set(pad, by, pad + bw, by + bh);
            rPlay.set(pad + bw + gap, by, pad + bw * 2 + gap, by + bh);
            rNext.set(pad + (bw + gap) * 2, by, w - pad, by + bh);
            key(c, rPrev, 2, held == 2);
            key(c, rPlay, playing ? 3 : 1, held == 1);
            key(c, rNext, 4, held == 3);
        }

        void key(Canvas c, Rect r, int kind, boolean on) {
            knob.setStyle(Paint.Style.FILL);
            knob.setColor(on ? AMBER : PANEL);
            c.drawRect(r, knob);
            frame.setColor(LINE);
            c.drawRect(r, frame);
            knob.setColor(on ? INK : (track.isEmpty() ? FAINT : TEXT));
            float x = r.exactCenterX(), y = r.exactCenterY();
            float a = px(8), b = px(10);
            Path p = new Path();
            if (kind == 1) {
                p.moveTo(x - a * .6f, y - b);
                p.lineTo(x + a, y);
                p.lineTo(x - a * .6f, y + b);
                p.close();
                c.drawPath(p, knob);
            } else if (kind == 2) {
                p.moveTo(x + a, y - b);
                p.lineTo(x - a * .6f, y);
                p.lineTo(x + a, y + b);
                p.close();
                c.drawPath(p, knob);
                c.drawRect(x + a, y - b, x + a + px(2), y + b, knob);
            } else if (kind == 3) {
                c.drawRect(x - px(9), y - b, x - px(3), y + b, knob);
                c.drawRect(x + px(3), y - b, x + px(9), y + b, knob);
            } else {
                p.moveTo(x - a, y - b);
                p.lineTo(x + a * .6f, y);
                p.lineTo(x - a, y + b);
                p.close();
                c.drawPath(p, knob);
                c.drawRect(x + a * .6f, y - b, x + a * .6f + px(2), y + b, knob);
            }
        }
    }

    /** Penggaris 24 jam dengan jarum di waktu sekarang. Digambar ulang sekali per menit. */
    class Gauge extends View {
        final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint num = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path needle = new Path();

        Gauge() {
            super(HomeActivity.this);
            num.setTypeface(fMono);
            num.setTextSize(sp(10));
            num.setLetterSpacing(0.1f);
        }

        @Override protected void onDraw(Canvas c) {
            Calendar cal = Calendar.getInstance();
            float frac = (cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)) / 1440f;
            float w = getWidth() - px(2), x0 = px(1), base = px(26), now = x0 + w * frac;

            line.setStrokeWidth(px(1));
            for (int hr = 0; hr <= 24; hr++) {
                float x = x0 + w * hr / 24f;
                boolean major = hr % 6 == 0;
                line.setColor(x <= now ? AMBER : FAINT);
                c.drawLine(x, base - (major ? px(14) : px(7)), x, base, line);
            }
            line.setColor(FAINT);
            c.drawLine(x0, base, x0 + w, base, line);
            line.setColor(AMBER);
            line.setStrokeWidth(px(3));
            c.drawLine(x0, base, now, base, line);

            needle.rewind();
            needle.moveTo(now - px(6), base - px(24));
            needle.lineTo(now + px(6), base - px(24));
            needle.lineTo(now, base - px(16));
            needle.close();
            line.setStyle(Paint.Style.FILL);
            c.drawPath(needle, line);
            line.setStyle(Paint.Style.STROKE);

            // Penanda waktu sholat: belah ketupat kecil di bawah garis, yang berikutnya amber.
            if (pray != null) {
                int accent = theme.id == 10 ? 0xFF00E5FF : AMBER;
                int next = nextPrayer(cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE));
                float r = px(3.5f), cy = base + px(6);
                line.setStyle(Paint.Style.FILL);
                for (int i = 0; i < pray.length; i++) {
                    float x = x0 + w * pray[i] / 1440f;
                    line.setColor(i == next ? accent : TEXT);
                    needle.rewind();
                    needle.moveTo(x, cy - r);
                    needle.lineTo(x + r, cy);
                    needle.lineTo(x, cy + r);
                    needle.lineTo(x - r, cy);
                    needle.close();
                    c.drawPath(needle, line);
                }
                line.setStyle(Paint.Style.STROKE);
            }

            num.setColor(DIM);
            float y = base + px(20);
            for (int hr = 0; hr <= 24; hr += 6) {
                String s = String.format(Locale.US, "%02d", hr);
                num.setTextAlign(hr == 0 ? Paint.Align.LEFT : hr == 24 ? Paint.Align.RIGHT : Paint.Align.CENTER);
                c.drawText(s, x0 + w * hr / 24f, y, num);
            }
        }
    }

    /** Lima waktu sholat + hitung mundur ke waktu berikutnya. Digambar ulang sekali per menit. */
    class PrayerView extends View {
        final TextPaint name = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        final TextPaint time = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        final TextPaint info = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        final Paint rule = new Paint();

        /** Hitung mundur berdetik: hanya view kecil ini yang digambar ulang tiap detik, selama home tampil. */
        final Runnable tick = new Runnable() {
            @Override public void run() {
                invalidate();
                postDelayed(this, 1000 - System.currentTimeMillis() % 1000);
            }
        };

        void startTicking() {
            removeCallbacks(tick);
            tick.run();
        }

        void stopTicking() { removeCallbacks(tick); }

        PrayerView() {
            super(HomeActivity.this);
            name.setTypeface(fMono);
            name.setTextSize(sp(10));
            name.setLetterSpacing(0.15f);
            time.setTypeface(fBold);
            time.setTextSize(sp(30));
            time.setLetterSpacing(0.02f);
            info.setTypeface(fMono);
            info.setTextSize(sp(11));
            info.setLetterSpacing(0.12f);
            rule.setColor(LINE);
        }

        @Override protected void onDraw(Canvas c) {
            if (pray == null) return;
            int w = getWidth();
            Calendar cal = Calendar.getInstance();
            int now = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
            int next = nextPrayer(now);

            c.drawRect(0, 0, w, px(1), rule);
            float col = w / 5f;
            for (int i = 0; i < pray.length; i++) {
                float x = i * col;
                int color = i == next ? AMBER : pray[i] <= now ? DIM : TEXT;
                if (i == next) c.drawRect(x, px(1), x + px(28), px(4), paintOf(AMBER));
                name.setColor(i == next ? AMBER : DIM);
                c.drawText(PrayerTimes.NAMES[i], x, px(28), name);
                time.setColor(color);
                c.drawText(String.format(Locale.US, "%02d:%02d", pray[i] / 60, pray[i] % 60), x, px(64), time);
            }

            int target = next >= 0 ? pray[next] : 1440 + prayTomorrow[0];
            int left = (target - now) * 60 - cal.get(Calendar.SECOND); // detik tersisa
            String wait = String.format(Locale.US, "%02d:%02d:%02d", left / 3600, left / 60 % 60, left % 60);
            String msg = "▸ " + PrayerTimes.NAMES[next >= 0 ? next : 0] + " DALAM " + wait;
            info.setColor(AMBER);
            info.setTextAlign(Paint.Align.LEFT);
            c.drawText(msg, 0, px(96), info);

            String place = (city != null ? city : String.format(Locale.US, "%.2f, %.2f", lat, lon))
                    + (locApprox ? " · PERKIRAAN" : "");
            float avail = w - info.measureText(msg) - px(16);
            info.setColor(DIM);
            info.setTextAlign(Paint.Align.RIGHT);
            CharSequence p = TextUtils.ellipsize(place, info, avail, TextUtils.TruncateAt.END);
            c.drawText(p, 0, p.length(), w, px(96), info);
        }
    }

    final Paint solid = new Paint();

    Paint paintOf(int color) {
        solid.setColor(color);
        return solid;
    }

    /** Tanda potong tipis di empat sudut layar, gaya panel instrumen. */
    class CropMarks extends Drawable {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        CropMarks() {
            p.setColor(FAINT);
            p.setStrokeWidth(px(1.5f));
        }

        @Override public void draw(Canvas c) {
            if (!theme.cropMarks) return;
            Rect b = getBounds();
            int i = px(9), l = px(14);
            float L = b.left + i, T = b.top + i, R = b.right - i, B = b.bottom - i;
            c.drawLine(L, T, L + l, T, p); c.drawLine(L, T, L, T + l, p);
            c.drawLine(R, T, R - l, T, p); c.drawLine(R, T, R, T + l, p);
            c.drawLine(L, B, L + l, B, p); c.drawLine(L, B, L, B - l, p);
            c.drawLine(R, B, R - l, B, p); c.drawLine(R, B, R, B - l, p);
        }

        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(ColorFilter f) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
