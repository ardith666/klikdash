package id.kokpit;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

/** Background tema dari /sdcard/KlikDash/themes/NN-gelap|terang.ext. */
final class ThemeImages {

    static final String[] EXT = {"jpg", "jpeg", "png", "webp"};

    private ThemeImages() { }

    static File folder(Context c) {
        File root = Environment.getExternalStorageDirectory();
        if (root == null) root = c.getFilesDir();
        File dir = new File(root, "KlikDash/themes");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    static String base(int id, boolean light) {
        return String.format(Locale.US, "%02d-%s", id, light ? "light" : "dark");
    }

    static File find(Context c, int id, boolean light) {
        File dir = folder(c);
        String base = base(id, light);
        for (String ext : EXT) {
            File f = new File(dir, base + "." + ext);
            if (f.isFile()) return f;
        }
        return null;
    }

    static File importImage(Context c, Uri uri, int id, boolean light) throws IOException {
        String ext = extension(c, uri);
        File dir = folder(c);
        String base = base(id, light);
        for (String old : EXT) {
            File f = new File(dir, base + "." + old);
            if (!old.equals(ext)) f.delete();
        }
        File target = new File(dir, base + "." + ext);
        InputStream in = c.getContentResolver().openInputStream(uri);
        if (in == null) throw new IOException("stream null");
        try (InputStream src = in; OutputStream out = new FileOutputStream(target)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = src.read(buf)) > 0) out.write(buf, 0, n);
        } catch (IOException e) {
            target.delete();
            throw e;
        }
        return target;
    }

    static Bitmap load(Context c, int id, boolean light) {
        File f = find(c, id, light);
        if (f == null) return null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

            int reqW = c.getResources().getDisplayMetrics().widthPixels;
            int reqH = c.getResources().getDisplayMetrics().heightPixels;
            int sample = 1;
            while (bounds.outWidth / sample > reqW * 2 || bounds.outHeight / sample > reqH * 2) sample *= 2;

            BitmapFactory.Options opt = new BitmapFactory.Options();
            opt.inSampleSize = sample;
            opt.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap b = BitmapFactory.decodeFile(f.getPath(), opt);
            if (b == null) return null;

            int rotate = rotation(f);
            if (rotate != 0) {
                Matrix m = new Matrix();
                m.postRotate(rotate);
                Bitmap rotated = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
                if (rotated != b) b.recycle();
                b = rotated;
            }
            return b;
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    static int rotation(File f) {
        try {
            int o = new ExifInterface(f.getAbsolutePath())
                    .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            if (o == ExifInterface.ORIENTATION_ROTATE_90) return 90;
            if (o == ExifInterface.ORIENTATION_ROTATE_180) return 180;
            if (o == ExifInterface.ORIENTATION_ROTATE_270) return 270;
        } catch (Exception ignored) { }
        return 0;
    }

    static String extension(Context c, Uri uri) {
        String mime = c.getContentResolver().getType(uri);
        if ("image/png".equals(mime)) return "png";
        if ("image/webp".equals(mime)) return "webp";
        if ("image/jpeg".equals(mime)) return "jpg";
        String name = uri.getLastPathSegment();
        if (name != null) {
            int dot = name.lastIndexOf('.');
            if (dot >= 0) {
                String ext = name.substring(dot + 1).toLowerCase(Locale.US);
                for (String ok : EXT) if (ok.equals(ext)) return ext;
            }
        }
        return "jpg";
    }
}
