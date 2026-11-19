package jp.mycarlog.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/** Pan / pinch-zoom a picture behind a fixed square window; result() returns that square. */
final class CropView extends View {
    private static final int OUTPUT = 256;

    private final Bitmap bm;
    private final Matrix m = new Matrix();
    private final RectF crop = new RectF();
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint dim = new Paint();
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path overlay = new Path();
    private final ScaleGestureDetector scaler;
    private final float[] vals = new float[9];
    private float minScale = 1f;
    private float lastX, lastY;
    private int lastCount;

    CropView(Context c, Bitmap bitmap) {
        super(c);
        bm = bitmap;
        dim.setColor(0xB0000000);
        line.setColor(Color.WHITE);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(2f * c.getResources().getDisplayMetrics().density);
        scaler = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector d) {
                m.getValues(vals);
                float cur = vals[Matrix.MSCALE_X];
                float target = Math.max(minScale, Math.min(minScale * 8f, cur * d.getScaleFactor()));
                float f = target / cur;
                m.postScale(f, f, d.getFocusX(), d.getFocusY());
                clamp();
                invalidate();
                return true;
            }
        });
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        float size = Math.min(w, h) * 0.9f;
        crop.set((w - size) / 2f, (h - size) / 2f, (w + size) / 2f, (h + size) / 2f);
        minScale = Math.max(size / bm.getWidth(), size / bm.getHeight());
        m.setScale(minScale, minScale);
        m.postTranslate(crop.centerX() - bm.getWidth() * minScale / 2f,
                crop.centerY() - bm.getHeight() * minScale / 2f);
        overlay.reset();
        overlay.setFillType(Path.FillType.EVEN_ODD);
        overlay.addRect(0, 0, w, h, Path.Direction.CW);
        overlay.addRect(crop, Path.Direction.CW);
    }

    /** Keep the picture covering the whole crop square. */
    private void clamp() {
        RectF r = new RectF(0, 0, bm.getWidth(), bm.getHeight());
        m.mapRect(r);
        float dx = 0, dy = 0;
        if (r.left > crop.left) dx = crop.left - r.left;
        else if (r.right < crop.right) dx = crop.right - r.right;
        if (r.top > crop.top) dy = crop.top - r.top;
        else if (r.bottom < crop.bottom) dy = crop.bottom - r.bottom;
        m.postTranslate(dx, dy);
    }

    @Override protected void onDraw(Canvas canvas) {
        canvas.drawBitmap(bm, m, bitmapPaint);
        canvas.drawPath(overlay, dim);
        canvas.drawRect(crop, line);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        getParent().requestDisallowInterceptTouchEvent(true);
        scaler.onTouchEvent(e);
        int count = e.getPointerCount();
        float x = 0, y = 0;
        for (int i = 0; i < count; i++) {
            x += e.getX(i);
            y += e.getY(i);
        }
        x /= count;
        y /= count;
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_MOVE && count == lastCount) {
            m.postTranslate(x - lastX, y - lastY);
            clamp();
            invalidate();
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) lastCount = 0;
        else lastCount = count;
        lastX = x;
        lastY = y;
        return true;
    }

    /** The picture inside the square window, as a 256 x 256 bitmap. */
    Bitmap result() {
        Bitmap out = Bitmap.createBitmap(OUTPUT, OUTPUT, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Matrix mm = new Matrix(m);
        mm.postTranslate(-crop.left, -crop.top);
        float k = OUTPUT / crop.width();
        mm.postScale(k, k);
        c.drawBitmap(bm, mm, bitmapPaint);
        return out;
    }
}
