package app.voicekb.tinyasr;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Three steps to a working keyboard: enable it, allow the microphone, select it. */
public final class SetupActivity extends Activity {
    private static final int REQ_MIC = 1;
    private Button enable, permission, choose;
    private boolean askedOnce;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Prefs prefs = new Prefs(this);
        int pad = dp(20);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(pad, pad, pad, pad);
        col.setFitsSystemWindows(true);

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        col.addView(title);

        TextView intro = new TextView(this);
        intro.setText(R.string.setup_intro);
        intro.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        intro.setPadding(0, dp(8), 0, dp(16));
        col.addView(intro);

        enable = step(col);
        enable.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        permission = step(col);
        permission.setOnClickListener(v -> askMicrophone());
        choose = step(col);
        choose.setOnClickListener(v -> getSystemService(InputMethodManager.class).showInputMethodPicker());

        TextView langsTitle = new TextView(this);
        langsTitle.setText(R.string.setup_languages);
        langsTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        langsTitle.setPadding(0, dp(20), 0, dp(4));
        col.addView(langsTitle);

        int[] names = {R.string.lang_uk, R.string.lang_en, R.string.lang_ru};
        for (int i = 0; i < Prefs.LANGS.length; i++) {
            String lang = Prefs.LANGS[i];
            CheckBox box = new CheckBox(this);
            box.setText(names[i]);
            box.setChecked(prefs.langEnabled(lang));
            box.setOnCheckedChangeListener((b, checked) -> {
                // The last language stays on.
                if (!prefs.setLangEnabled(lang, checked)) b.setChecked(true);
            });
            col.addView(box);
        }

        CheckBox autoMode = new CheckBox(this);
        autoMode.setText(R.string.setup_auto_mode);
        autoMode.setChecked(prefs.autoMode());
        autoMode.setOnCheckedChangeListener((b, checked) -> prefs.setAutoMode(checked));
        col.addView(autoMode);

        CheckBox auto = new CheckBox(this);
        auto.setText(R.string.setup_autostart);
        auto.setChecked(prefs.autoStart());
        auto.setOnCheckedChangeListener((b, checked) -> prefs.setAutoStart(checked));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(12);
        col.addView(auto, lp);

        CheckBox back = new CheckBox(this);
        back.setText(R.string.setup_return);
        back.setChecked(prefs.returnAfter());
        back.setOnCheckedChangeListener((b, checked) -> prefs.setReturnAfter(checked));
        col.addView(back);

        EditText test = new EditText(this);
        test.setHint(R.string.setup_try);
        test.setMinLines(3);
        test.setGravity(android.view.Gravity.TOP);
        lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(12);
        col.addView(test, lp);

        TextView about = new TextView(this);
        about.setText(getString(R.string.setup_version, versionName()) + "\n\n" + getString(R.string.setup_about));
        about.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        about.setPadding(0, dp(16), 0, 0);
        col.addView(about);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(col);
        setContentView(scroll);
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "?";
        }
    }

    private Button step(LinearLayout parent) {
        Button b = new Button(this);
        b.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(6);
        parent.addView(b, lp);
        return b;
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    /** The keyboard picker is a dialog: its result shows when the focus comes back. */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) refresh();
    }

    private void refresh() {
        enable.setText(mark(isEnabled()) + getString(R.string.setup_enable));
        permission.setText(mark(hasMicrophone()) + getString(R.string.setup_permission));
        choose.setText(mark(isSelected()) + getString(R.string.setup_choose));
        choose.setEnabled(isEnabled());
    }

    private static String mark(boolean done) {
        return done ? "✓  " : "";
    }

    private boolean isEnabled() {
        for (InputMethodInfo i : getSystemService(InputMethodManager.class).getEnabledInputMethodList()) {
            if (getPackageName().equals(i.getPackageName())) return true;
        }
        return false;
    }

    private boolean isSelected() {
        String id = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        return id != null && id.startsWith(getPackageName() + "/");
    }

    private boolean hasMicrophone() {
        return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void askMicrophone() {
        if (hasMicrophone()) return;
        if (askedOnce && !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            // Denied for good: only the app's settings page can change it.
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", getPackageName(), null)));
            return;
        }
        askedOnce = true;
        requestPermissions(new String[] {Manifest.permission.RECORD_AUDIO}, REQ_MIC);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        refresh();
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
