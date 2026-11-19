package app.voicekb.tinyasr;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Round microphone button with a halo that follows the input level. */
final class MicButton extends View {
    static final int IDLE = 0, BUSY = 1, LISTENING = 2, DISABLED = 3;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private int mode = IDLE;
    private float level;
    private int accent = 0xFF1A73E8, live = 0xFFD93025, muted = 0xFF8A8F98, glyph = 0xFFFFFFFF;

    MicButton(Context c) {
        super(c);
        setClickable(true);
        setFocusable(true);
    }

    void setColors(int accent, int live, int muted, int glyph) {
        this.accent = accent;
        this.live = live;
        this.muted = muted;
        this.glyph = glyph;
        invalidate();
    }

    void setMode(int mode) {
        if (this.mode != mode) {
            this.mode = mode;
            if (mode != LISTENING) level = 0;
            invalidate();
        }
    }

    void setLevel(float l) {
        // Fast attack, slow release.
        level = l > level ? l : level * 0.8f + l * 0.2f;
        if (mode == LISTENING) invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float max = Math.min(cx, cy);
        float r = max * 0.72f;
        int color = mode == LISTENING ? live : mode == IDLE ? accent : muted;

        if (mode == LISTENING) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor((color & 0x00FFFFFF) | 0x40000000);
            c.drawCircle(cx, cy, r + (max - r) * level, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        c.drawCircle(cx, cy, r, paint);

        paint.setColor(glyph);
        if (mode == LISTENING) {
            // Stop square.
            float s = r * 0.36f;
            rect.set(cx - s, cy - s, cx + s, cy + s);
            c.drawRoundRect(rect, s * 0.25f, s * 0.25f, paint);
            return;
        }
        // Microphone: capsule, cradle, stem, base.
        float w = r * 0.20f, h = r * 0.34f;
        float top = cy - r * 0.50f;
        rect.set(cx - w, top, cx + w, top + 2 * h + w);
        c.drawRoundRect(rect, w, w, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(r * 0.08f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        float cr = r * 0.36f;
        float cyc = top + 2 * h + w - cr * 0.55f;
        rect.set(cx - cr, cyc - cr, cx + cr, cyc + cr);
        c.drawArc(rect, 0, 180, false, paint);
        float stemTop = cyc + cr, stemBottom = stemTop + r * 0.16f;
        c.drawLine(cx, stemTop, cx, stemBottom, paint);
        c.drawLine(cx - r * 0.18f, stemBottom, cx + r * 0.18f, stemBottom, paint);
    }
}
