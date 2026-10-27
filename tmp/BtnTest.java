// Run: javac -d build/test src/app/mapper/Btn.java tmp/BtnTest.java && java -ea -cp build/test app.mapper.BtnTest
package app.mapper;

import java.util.List;

public class BtnTest {
  public static void main(String[] a) {
    Btn t = new Btn(Btn.TAP, 0.5f, 0.25f, 0.05f); t.keys[0] = 62;
    Btn j = new Btn(Btn.JOY, 0.2f, 0.7f, 0.14f); j.keys[0] = 51; j.keys[1] = 29; j.keys[2] = 47; j.keys[3] = 32;
    List<Btn> l = Btn.decode(Btn.encode(List.of(t, j)));
    assert l.size() == 2 && l.get(1).type == Btn.JOY && l.get(1).keys[3] == 32 && l.get(0).y == 0.25f;
    assert Btn.decode("garbage;1,2;").isEmpty();
    boolean[] h = new boolean[300];
    assert j.offset(h) == null && t.offset(h) == null;
    h[51] = true; // W
    float[] o = j.offset(h);
    assert o[0] == 0 && o[1] == -1;
    h[32] = true; // W+D diagonal, normalized
    o = j.offset(h);
    assert Math.abs(o[0] - 0.7071f) < 1e-4 && Math.abs(o[1] + 0.7071f) < 1e-4;
    h[47] = true; h[32] = false; // W+S cancel, finger stays down at center
    o = j.offset(h);
    assert o != null && o[0] == 0 && o[1] == 0;
    h[62] = true;
    assert t.offset(h) != null && t.uses(62) && !t.uses(51);
    System.out.println("BtnTest ok");
  }
}
