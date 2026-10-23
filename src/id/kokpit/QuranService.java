package id.kokpit;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pemutar murattal per ayat (Mishary Rashid Alafasy) yang tetap jalan di latar.
 * File audio: <external files>/quran/audio/SSSAAA.mp3 (format everyayah.com).
 */
public class QuranService extends Service
        implements MediaPlayer.OnCompletionListener, AudioManager.OnAudioFocusChangeListener {

    static final String ACTION_PLAY = "id.kokpit.quran.PLAY", ACTION_TOGGLE = "id.kokpit.quran.TOGGLE",
            ACTION_NEXT = "id.kokpit.quran.NEXT", ACTION_PREV = "id.kokpit.quran.PREV",
            ACTION_STOP = "id.kokpit.quran.STOP";

    /** LANJUT: terus ke surah berikutnya; SURAH/AYAT: ulangi. */
    static final int MODE_CONTINUE = 0, MODE_REPEAT_SURAH = 1, MODE_REPEAT_AYAH = 2;
    static final String RECITER = "Mishary Rashid Alafasy";

    // Status dibaca langsung oleh UI (satu proses, thread utama).
    static int surah = 1, ayah = 1, mode = MODE_CONTINUE;
    static boolean playing, active, bismillah, missing;
    static final List<Runnable> listeners = new ArrayList<>();

    static File audioDir(Context c) { return new File(c.getExternalFilesDir(null), "quran/audio"); }

    static File audioFile(Context c, int s, int a) {
        return new File(audioDir(c), String.format(Locale.US, "%03d%03d.mp3", s, a));
    }

    static void send(Context c, String action) {
        c.startForegroundService(new Intent(c, QuranService.class).setAction(action));
    }

    static void play(Context c, int s, int a) {
        c.startForegroundService(new Intent(c, QuranService.class).setAction(ACTION_PLAY)
                .putExtra("s", s).putExtra("a", a));
    }

    static void loadState(Context c) {
        SharedPreferences p = c.getSharedPreferences("quran", MODE_PRIVATE);
        surah = p.getInt("s", 1);
        ayah = p.getInt("a", 1);
        mode = p.getInt("mode", MODE_CONTINUE);
    }

    static void setMode(Context c, int m) {
        mode = m;
        c.getSharedPreferences("quran", MODE_PRIVATE).edit().putInt("mode", m).apply();
        notifyListeners();
    }

    static void notifyListeners() {
        for (Runnable r : new ArrayList<>(listeners)) r.run();
    }

    MediaPlayer player;
    MediaSession session;
    AudioManager am;
    AudioFocusRequest focus;
    NotificationManager nm;
    boolean resumeOnGain;
    final Handler main = new Handler(Looper.getMainLooper());
    final AudioAttributes attrs = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build();

    @Override public void onCreate() {
        super.onCreate();
        loadState(this);
        am = (AudioManager) getSystemService(AUDIO_SERVICE);
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel("quran", "Murattal", NotificationManager.IMPORTANCE_LOW));
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(this)
                .build();

        session = new MediaSession(this, "kokpit-quran");
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { resume(); }
            @Override public void onPause() { pause(); }
            @Override public void onStop() { pause(); }
            @Override public void onSkipToNext() { step(1); }
            @Override public void onSkipToPrevious() { step(-1); }
        });
        session.setActive(true);
        active = true;

        // Nama surah untuk notifikasi; teks dimuat di latar.
        new Thread(() -> {
            try { QuranData.load(this); } catch (Exception ignored) { }
            main.post(this::publish);
        }, "quran-load").start();
    }

    @Override public int onStartCommand(Intent i, int flags, int id) {
        startForeground(1, notification());
        String a = i != null ? i.getAction() : null;
        if (ACTION_PLAY.equals(a)) {
            start(i.getIntExtra("s", surah), i.getIntExtra("a", 1), true);
        } else if (ACTION_TOGGLE.equals(a)) {
            if (playing) pause(); else resume();
        } else if (ACTION_NEXT.equals(a)) {
            step(1);
        } else if (ACTION_PREV.equals(a)) {
            step(-1);
        } else if (ACTION_STOP.equals(a)) {
            stopAll();
        } else {
            publish();
        }
        return START_NOT_STICKY;
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onDestroy() {
        release();
        am.abandonAudioFocusRequest(focus);
        session.release();
        playing = false;
        active = false;
        notifyListeners();
        super.onDestroy();
    }

    // ------------------------------------------------------------ pemutaran

    /** Mulai dari ayat {@code a}; basmalah diputar dulu bila ayat 1 dan surah memilikinya. */
    void start(int s, int a, boolean withBismillah) {
        surah = s;
        ayah = Math.max(1, Math.min(a, QuranData.count(s)));
        bismillah = withBismillah && ayah == 1 && QuranData.hasBismillah(s);
        getSharedPreferences("quran", MODE_PRIVATE).edit().putInt("s", surah).putInt("a", ayah).apply();
        if (am.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            playing = false;
            publish();
            return;
        }
        playCurrent();
    }

    void playCurrent() {
        File f = bismillah ? audioFile(this, 1, 1) : audioFile(this, surah, ayah);
        release();
        missing = !f.exists();
        if (missing) {
            playing = false;
            publish();
            return;
        }
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(attrs);
            player.setDataSource(f.getPath());
            player.setOnCompletionListener(this);
            player.prepare();
            player.start();
            playing = true;
        } catch (Exception e) {
            release();
            playing = false;
        }
        publish();
    }

    @Override public void onCompletion(MediaPlayer mp) {
        if (bismillah) {
            bismillah = false;
            playCurrent();
            return;
        }
        if (mode == MODE_REPEAT_AYAH) {
            playCurrent();
        } else if (ayah < QuranData.count(surah)) {
            start(surah, ayah + 1, false);
        } else if (mode == MODE_REPEAT_SURAH) {
            start(surah, 1, true);
        } else if (surah < QuranData.SURAH_COUNT) {
            start(surah + 1, 1, true);
        } else {
            pause();
        }
    }

    void step(int d) {
        int s = surah, a = ayah + d;
        if (a < 1) {
            if (s > 1) { s--; a = QuranData.count(s); } else a = 1;
        } else if (a > QuranData.count(s)) {
            if (s < QuranData.SURAH_COUNT) { s++; a = 1; } else a = QuranData.count(s);
        }
        start(s, a, a == 1);
    }

    void resume() {
        if (player != null && !playing) {
            if (am.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return;
            player.start();
            playing = true;
            publish();
        } else if (player == null) {
            start(surah, ayah, ayah == 1);
        }
    }

    void pause() {
        if (player != null && playing) player.pause();
        playing = false;
        publish();
    }

    void stopAll() {
        release();
        playing = false;
        am.abandonAudioFocusRequest(focus);
        stopForeground(true);
        stopSelf();
    }

    void release() {
        if (player != null) {
            player.release();
            player = null;
        }
    }

    @Override public void onAudioFocusChange(int change) {
        switch (change) {
            case AudioManager.AUDIOFOCUS_LOSS:
                resumeOnGain = false;
                pause();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                resumeOnGain = playing;
                pause();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                if (player != null) player.setVolume(0.3f, 0.3f);
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                if (player != null) player.setVolume(1f, 1f);
                if (resumeOnGain) resume();
                resumeOnGain = false;
                break;
        }
    }

    // ------------------------------------------------------------ status & notifikasi

    String title() {
        String name = QuranData.loaded() ? QuranData.get(surah).latin : "Surah " + surah;
        return bismillah ? name + " · Basmalah" : name + " · Ayat " + ayah;
    }

    void publish() {
        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE
                | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_STOP;
        session.setPlaybackState(new PlaybackState.Builder()
                .setActions(actions)
                .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED, 0, 1f)
                .build());
        session.setMetadata(new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title())
                .putString(MediaMetadata.METADATA_KEY_ARTIST, RECITER)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, "Al-Qur'an")
                .build());
        nm.notify(1, notification());
        if (!playing) stopForeground(false);
        notifyListeners();
    }

    Notification notification() {
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, QuranActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), 0);
        return new Notification.Builder(this, "quran")
                .setSmallIcon(R.drawable.ic_quran)
                .setContentTitle(title())
                .setContentText(missing ? "Audio belum tersedia di perangkat" : RECITER)
                .setContentIntent(open)
                .setOngoing(playing)
                .setShowWhen(false)
                .addAction(new Notification.Action.Builder(null, "Sebelumnya", pi(ACTION_PREV, 1)).build())
                .addAction(new Notification.Action.Builder(null, playing ? "Jeda" : "Putar", pi(ACTION_TOGGLE, 2)).build())
                .addAction(new Notification.Action.Builder(null, "Berikutnya", pi(ACTION_NEXT, 3)).build())
                .setStyle(new Notification.MediaStyle()
                        .setMediaSession(session.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2))
                .build();
    }

    PendingIntent pi(String action, int code) {
        return PendingIntent.getService(this, code, new Intent(this, QuranService.class).setAction(action), 0);
    }
}
