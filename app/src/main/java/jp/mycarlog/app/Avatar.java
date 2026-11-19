package jp.mycarlog.app;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** Round vehicle icons. The photo is stored in the vehicle record as a small base64 JPEG. */
final class Avatar {
    private static final int STORED_SIZE = 256;

    /** Preset icons offered when a vehicle has no photo. */
    static final String[] ICON_IDS = {"sedan", "hatch", "sports", "wagon", "suv", "van", "truck", "kei"};
    private static final int[] ICON_RES = {R.drawable.ic_car_sedan, R.drawable.ic_car_hatch,
            R.drawable.ic_car_sports, R.drawable.ic_car_wagon, R.drawable.ic_car_suv,
            R.drawable.ic_car_van, R.drawable.ic_car_truck, R.drawable.ic_car_kei};

    static int iconRes(String id) {
        for (int i = 0; i < ICON_IDS.length; i++) if (ICON_IDS[i].equals(id)) return ICON_RES[i];
        return 0;
    }

    private Avatar() { }

    /** Reads an image (EXIF rotation applied, long side at most about 1600 px); null on failure. */
    static Bitmap load(ContentResolver cr, Uri uri) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream is = cr.openInputStream(uri)) {
                BitmapFactory.decodeStream(is, null, o);
            }
            int longSide = Math.max(o.outWidth, o.outHeight);
            if (longSide <= 0) return null;
            o.inJustDecodeBounds = false;
            o.inSampleSize = Math.max(1, longSide / 1600);
            Bitmap bm;
            try (InputStream is = cr.openInputStream(uri)) {
                bm = BitmapFactory.decodeStream(is, null, o);
            }
            if (bm == null) return null;

            int rotation = 0;
            try (InputStream is = cr.openInputStream(uri)) {
                int ori = new ExifInterface(is).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                if (ori == ExifInterface.ORIENTATION_ROTATE_90) rotation = 90;
                else if (ori == ExifInterface.ORIENTATION_ROTATE_180) rotation = 180;
                else if (ori == ExifInterface.ORIENTATION_ROTATE_270) rotation = 270;
            } catch (Exception ignored) { }
            if (rotation != 0) {
                Matrix m = new Matrix();
                m.postRotate(rotation);
                bm = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), m, true);
            }
            return bm;
        } catch (Exception e) {
            return null;
        }
    }

    /** JPEG + base64 of an already square bitmap. */
    static String toBase64(Bitmap square) {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        square.compress(Bitmap.CompressFormat.JPEG, 85, bo);
        return Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP);
    }

    static Bitmap decode(String b64) {
        if (b64 == null || b64.isEmpty()) return null;
        try {
            byte[] raw = Base64.decode(b64, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(raw, 0, raw.length);
        } catch (Exception e) {
            return null;
        }
    }

    /** A round icon: the photo, or the first letter of the name when there is none. */
    static View view(Context c, String name, String photo, String icon, int sizeDp, boolean ring,
                     AppTheme.Pal pal) {
        final float d = c.getResources().getDisplayMetrics().density;
        int size = (int) (sizeDp * d);
        int inset = (int) (4 * d);

        FrameLayout frame = new FrameLayout(c);
        frame.setLayoutParams(new android.widget.LinearLayout.LayoutParams(size, size));
        GradientDrawable border = new GradientDrawable();
        border.setShape(GradientDrawable.OVAL);
        border.setColor(0x00000000);
        border.setStroke((int) (3 * d), ring ? pal.accent : 0x00000000);
        frame.setBackground(border);

        View inner;
        Bitmap bm = decode(photo);
        if (bm != null) {
            ImageView iv = new ImageView(c);
            iv.setImageBitmap(bm);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            inner = iv;
        } else if (iconRes(icon) != 0) {
            ImageView iv = new ImageView(c);
            iv.setImageResource(iconRes(icon));
            iv.setColorFilter(pal.accent);
            int pad = (int) (sizeDp * 0.07f * d);
            iv.setPadding(pad, pad, pad, pad);
            iv.setBackgroundColor(pal.tonal());
            inner = iv;
        } else {
            TextView t = new TextView(c);
            t.setText(name == null || name.isEmpty() ? "?" : name.substring(0, name.offsetByCodePoints(0, 1)));
            t.setGravity(Gravity.CENTER);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            t.setTextSize(sizeDp * 0.36f);
            t.setTextColor(pal.accent);
            t.setBackgroundColor(pal.tonal());
            inner = t;
        }
        inner.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setOval(0, 0, v.getWidth(), v.getHeight());
            }
        });
        inner.setClipToOutline(true);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size - 2 * inset, size - 2 * inset);
        lp.setMargins(inset, inset, inset, inset);
        frame.addView(inner, lp);
        return frame;
    }
}
