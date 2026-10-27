package app.mapper;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.accessibilityservice.GestureDescription.StrokeDescription;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Path;
import android.graphics.RectF;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Keyboard to touch mapper. Battery: no polling, no timers, no animations. Work happens only on
 * app switches and on mapped key presses. The overlay redraws only when something changes.
 */
public class MapperService extends AccessibilityService {
  static final float TAP_R = 0.045f, JOY_R = 0.14f;
  static final long STEP_MS = 10;
  static final int ACCENT = 0xFFFFB300, WHITE = 0xFFFFFFFF, CARD = 0xE0202124, FILL = 0x8C000000;

  SharedPreferences prefs;
  WindowManager wm;
  Overlay view;
  Handle handle;
  WindowManager.LayoutParams lp, hlp;
  boolean attached, editing, hidden, paused;

  String pkg;
  CharSequence appName = "";
  ArrayList<Btn> btns = new ArrayList<>();
  final boolean[] held = new boolean[KeyEvent.getMaxKeyCode() + 1];
  final HashMap<String, Boolean> appCache = new HashMap<>();
  int gen;

  @Override protected void onServiceConnected() {
    prefs = getSharedPreferences("m", MODE_PRIVATE);
    wm = (WindowManager) getSystemService(WINDOW_SERVICE);
    hidden = prefs.getBoolean("hidden", false);
    view = new Overlay(this);
    lp = params(-1, -1);
    handle = new Handle(this);
    int size = (int) (40 * view.dp);
    hlp = params(size, size);
    hlp.gravity = Gravity.TOP | Gravity.START;
    hlp.x = prefs.getInt("hx", (int) (8 * view.dp));
    hlp.y = prefs.getInt("hy", (int) (120 * view.dp));
  }

  static WindowManager.LayoutParams params(int w, int h) {
    WindowManager.LayoutParams p = new WindowManager.LayoutParams(w, h,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT);
    p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
    p.setFitInsetsTypes(0);
    return p;
  }

  @Override public void onAccessibilityEvent(AccessibilityEvent e) {
    if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || e.getPackageName() == null) return;
    String p = e.getPackageName().toString();
    if (p.equals(pkg) || !isActivity(p, e.getClassName())) return; // popups, overlays and the keyboard keep the current app
    if (editing) finishEdit(true);
    releaseAll();
    pkg = p;
    paused = false;
    try {
      appName = getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(p, 0));
    } catch (PackageManager.NameNotFoundException ex) {
      appName = p;
    }
    if (isGame(p)) { btns = Btn.decode(prefs.getString("p:" + p, null)); show(); } else hide();
  }

  @Override public void onInterrupt() { releaseAll(); }

  @Override public void onDestroy() { hide(); super.onDestroy(); }

  /** Only a real activity coming to the front counts as an app switch. */
  boolean isActivity(String p, CharSequence cls) {
    if (cls == null) return false;
    String key = p + "/" + cls;
    Boolean r = appCache.get(key);
    if (r == null) {
      try {
        getPackageManager().getActivityInfo(new android.content.ComponentName(p, cls.toString()), 0);
        String ime = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        r = ime == null || !ime.startsWith(p + "/");
      } catch (PackageManager.NameNotFoundException ex) {
        r = false;
      }
      appCache.put(key, r);
    }
    return r;
  }

  boolean isGame(String p) {
    if (prefs.contains("p:" + p)) return true;
    try {
      ApplicationInfo ai = getPackageManager().getApplicationInfo(p, 0);
      if (ai.category == ApplicationInfo.CATEGORY_GAME || (ai.flags & ApplicationInfo.FLAG_IS_GAME) != 0) return true;
      Boolean r = engineCache.get(p);
      if (r == null) { r = usesGameEngine(ai); engineCache.put(p, r); }
      return r;
    } catch (PackageManager.NameNotFoundException ex) {
      return false;
    }
  }

  final HashMap<String, Boolean> engineCache = new HashMap<>();
  static final java.util.regex.Pattern ENGINE = java.util.regex.Pattern.compile(
      "lib/[^/]+/lib(unity|il2cpp|SDL2|UE4|Unreal|godot_android|cocos2d\\w*|cocos\\w*|gdx|fmod|hl)\\.so");

  /** Many games do not declare the game category. Their engine library gives them away. */
  static boolean usesGameEngine(ApplicationInfo ai) {
    ArrayList<String> apks = new ArrayList<>();
    apks.add(ai.sourceDir);
    if (ai.splitSourceDirs != null) java.util.Collections.addAll(apks, ai.splitSourceDirs);
    for (String apk : apks) {
      try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(apk)) {
        java.util.Enumeration<? extends java.util.zip.ZipEntry> e = z.entries();
        while (e.hasMoreElements()) if (ENGINE.matcher(e.nextElement().getName()).matches()) return true;
      } catch (java.io.IOException ignored) { }
    }
    return false;
  }

  // ---- keys ----

  @Override protected boolean onKeyEvent(KeyEvent e) {
    if (pkg == null || view == null) return false;
    int c = e.getKeyCode();
    boolean down = e.getAction() == KeyEvent.ACTION_DOWN, first = down && e.getRepeatCount() == 0;
    if (e.isCtrlPressed() && e.isAltPressed()
        && (c == KeyEvent.KEYCODE_K || c == KeyEvent.KEYCODE_H || c == KeyEvent.KEYCODE_P)) {
      if (first) hotkey(c);
      return true;
    }
    if (editing) {
      if (first) view.editKey(c);
      return true;
    }
    if (!attached || paused || c >= held.length || !mapped(c)) return false;
    if (e.getRepeatCount() > 0 || held[c] == down) return true;
    held[c] = down;
    inject(false);
    return true;
  }

  boolean mapped(int c) {
    for (Btn b : btns) if (b.uses(c)) return true;
    return false;
  }

  void hotkey(int c) {
    if (c == KeyEvent.KEYCODE_K) {
      if (editing) finishEdit(true); else startEdit();
    } else if (c == KeyEvent.KEYCODE_H) {
      hidden = !hidden;
      prefs.edit().putBoolean("hidden", hidden).apply();
      refresh();
    } else if (attached && !editing) {
      paused = !paused;
      releaseAll();
      refresh();
    }
  }

  // ---- overlay windows ----

  void show() {
    if (!attached) {
      wm.addView(view, lp);
      wm.addView(handle, hlp);
      attached = true;
    }
    refresh();
  }

  void hide() {
    if (attached) {
      wm.removeView(view);
      wm.removeView(handle);
      attached = false;
    }
  }

  /** Any app can be mapped. It is remembered as a game once saved. */
  void startEdit() {
    show();
    releaseAll();
    editing = true;
    paused = false;
    view.sel = null;
    refresh();
  }

  void finishEdit(boolean save) {
    editing = false;
    view.sel = null;
    if (save) prefs.edit().putString("p:" + pkg, Btn.encode(btns)).apply();
    else btns = Btn.decode(prefs.getString("p:" + pkg, null));
    if (!save && !isGame(pkg)) hide(); else refresh();
  }

  /** Idle: untouchable, markers at 50% or nothing. Editing: touchable, 80% over a light tint. */
  void refresh() {
    if (!attached) return;
    int f = lp.flags;
    lp.flags = editing ? f & ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        : f | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
    if (f != lp.flags) wm.updateViewLayout(view, lp);
    view.setAlpha(editing ? 0.8f : 0.5f);
    view.setVisibility(editing || !hidden || paused ? View.VISIBLE : View.INVISIBLE);
    handle.setVisibility(editing || hidden ? View.GONE : View.VISIBLE);
    view.invalidate();
    handle.invalidate();
  }

  // ---- touch injection ----

  void releaseAll() {
    java.util.Arrays.fill(held, false);
    inject(false);
  }

  /**
   * Sends one gesture holding every finger that should be down. Fingers already down are
   * continued, new ones start, released ones lift. A new gesture that skipped a finger that is
   * down would cancel it, so every live stroke is always included.
   */
  void inject(boolean retry) {
    int[] loc = new int[2];
    view.getLocationOnScreen(loc);
    float w = view.getWidth(), h = view.getHeight(), m = Math.min(w, h);
    GestureDescription.Builder g = new GestureDescription.Builder();
    int n = 0, max = GestureDescription.getMaxStrokeCount();
    for (Btn b : btns) {
      float[] o = n < max ? b.offset(held) : null;
      if (o == null && b.stroke == null) continue;
      float x = Math.max(0, loc[0] + w * b.x + o(o, 0) * b.r * m);
      float y = Math.max(0, loc[1] + h * b.y + o(o, 1) * b.r * m);
      Path p = new Path();
      StrokeDescription s;
      if (b.stroke == null) {
        p.moveTo(x, y);
        s = new StrokeDescription(p, 0, STEP_MS, true);
      } else {
        p.moveTo(b.sx, b.sy);
        if (o != null && (x != b.sx || y != b.sy)) p.lineTo(x, y);
        s = ((StrokeDescription) b.stroke).continueStroke(p, 0, STEP_MS, o != null);
      }
      g.addStroke(s);
      n++;
      b.stroke = o == null ? null : s;
      if (o != null) { b.sx = x; b.sy = y; }
    }
    if (n == 0) return;
    final int id = ++gen;
    boolean ok = dispatchGesture(g.build(), new GestureResultCallback() {
      @Override public void onCancelled(GestureDescription d) {
        // A real finger on the screen or a system gesture cut our fingers. Put held ones back once.
        if (id != gen) return;
        for (Btn b : btns) b.stroke = null;
        if (!retry) inject(true);
      }
    }, null);
    if (!ok) for (Btn b : btns) b.stroke = null;
  }

  private static float o(float[] o, int i) { return o == null ? 0 : o[i]; }

  /** Ctrl, Alt and Meta start hotkeys, so they cannot be bound. */
  static boolean reserved(int c) {
    return c == KeyEvent.KEYCODE_CTRL_LEFT || c == KeyEvent.KEYCODE_CTRL_RIGHT || c == KeyEvent.KEYCODE_ALT_LEFT
        || c == KeyEvent.KEYCODE_ALT_RIGHT || c == KeyEvent.KEYCODE_META_LEFT || c == KeyEvent.KEYCODE_META_RIGHT;
  }

  static String keyName(int k) {
    switch (k) {
      case 0: return "?";
      case KeyEvent.KEYCODE_SPACE: return "Space";
      case KeyEvent.KEYCODE_ENTER: return "Enter";
      case KeyEvent.KEYCODE_TAB: return "Tab";
      case KeyEvent.KEYCODE_SHIFT_LEFT: return "Shift";
      case KeyEvent.KEYCODE_SHIFT_RIGHT: return "R Shift";
      case KeyEvent.KEYCODE_CAPS_LOCK: return "Caps";
      case KeyEvent.KEYCODE_DPAD_UP: return "↑";
      case KeyEvent.KEYCODE_DPAD_DOWN: return "↓";
      case KeyEvent.KEYCODE_DPAD_LEFT: return "←";
      case KeyEvent.KEYCODE_DPAD_RIGHT: return "→";
      case KeyEvent.KEYCODE_GRAVE: return "`";
      case KeyEvent.KEYCODE_MINUS: return "-";
      case KeyEvent.KEYCODE_EQUALS: return "=";
      case KeyEvent.KEYCODE_COMMA: return ",";
      case KeyEvent.KEYCODE_PERIOD: return ".";
      case KeyEvent.KEYCODE_SLASH: return "/";
      case KeyEvent.KEYCODE_BACKSLASH: return "\\";
      case KeyEvent.KEYCODE_SEMICOLON: return ";";
      case KeyEvent.KEYCODE_APOSTROPHE: return "'";
      case KeyEvent.KEYCODE_PAGE_UP: return "PgUp";
      case KeyEvent.KEYCODE_PAGE_DOWN: return "PgDn";
      case KeyEvent.KEYCODE_MOVE_HOME: return "Home";
      case KeyEvent.KEYCODE_MOVE_END: return "End";
      case KeyEvent.KEYCODE_INSERT: return "Ins";
    }
    String s = KeyEvent.keyCodeToString(k).replace("KEYCODE_", "");
    if (s.startsWith("NUMPAD_")) return "Num " + s.substring(7).toLowerCase();
    return s.length() <= 3 ? s : s.charAt(0) + s.substring(1).toLowerCase().replace('_', ' ');
  }

  // ---- floating handle: tap to edit, drag to move ----

  final class Handle extends View {
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    float rawX, rawY;
    int startX, startY;
    boolean moved;

    Handle(Context c) {
      super(c);
      setAlpha(0.5f);
      setContentDescription("Edit key mapping");
    }

    @Override protected void onDraw(Canvas c) {
      float w = getWidth(), d = view.dp;
      p.setStyle(Paint.Style.FILL);
      p.setColor(CARD);
      c.drawCircle(w / 2, w / 2, w / 2, p);
      // keyboard glyph: outline with two rows of keys and a space bar
      p.setColor(paused ? ACCENT : WHITE);
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(1.5f * d);
      RectF r = new RectF(w / 2 - 11 * d, w / 2 - 7 * d, w / 2 + 11 * d, w / 2 + 7 * d);
      c.drawRoundRect(r, 3 * d, 3 * d, p);
      p.setStyle(Paint.Style.FILL);
      for (int row = 0; row < 2; row++)
        for (int i = 0; i < 4; i++)
          c.drawCircle(r.left + (4 + i * 4.7f) * d, r.top + (3.8f + row * 3.4f) * d, 1.1f * d, p);
      c.drawRect(r.left + 6 * d, r.bottom - 3.6f * d, r.right - 6 * d, r.bottom - 2.2f * d, p);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
      switch (e.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:
          rawX = e.getRawX(); rawY = e.getRawY(); startX = hlp.x; startY = hlp.y; moved = false;
          setAlpha(0.9f);
          return true;
        case MotionEvent.ACTION_MOVE:
          float dx = e.getRawX() - rawX, dy = e.getRawY() - rawY;
          if (Math.hypot(dx, dy) > 10 * view.dp) moved = true;
          if (moved) {
            hlp.x = startX + (int) dx;
            hlp.y = startY + (int) dy;
            wm.updateViewLayout(this, hlp);
          }
          return true;
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL:
          setAlpha(0.5f);
          if (moved) prefs.edit().putInt("hx", hlp.x).putInt("hy", hlp.y).apply();
          else if (e.getActionMasked() == MotionEvent.ACTION_UP) startEdit();
          return true;
      }
      return false;
    }
  }

  // ---- full screen overlay and editor ----

  final class Overlay extends View {
    static final int CANCEL = 0, DELETE = 1, SMALLER = 2, BIGGER = 3, SAVE = 4;
    final String[] barLabels = {"Cancel", "Delete", "Smaller", "Bigger", "Save"};
    final RectF[] bar = new RectF[5];
    final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG),
        text = new Paint(Paint.ANTI_ALIAS_FLAG), small = new Paint(Paint.ANTI_ALIAS_FLAG);
    final float dp;
    Btn sel, drag;
    int step, pressed = -1;
    float downX, downY;
    boolean moved, longFired;
    final Runnable longPress = () -> {
      longFired = true;
      add(Btn.JOY, downX, downY, JOY_R);
    };

    Overlay(Context c) {
      super(c);
      dp = c.getResources().getDisplayMetrics().density;
      for (int i = 0; i < bar.length; i++) bar[i] = new RectF();
      ring.setStyle(Paint.Style.STROKE);
      text.setTextAlign(Paint.Align.CENTER);
      text.setFakeBoldText(true);
      small.setTextAlign(Paint.Align.CENTER);
      small.setTextSize(12 * dp);
    }

    @Override protected void onDraw(Canvas c) {
      float w = getWidth(), h = getHeight();
      if (editing) c.drawColor(0x33000000);
      if (editing || !hidden) for (Btn b : btns) drawControl(c, b, w, h);
      if (editing) {
        drawTopCard(c, w);
        drawBar(c, w, h);
        if (sel != null) drawCallout(c, sel, w, h);
      } else if (paused) {
        pill(c, "Paused  •  Ctrl+Alt+P to resume", w / 2, 28 * dp, ACCENT, CARD);
      }
    }

    void drawControl(Canvas c, Btn b, float w, float h) {
      float x = b.x * w, y = b.y * h, r = b.r * Math.min(w, h);
      boolean on = b == sel;
      fill.setColor(FILL);
      ring.setColor(on ? ACCENT : WHITE);
      ring.setStrokeWidth((on ? 3 : 1.5f) * dp);
      c.drawCircle(x, y, r, fill);
      c.drawCircle(x, y, r, ring);
      if (b.type == Btn.TAP) {
        label(c, keyName(b.keys[0]), x, y, r * 1.5f, 15 * dp, on ? ACCENT : WHITE);
        return;
      }
      // joystick: thumb in the middle, one pill per direction
      fill.setColor(0x55FFFFFF);
      c.drawCircle(x, y, r * 0.28f, fill);
      float[][] at = {{0, -0.66f}, {-0.66f, 0}, {0, 0.66f}, {0.66f, 0}};
      for (int i = 0; i < 4; i++) {
        boolean asking = on && step == i;
        pill(c, keyName(b.keys[i]), x + at[i][0] * r, y + at[i][1] * r, asking ? 0xFF000000 : WHITE,
            asking ? ACCENT : 0xB0000000);
      }
    }

    /** Centered text that shrinks to fit maxW. */
    void label(Canvas c, String s, float x, float y, float maxW, float size, int color) {
      text.setTextSize(size);
      float tw = text.measureText(s);
      if (tw > maxW) text.setTextSize(size * maxW / tw);
      text.setColor(color);
      c.drawText(s, x, y - (text.ascent() + text.descent()) / 2, text);
    }

    RectF pill(Canvas c, String s, float x, float y, int fg, int bg) {
      text.setTextSize(13 * dp);
      float hw = text.measureText(s) / 2 + 10 * dp, hh = 13 * dp;
      RectF r = new RectF(x - hw, y - hh, x + hw, y + hh);
      fill.setColor(bg);
      c.drawRoundRect(r, hh, hh, fill);
      text.setColor(fg);
      c.drawText(s, x, y - (text.ascent() + text.descent()) / 2, text);
      return r;
    }

    void drawTopCard(Canvas c, float w) {
      String title = "Mapping " + appName;
      String hint = "Tap empty space to add a button  •  Hold to add a joystick  •  Drag to move  •  Tap a control to rebind";
      text.setTextSize(15 * dp);
      float tw = Math.max(text.measureText(title), small.measureText(hint));
      float hw = Math.min(tw / 2 + 16 * dp, w / 2 - 8 * dp);
      RectF r = new RectF(w / 2 - hw, 12 * dp, w / 2 + hw, 64 * dp);
      fill.setColor(CARD);
      c.drawRoundRect(r, 14 * dp, 14 * dp, fill);
      label(c, title, w / 2, 30 * dp, hw * 2 - 24 * dp, 15 * dp, WHITE);
      small.setColor(0xFFBDC1C6);
      small.setTextSize(12 * dp);
      float sw = small.measureText(hint);
      if (sw > hw * 2 - 24 * dp) small.setTextSize(12 * dp * (hw * 2 - 24 * dp) / sw);
      c.drawText(hint, w / 2, 52 * dp, small);
    }

    void drawBar(Canvas c, float w, float h) {
      float bw = 86 * dp, gap = 8 * dp, bh = 40 * dp;
      float total = bar.length * bw + (bar.length - 1) * gap, x = (w - total) / 2, y = h - bh - 20 * dp;
      RectF back = new RectF(x - 8 * dp, y - 8 * dp, x + total + 8 * dp, y + bh + 8 * dp);
      fill.setColor(CARD);
      c.drawRoundRect(back, 28 * dp, 28 * dp, fill);
      for (int i = 0; i < bar.length; i++) {
        RectF r = bar[i];
        r.set(x, y, x + bw, y + bh);
        x += bw + gap;
        boolean enabled = i == CANCEL || i == SAVE || sel != null;
        int bg = i == SAVE ? ACCENT : 0x33FFFFFF;
        if (i == pressed) bg = i == SAVE ? 0xFFFFCA28 : 0x55FFFFFF;
        fill.setColor(enabled ? bg : 0x14FFFFFF);
        c.drawRoundRect(r, bh / 2, bh / 2, fill);
        label(c, barLabels[i], r.centerX(), r.centerY(), bw - 12 * dp, 14 * dp,
            !enabled ? 0x66FFFFFF : i == SAVE ? 0xFF000000 : WHITE);
      }
    }

    void drawCallout(Canvas c, Btn b, float w, float h) {
      String s = b.type == Btn.TAP ? "Press a key" : "Press the " + Btn.DIRS[step].toLowerCase() + " key";
      float r = b.r * Math.min(w, h), y = b.y * h - r - 22 * dp;
      if (y < 90 * dp) y = b.y * h + r + 22 * dp;
      text.setTextSize(13 * dp);
      float hw = text.measureText(s) / 2 + 10 * dp;
      float x = Math.max(hw + 8 * dp, Math.min(w - hw - 8 * dp, b.x * w));
      pill(c, s, x, y, 0xFF000000, ACCENT);
    }

    Btn hit(float x, float y) {
      float w = getWidth(), h = getHeight(), m = Math.min(w, h);
      for (int i = btns.size() - 1; i >= 0; i--) {
        Btn b = btns.get(i);
        float dx = x - b.x * w, dy = y - b.y * h, r = Math.max(b.r * m, 24 * dp);
        if (dx * dx + dy * dy <= r * r) return b;
      }
      return null;
    }

    int barHit(float x, float y) {
      for (int i = 0; i < bar.length; i++) if (bar[i].contains(x, y)) return i;
      return -1;
    }

    void add(int type, float x, float y, float r) {
      Btn b = new Btn(type, x / getWidth(), y / getHeight(), r);
      btns.add(b);
      select(b);
    }

    void select(Btn b) {
      sel = b;
      step = 0;
      invalidate();
    }

    void action(int i) {
      if (i == CANCEL) finishEdit(false);
      else if (i == SAVE) finishEdit(true);
      else if (sel == null) return;
      else if (i == DELETE) { btns.remove(sel); select(null); }
      else resize(i == BIGGER);
    }

    void resize(boolean bigger) {
      sel.r = Math.max(0.02f, Math.min(0.4f, sel.r * (bigger ? 1.15f : 0.87f)));
      invalidate();
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
      if (!editing) return false;
      float x = e.getX(), y = e.getY();
      switch (e.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:
          downX = x; downY = y; moved = false; longFired = false;
          pressed = barHit(x, y);
          if (pressed >= 0) { invalidate(); return true; }
          drag = hit(x, y);
          if (drag == null) postDelayed(longPress, 450);
          return true;
        case MotionEvent.ACTION_MOVE:
          if (pressed >= 0) return true;
          if (Math.hypot(x - downX, y - downY) > 12 * dp) {
            moved = true;
            removeCallbacks(longPress);
          }
          if (drag != null && moved) {
            drag.x = clamp(x / getWidth());
            drag.y = clamp(y / getHeight());
            invalidate();
          }
          return true;
        case MotionEvent.ACTION_UP:
          removeCallbacks(longPress);
          if (pressed >= 0) {
            int p = pressed;
            pressed = -1;
            invalidate();
            if (barHit(x, y) == p) action(p);
          } else if (drag != null && !moved) select(drag);
          else if (drag == null && !moved && !longFired) add(Btn.TAP, x, y, TAP_R);
          drag = null;
          return true;
        case MotionEvent.ACTION_CANCEL:
          removeCallbacks(longPress);
          drag = null;
          pressed = -1;
          invalidate();
          return true;
      }
      return false;
    }

    void editKey(int c) {
      if (c == KeyEvent.KEYCODE_ESCAPE) {
        if (sel != null) select(null); else finishEdit(true);
      } else if (sel == null) {
        return;
      } else if (c == KeyEvent.KEYCODE_DEL || c == KeyEvent.KEYCODE_FORWARD_DEL) {
        action(DELETE);
      } else if (c == KeyEvent.KEYCODE_LEFT_BRACKET || c == KeyEvent.KEYCODE_RIGHT_BRACKET) {
        resize(c == KeyEvent.KEYCODE_RIGHT_BRACKET);
      } else if (!reserved(c)) {
        sel.keys[step++] = c;
        if (step >= sel.keyCount()) sel = null;
        invalidate();
      }
    }
  }

  static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }
}
