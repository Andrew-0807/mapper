package app.mapper;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Map;

public class MainActivity extends Activity {
  static final String[][] SHORTCUTS = {
    {"Ctrl+Alt+K", "Edit the mapping for the current app. Works in any app."},
    {"Ctrl+Alt+H", "Hide or show the markers. Keys keep working while hidden."},
    {"Ctrl+Alt+P", "Pause mapping, for typing in game chat."},
  };
  static final String[][] EDITING = {
    {"Tap empty space", "Add a button, then press its key."},
    {"Hold empty space", "Add a joystick, then press up, left, down and right."},
    {"Drag a control", "Move it."},
    {"Tap a control", "Select it and rebind its keys."},
    {"Del", "Remove the selected control."},
    {"[  ]", "Make the selected control smaller or bigger."},
    {"Esc", "Deselect. With nothing selected, save and exit."},
  };

  float dp;
  LinearLayout root, games;
  TextView status, statusDot;
  Button open;
  SharedPreferences prefs;

  @Override protected void onCreate(Bundle b) {
    super.onCreate(b);
    dp = getResources().getDisplayMetrics().density;
    prefs = getSharedPreferences("m", MODE_PRIVATE);
    root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(px(20), px(52), px(20), px(28));

    TextView title = text("Mapper", 30, true);
    root.addView(title);
    TextView sub = text("Play touch games with your keyboard.", 15, false);
    sub.setAlpha(0.7f);
    sub.setPadding(0, px(2), 0, px(16));
    root.addView(sub);

    LinearLayout sc = card();
    LinearLayout row = new LinearLayout(this);
    row.setGravity(Gravity.CENTER_VERTICAL);
    statusDot = new TextView(this);
    GradientDrawable dot = new GradientDrawable();
    dot.setShape(GradientDrawable.OVAL);
    statusDot.setBackground(dot);
    row.addView(statusDot, new LinearLayout.LayoutParams(px(12), px(12)));
    status = text("", 17, true);
    status.setPadding(px(10), 0, 0, 0);
    row.addView(status);
    sc.addView(row);
    TextView why = text("Mapper uses accessibility to read keys and tap the screen for you. "
        + "It sees no screen content and only runs while a game is open.", 14, false);
    why.setAlpha(0.7f);
    why.setPadding(0, px(8), 0, px(8));
    sc.addView(why);
    open = new Button(this);
    open.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
    sc.addView(open);

    section("Shortcuts");
    LinearLayout k = card();
    for (String[] s : SHORTCUTS) k.addView(shortcut(s[0], s[1]));
    TextView note = text("The overlay opens by itself in games. Tap the floating keyboard bubble to edit. "
        + "Unmapped keys go to the game as normal. Ctrl, Alt and Meta cannot be bound.", 13, false);
    note.setAlpha(0.6f);
    note.setPadding(0, px(8), 0, 0);
    k.addView(note);

    section("While editing");
    LinearLayout ed = card();
    for (String[] s : EDITING) ed.addView(shortcut(s[0], s[1]));

    section("Mapped games");
    games = card();

    if (getActionBar() != null) getActionBar().hide();
    ScrollView sv = new ScrollView(this);
    android.widget.FrameLayout.LayoutParams flp = new android.widget.FrameLayout.LayoutParams(
        Math.min(getResources().getDisplayMetrics().widthPixels, px(760)), -2, Gravity.CENTER_HORIZONTAL);
    sv.addView(root, flp);
    setContentView(sv);
  }

  @Override protected void onResume() {
    super.onResume();
    String on = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
    boolean enabled = on != null && on.contains(getPackageName() + "/");
    status.setText(enabled ? "Mapper is on" : "Mapper is off");
    ((GradientDrawable) statusDot.getBackground()).setColor(enabled ? 0xFF34A853 : 0xFFEA4335);
    open.setText(enabled ? "Accessibility settings" : "Turn on in accessibility settings");
    open.setBackgroundTintList(android.content.res.ColorStateList.valueOf(enabled ? 0x33888888 : 0xFFFFB300));
    open.setTextColor(enabled ? status.getCurrentTextColor() : 0xFF000000);
    listGames();
  }

  void listGames() {
    games.removeAllViews();
    PackageManager pm = getPackageManager();
    for (Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
      if (!e.getKey().startsWith("p:")) continue;
      String pkg = e.getKey().substring(2);
      CharSequence name = pkg;
      try { name = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)); } catch (PackageManager.NameNotFoundException ignored) { }
      int n = Btn.decode((String) e.getValue()).size();
      LinearLayout row = new LinearLayout(this);
      row.setGravity(Gravity.CENTER_VERTICAL);
      row.setPadding(0, px(4), 0, px(4));
      LinearLayout col = new LinearLayout(this);
      col.setOrientation(LinearLayout.VERTICAL);
      col.addView(text(name.toString(), 16, true));
      TextView count = text(n + (n == 1 ? " control" : " controls"), 13, false);
      count.setAlpha(0.6f);
      col.addView(count);
      row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
      Button reset = new Button(this, null, android.R.attr.borderlessButtonStyle);
      reset.setText("Remove");
      final CharSequence label = name;
      reset.setOnClickListener(v -> new AlertDialog.Builder(this)
          .setTitle("Remove mapping for " + label + "?")
          .setMessage("All its buttons and joysticks are deleted.")
          .setNegativeButton("Keep", null)
          .setPositiveButton("Remove", (d, w) -> { prefs.edit().remove("p:" + pkg).apply(); listGames(); })
          .show());
      row.addView(reset);
      games.addView(row);
    }
    if (games.getChildCount() == 0) {
      TextView t = text("No games mapped yet. Open a game and press Ctrl+Alt+K.", 14, false);
      t.setAlpha(0.6f);
      games.addView(t);
    }
  }

  View shortcut(String key, String what) {
    LinearLayout row = new LinearLayout(this);
    row.setPadding(0, px(6), 0, px(6));
    row.setGravity(Gravity.CENTER_VERTICAL);
    TextView k = text(key, 14, true);
    k.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
    k.setGravity(Gravity.CENTER);
    k.setPadding(px(8), px(4), px(8), px(4));
    GradientDrawable bg = new GradientDrawable();
    bg.setCornerRadius(px(6));
    bg.setColor(0x1F888888);
    k.setBackground(bg);
    row.addView(k, new LinearLayout.LayoutParams(px(176), -2));
    TextView w = text(what, 14, false);
    w.setPadding(px(12), 0, 0, 0);
    row.addView(w, new LinearLayout.LayoutParams(0, -2, 1));
    return row;
  }

  void section(String s) {
    TextView t = text(s.toUpperCase(), 12, true);
    t.setLetterSpacing(0.08f);
    t.setAlpha(0.6f);
    t.setPadding(px(4), px(20), 0, px(8));
    root.addView(t);
  }

  LinearLayout card() {
    LinearLayout c = new LinearLayout(this);
    c.setOrientation(LinearLayout.VERTICAL);
    c.setPadding(px(16), px(14), px(16), px(14));
    GradientDrawable bg = new GradientDrawable();
    bg.setCornerRadius(px(16));
    bg.setColor(0x14888888);
    c.setBackground(bg);
    root.addView(c);
    return c;
  }

  TextView text(String s, int sp, boolean bold) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextSize(sp);
    if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
    return t;
  }

  int px(float v) { return (int) (v * dp); }
}
