package app.voicekb.tinyasr;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A voice-only input method: a microphone, and the few keys needed to fix up what was said. */
public final class VoiceIme extends InputMethodService implements VoiceEngine.Listener {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private VoiceEngine engine;
    private Prefs prefs;

    private TextView status;
    private TextView langKey;
    private MicButton mic;

    private VoiceEngine.State state = VoiceEngine.State.IDLE;
    /** True while our composing text (the partial) is in the editor. */
    private boolean composing;
    /** The space put before the current composing text, if any. */
    private String composingLead = "";
    private String error;
    /** When we last changed the editor's text (uptime ms). */
    private long lastEdit;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new Prefs(this);
        engine = VoiceEngine.get(this);
        engine.preload();
    }

    // ---- view ----

    @Override
    public View onCreateInputView() {
        boolean night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        int bg = night ? 0xFF1B1D21 : 0xFFECEFF3;
        int key = night ? 0xFF2E3238 : 0xFFFFFFFF;
        int text = night ? 0xFFE8EAED : 0xFF202124;
        int hint = night ? 0xFF9AA0A6 : 0xFF5F6368;
        int accent = night ? 0xFF8AB4F8 : 0xFF1A73E8;
        int live = night ? 0xFFF28B82 : 0xFFD93025;
        int glyph = night ? 0xFF1B1D21 : 0xFFFFFFFF;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        int pad = dp(6);
        root.setPadding(pad, pad, pad, pad);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int bottom = Build.VERSION.SDK_INT >= 30
                    ? insets.getInsets(WindowInsets.Type.systemBars()).bottom
                    : insets.getSystemWindowInsetBottom();
            v.setPadding(pad, pad, pad, pad + bottom);
            return insets;
        });

        status = new TextView(this);
        status.setTextColor(hint);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        status.setGravity(Gravity.CENTER);
        status.setSingleLine(true);
        status.setEllipsize(TextUtils.TruncateAt.END);
        status.setPadding(0, dp(2), 0, dp(4));
        status.setOnClickListener(v -> {
            if (!hasMicPermission()) openSetup();
        });
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout top = row();
        root.addView(top, new LinearLayout.LayoutParams(-1, dp(84)));
        TextView switchKey = key("⌨", key, text, 22);
        switchKey.setContentDescription(getString(R.string.key_switch));
        switchKey.setOnClickListener(v -> switchKeyboard());
        switchKey.setOnLongClickListener(v -> {
            getSystemService(InputMethodManager.class).showInputMethodPicker();
            return true;
        });
        top.addView(switchKey, cell(1));

        langKey = key("", key, accent, 15);
        langKey.setTypeface(Typeface.DEFAULT_BOLD);
        langKey.setContentDescription(getString(R.string.key_language));
        langKey.setOnClickListener(v -> cycleLanguage());
        top.addView(langKey, cell(1));

        mic = new MicButton(this);
        mic.setColors(accent, live, hint, glyph);
        mic.setContentDescription(getString(R.string.key_mic));
        mic.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            toggle();
        });
        top.addView(mic, cell(1.6f));

        TextView back = key("⌫", key, text, 22);
        back.setContentDescription(getString(R.string.key_backspace));
        setRepeating(back, this::backspace);
        top.addView(back, cell(1));

        TextView enter = key("⏎", key, text, 22);
        enter.setContentDescription(getString(R.string.key_enter));
        enter.setOnClickListener(v -> enter());
        top.addView(enter, cell(1));

        LinearLayout bottom = row();
        root.addView(bottom, new LinearLayout.LayoutParams(-1, dp(52)));
        for (String p : new String[] {",", "."}) bottom.addView(punctuation(p, key, text), cell(1));
        TextView space = key("␣", key, hint, 20);
        space.setContentDescription(getString(R.string.key_space));
        space.setOnClickListener(v -> type(" "));
        bottom.addView(space, cell(3.6f));
        for (String p : new String[] {"?", "!"}) bottom.addView(punctuation(p, key, text), cell(1));

        refresh();
        return root;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        return r;
    }

    private LinearLayout.LayoutParams cell(float weight) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, weight);
        int m = dp(3);
        lp.setMargins(m, m, m, m);
        return lp;
    }

    private TextView key(String label, int bg, int fg, int sp) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(fg);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setGravity(Gravity.CENTER);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(bg);
        shape.setCornerRadius(dp(8));
        t.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33808080), shape, null));
        t.setClickable(true);
        t.setFocusable(true);
        return t;
    }

    private TextView punctuation(String p, int bg, int fg) {
        TextView t = key(p, bg, fg, 20);
        t.setOnClickListener(v -> type(p));
        return t;
    }

    /** Runs the action on press, then repeatedly while held. */
    private void setRepeating(View v, Runnable action) {
        Runnable[] repeat = new Runnable[1];
        repeat[0] = () -> {
            action.run();
            handler.postDelayed(repeat[0], 60);
        };
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.setPressed(true);
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    action.run();
                    handler.postDelayed(repeat[0], 400);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.setPressed(false);
                    handler.removeCallbacks(repeat[0]);
                    return true;
                default:
                    return true;
            }
        });
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // ---- lifecycle ----

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        if (!engine.isRunning()) {
            composing = false;
            error = null;
            state = VoiceEngine.State.IDLE;
        }
        refresh();
        if (!restarting && prefs.autoStart() && hasMicPermission() && !engine.isRunning()) startListening();
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        abort();
        super.onFinishInputView(finishingInput);
    }

    @Override
    public void onFinishInput() {
        abort();
        super.onFinishInput();
    }

    @Override
    public void onDestroy() {
        abort();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    /** Never go fullscreen in landscape: the text being dictated should stay visible. */
    @Override
    public boolean onEvaluateFullscreenMode() {
        return false;
    }

    /** Drops the recording; what is on screen stays as plain text. */
    private void abort() {
        if (engine.isRunning()) engine.cancel();
        if (composing) {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) ic.finishComposingText();
            composing = false;
        }
        state = VoiceEngine.State.IDLE;
        refresh();
    }

    // ---- keys ----

    private void toggle() {
        if (!hasMicPermission()) {
            openSetup();
            return;
        }
        if (engine.isRunning()) {
            if (state == VoiceEngine.State.LISTENING) engine.stop();
            else if (state == VoiceEngine.State.LOADING) abort();
        } else {
            startListening();
        }
    }

    private void startListening() {
        error = null;
        composing = false;
        state = VoiceEngine.State.LOADING;
        refresh();
        String mode = prefs.mode();
        engine.start(mode, prefs.startLang(mode), this);
    }

    private void cycleLanguage() {
        prefs.setMode(prefs.nextMode());
        boolean wasListening = engine.isRunning();
        if (wasListening) abort();
        refresh();
        if (wasListening) startListening();
    }

    private void switchKeyboard() {
        abort();
        if (!switchToPreviousInputMethod()) {
            getSystemService(InputMethodManager.class).showInputMethodPicker();
        }
    }

    private void backspace() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        if (composing) abort();
        CharSequence sel = ic.getSelectedText(0);
        if (!TextUtils.isEmpty(sel)) {
            ic.commitText("", 1);
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
        }
    }

    private void enter() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        abort();
        EditorInfo info = getCurrentInputEditorInfo();
        int action = info == null ? EditorInfo.IME_ACTION_NONE : info.imeOptions & EditorInfo.IME_MASK_ACTION;
        boolean noAction = info == null || (info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0;
        if (!noAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action);
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
        }
    }

    /** Types a character from the key row: punctuation sticks to the word before it. */
    private void type(String s) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        boolean resume = settle();
        ic.beginBatchEdit();
        if (!" ".equals(s)) {
            CharSequence before = ic.getTextBeforeCursor(1, 0);
            if (before != null && before.length() == 1 && before.charAt(0) == ' ') ic.deleteSurroundingText(1, 0);
        }
        ic.commitText(s, 1);
        ic.endBatchEdit();
        if (resume) startListening();
    }

    /**
     * Before a key edits the text while a partial is on screen: the partial becomes plain text and the recording
     * ends, because the recognizer would otherwise write the same sentence again. Returns true if the caller should
     * start listening again after its edit.
     */
    private boolean settle() {
        if (!composing) return false;
        boolean listening = engine.isRunning() && state == VoiceEngine.State.LISTENING;
        abort();
        return listening;
    }

    /** The editor dropped our composing text (the user tapped somewhere else): continue at the new cursor. */
    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd, int candidatesStart,
                                  int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd);
        // Reports of our own edits arrive a few ms after them; those are not the user's.
        if (composing && candidatesStart < 0 && SystemClock.uptimeMillis() - lastEdit > 120) {
            if (settle()) startListening();
        }
    }

    // ---- recognizer events (main thread) ----

    @Override
    public void onState(VoiceEngine.State s) {
        state = s;
        refresh();
    }

    @Override
    public void onCommit(String text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.beginBatchEdit();
        String lead = composing ? composingLead : leadFor(ic, text);
        ic.commitText(lead + text, 1);
        composing = false;
        ic.endBatchEdit();
        lastEdit = SystemClock.uptimeMillis();
    }

    @Override
    public void onPartial(String text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.beginBatchEdit();
        if (text.isEmpty()) {
            if (composing) ic.commitText("", 1);
            composing = false;
        } else {
            if (!composing) composingLead = leadFor(ic, text);
            ic.setComposingText(composingLead + text, 1);
            composing = true;
        }
        ic.endBatchEdit();
        lastEdit = SystemClock.uptimeMillis();
    }

    /** A space before new words, unless the text before the cursor already separates them. */
    private static String leadFor(InputConnection ic, String text) {
        CharSequence before = ic.getTextBeforeCursor(1, 0);
        if (before == null || before.length() == 0) return "";
        char c = before.charAt(0);
        if (Character.isWhitespace(c) || "([{«„“\"'/-".indexOf(c) >= 0) return "";
        if (!text.isEmpty() && ".,!?:;)".indexOf(text.charAt(0)) >= 0) return "";
        return " ";
    }

    @Override
    public void onLanguage(String lang) {
        prefs.setLastLang(lang);
    }

    @Override
    public void onLevel(float level) {
        if (mic != null) mic.setLevel(level);
    }

    @Override
    public void onError(String message) {
        error = message;
        refresh();
    }

    @Override
    public void onDone() {
        InputConnection ic = getCurrentInputConnection();
        if (composing && ic != null) ic.finishComposingText();
        composing = false;
        state = VoiceEngine.State.IDLE;
        refresh();
    }

    // ---- status ----

    private boolean hasMicPermission() {
        return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void openSetup() {
        startActivity(new Intent(this, SetupActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void refresh() {
        if (status == null) return;
        langKey.setText(Prefs.label(prefs.mode()));
        langKey.setEnabled(prefs.modes().size() > 1);
        if (!hasMicPermission()) {
            status.setText(R.string.status_permission);
            mic.setMode(MicButton.DISABLED);
            return;
        }
        switch (state) {
            case LOADING:
                status.setText(R.string.status_loading);
                mic.setMode(MicButton.BUSY);
                break;
            case LISTENING:
                status.setText(R.string.status_listening);
                mic.setMode(MicButton.LISTENING);
                break;
            case FINISHING:
                status.setText(R.string.status_finishing);
                mic.setMode(MicButton.BUSY);
                break;
            default:
                if (error != null) {
                    status.setText(getString(R.string.status_error, error));
                } else {
                    status.setText(R.string.status_idle);
                }
                mic.setMode(MicButton.IDLE);
                break;
        }
    }
}
