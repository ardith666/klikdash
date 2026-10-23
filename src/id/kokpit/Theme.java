package id.kokpit;

/** Sepuluh tema KlikDash. Semua warna/ukuran dibaca sekali saat activity dibuat. */
final class Theme {

    static final int FRAME_FLAT = 0, FRAME_PAPER = 1, FRAME_RAISED = 2,
            FRAME_BEVEL = 3, FRAME_GLASS = 4, FRAME_EXTRUDED = 5;

    static final class P {
        final int bg, panel, line, text, dim, faint, amber, ink, hot, activeBg, translation;

        P(int bg, int panel, int line, int text, int dim, int faint, int amber, int ink, int hot,
          int activeBg, int translation) {
            this.bg = bg;
            this.panel = panel;
            this.line = line;
            this.text = text;
            this.dim = dim;
            this.faint = faint;
            this.amber = amber;
            this.ink = ink;
            this.hot = hot;
            this.activeBg = activeBg;
            this.translation = translation;
        }
    }

    final int id, frame, panelAlpha, scrimDark, scrimLight;
    final String name, display, mono;
    final boolean cropMarks;
    final P dark, light;

    Theme(int id, String name, int frame, int panelAlpha, boolean cropMarks, String display, String mono,
          int scrimDark, int scrimLight, P dark, P light) {
        this.id = id;
        this.name = name;
        this.frame = frame;
        this.panelAlpha = panelAlpha;
        this.cropMarks = cropMarks;
        this.display = display;
        this.mono = mono;
        this.scrimDark = scrimDark;
        this.scrimLight = scrimLight;
        this.dark = dark;
        this.light = light;
    }

    static final Theme[] THEMES = {
            new Theme(1, "INSTRUMEN", FRAME_FLAT, 255, true, "display.ttf", "mono.ttf",
                    0x99000000, 0x55FFFFFF,
                    p(0xFF0B0B0A, 0xFF151513, 0xFF2B2A26, 0xFFECE6D8, 0xFF8A8478, 0xFF4A4740, 0xFFFFB000, 0xFF0B0B0A, 0xFFFF5A3C, 0xFF1A1710, 0xFFD9D3C5),
                    p(0xFFF3EEE4, 0xFFFBF7EF, 0xFFD9D0C1, 0xFF2A2722, 0xFF6D675C, 0xFFB8AF9F, 0xFF9C5A00, 0xFF1C1915, 0xFFB33A22, 0xFFE9E0D0, 0xFF4A443A)),
            new Theme(5, "SIRKUIT", FRAME_EXTRUDED, 245, true, "display.ttf", "mono.ttf",
                    0x99000000, 0x55FFFFFF,
                    p(0xFF080C14, 0xFF111A27, 0xFF233447, 0xFFE8F1FA, 0xFF8DA3B8, 0xFF4C6178, 0xFFFF8A3D, 0xFF080C14, 0xFFFF4D6D, 0xFF152336, 0xFFCBD9E6),
                    p(0xFFEAF0F6, 0xFFF7FAFD, 0xFFC5D3E0, 0xFF14202D, 0xFF566B80, 0xFFA4B4C4, 0xFFB34E00, 0xFF0D1824, 0xFFB3263E, 0xFFD8E4EF, 0xFF3E4E5E)),
            new Theme(10, "ARKADE", FRAME_EXTRUDED, 245, true, "display.ttf", "mono.ttf",
                    0x99000000, 0x55FFFFFF,
                    p(0xFF0B0712, 0xFF151022, 0xFF2E2445, 0xFFF3EDFF, 0xFFA79ABF, 0xFF5F5378, 0xFF00E5FF, 0xFF0B0712, 0xFFFF4FA3, 0xFF1B1330, 0xFFDDD3F0),
                    p(0xFFF2EEFA, 0xFFFCFAFF, 0xFFD8CFEA, 0xFF211A33, 0xFF6B5F82, 0xFFB4A8C9, 0xFF007C91, 0xFF120D1E, 0xFFB01E66, 0xFFE6DFF5, 0xFF4E4562))
    };

    static P p(int bg, int panel, int line, int text, int dim, int faint, int amber, int ink, int hot,
               int activeBg, int translation) {
        return new P(bg, panel, line, text, dim, faint, amber, ink, hot, activeBg, translation);
    }

    static Theme get(int id) {
        if (id < 1 || id > THEMES.length) return THEMES[0];
        return THEMES[id - 1];
    }

    P palette(boolean light) { return light ? this.light : dark; }

    int scrim(boolean light) { return light ? scrimLight : scrimDark; }

    static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }
}
