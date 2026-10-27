package app.mapper;

import java.util.ArrayList;
import java.util.List;

/** One mapped control. x, y are fractions of the screen; r is a fraction of the short side. */
final class Btn {
  static final int TAP = 0, JOY = 1;
  static final String[] DIRS = {"UP", "LEFT", "DOWN", "RIGHT"};

  int type;
  float x, y, r;
  final int[] keys = new int[4]; // TAP uses keys[0]. JOY uses up, left, down, right.

  Object stroke; // last GestureDescription.StrokeDescription while the finger is down
  float sx, sy;  // where that stroke ended, in screen pixels

  Btn(int type, float x, float y, float r) {
    this.type = type; this.x = x; this.y = y; this.r = r;
  }

  int keyCount() { return type == TAP ? 1 : 4; }

  boolean uses(int key) {
    for (int i = 0; i < keyCount(); i++) if (keys[i] == key) return true;
    return false;
  }

  /** Finger offset in -1..1 units, or null when the finger should be up. */
  float[] offset(boolean[] held) {
    boolean any = false;
    for (int i = 0; i < keyCount(); i++) any |= h(held, keys[i]);
    if (!any) return null;
    if (type == TAP) return new float[] {0, 0};
    float dx = (h(held, keys[3]) ? 1 : 0) - (h(held, keys[1]) ? 1 : 0);
    float dy = (h(held, keys[2]) ? 1 : 0) - (h(held, keys[0]) ? 1 : 0);
    if (dx != 0 && dy != 0) { dx *= 0.7071f; dy *= 0.7071f; }
    return new float[] {dx, dy};
  }

  private static boolean h(boolean[] held, int k) { return k > 0 && k < held.length && held[k]; }

  static String encode(List<Btn> l) {
    StringBuilder s = new StringBuilder();
    for (Btn b : l) {
      s.append(b.type).append(',').append(b.x).append(',').append(b.y).append(',').append(b.r);
      for (int k : b.keys) s.append(',').append(k);
      s.append(';');
    }
    return s.toString();
  }

  static ArrayList<Btn> decode(String s) {
    ArrayList<Btn> l = new ArrayList<>();
    if (s == null) return l;
    for (String row : s.split(";")) {
      String[] f = row.split(",");
      if (f.length != 8) continue;
      try {
        Btn b = new Btn(Integer.parseInt(f[0]), Float.parseFloat(f[1]), Float.parseFloat(f[2]), Float.parseFloat(f[3]));
        for (int i = 0; i < 4; i++) b.keys[i] = Integer.parseInt(f[4 + i]);
        l.add(b);
      } catch (NumberFormatException ignored) { }
    }
    return l;
  }
}
