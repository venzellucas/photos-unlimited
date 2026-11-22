package app.photos.unlimited;

import android.content.Context;
import android.content.SharedPreferences;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A shape-only summary of what the library scan could not read (issues #1 and #2). It holds field
 * numbers, wire types, length buckets and numbers below 16, never names, dates, sizes, keys or
 * tokens. One summary per scan generation, added up across the runs of that scan.
 */
final class LibraryShape {
  static final int MAX_LABELS = 20, MAX_PAGES = 3, MAX_SKETCH = 1500;
  private static final String PREFS = "library_shape";

  /** One unreadable item: its fixed label and a sketch of the field that failed. */
  record Unknown(String label, String sketch) {}

  private String generation = "";
  /** stage letter -> pages read */
  private final Map<String, Integer> pages = new TreeMap<>();
  /** stage letter -> "field:wire" -> pages that had it */
  private final Map<String, Map<String, Integer>> roots = new TreeMap<>();
  /** label -> {count, first sketch} */
  private final Map<String, String[]> unknown = new LinkedHashMap<>();
  /** sketch of a skipped delta page -> count */
  private final Map<String, Integer> skipped = new LinkedHashMap<>();

  // ---- Sketches -------------------------------------------------------------------------------

  /** Field numbers and wire types of a message, merged per field, to the given depth. */
  static String sketch(byte[] data, int depth) {
    StringBuilder out = new StringBuilder();
    sketchInto(out, data, depth);
    return cap(out.toString());
  }

  private static void sketchInto(StringBuilder out, byte[] data, int depth) {
    List<Proto.Value> values;
    try {
      values = Proto.values(data);
    } catch (java.io.IOException e) {
      out.append('?');
      return;
    }
    Map<String, List<Proto.Value>> byField = new LinkedHashMap<>();
    for (Proto.Value v : values)
      byField.computeIfAbsent(v.field() + ":" + v.wire(), k -> new ArrayList<>()).add(v);
    boolean first = true;
    for (Map.Entry<String, List<Proto.Value>> e : byField.entrySet()) {
      if (!first) out.append(' ');
      first = false;
      out.append(e.getKey());
      if (e.getValue().size() > 1) out.append('x').append(e.getValue().size());
      value(out, e.getValue().get(0), depth);
      if (out.length() > MAX_SKETCH) return;
    }
  }

  /** One value's shape: a small number, or the kind and length bucket of its bytes. */
  static void value(StringBuilder out, Proto.Value v, int depth) {
    if (v.wire() == 0) {
      out.append('=').append(v.number() >= 0 && v.number() < 16 ? v.number().toString() : "n");
      return;
    }
    if (v.wire() != 2) return;
    byte[] b = v.bytes();
    String bucket = bucket(b.length);
    if (b.length == 0) out.append("(0)");
    else if (isText(b)) out.append("T(").append(bucket).append(')');
    else if (isMessage(b)) {
      out.append("M(").append(bucket).append(')');
      if (depth > 1) {
        out.append('{');
        sketchInto(out, b, depth - 1);
        out.append('}');
      }
    } else out.append("B(").append(bucket).append(')');
  }

  static String bucket(int length) {
    if (length == 0) return "0";
    if (length < 16) return "s";
    if (length < 256) return "m";
    if (length < 4096) return "l";
    return "x";
  }

  /**
   * Valid UTF-8 with no control characters other than tab and line breaks. Text is never opened
   * further. Text with line breaks can also parse as a message, whose field numbers would then
   * spell its bytes; reading it as text first prevents that.
   */
  static boolean isText(byte[] b) {
    try {
      String s =
          StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(b)).toString();
      for (int i = 0; i < s.length(); i++) {
        char c = s.charAt(i);
        if (c < 0x20 && c != '\t' && c != '\n' && c != '\r') return false;
      }
      return true;
    } catch (java.nio.charset.CharacterCodingException e) {
      return false;
    }
  }

  /** Parses fully, with small field numbers; random bytes rarely do both. */
  static boolean isMessage(byte[] b) {
    try {
      List<Proto.Value> values = Proto.values(b);
      if (values.isEmpty()) return false;
      for (Proto.Value v : values) if (v.field() > 1000) return false;
      return true;
    } catch (java.io.IOException e) {
      return false;
    }
  }

  /**
   * The occurrences of one field of a container, one level deep, at most three. The file name
   * field passes {@code open = false}: its kind and length only.
   */
  static String occurrences(byte[] container, int field, boolean open) {
    try {
      StringBuilder out = new StringBuilder();
      int n = 0;
      for (Proto.Value v : Proto.values(container)) {
        if (v.field() != field) continue;
        if (n++ >= 3) continue;
        if (out.length() > 0) out.append(' ');
        out.append(v.field()).append(':').append(v.wire());
        if (!open && v.wire() == 2) {
          byte[] b = v.bytes();
          out.append(isMessage(b) ? "M(" : "B(").append(bucket(b.length)).append(')');
        } else value(out, v, 2);
      }
      if (n > 3) out.append(" x").append(n);
      return cap(out.length() == 0 ? "-" : out.toString());
    } catch (java.io.IOException e) {
      return "?";
    }
  }

  /** The root's field list, "field:wire" in first-seen order, without values. */
  static List<String> fields(List<Proto.Value> top) {
    List<String> names = new ArrayList<>();
    for (Proto.Value v : top) {
      String name = v.field() + ":" + v.wire();
      if (!names.contains(name)) names.add(name);
    }
    return List.copyOf(names);
  }

  private static String cap(String s) {
    return s.length() > MAX_SKETCH ? s.substring(0, MAX_SKETCH) + "…" : s;
  }

  // ---- Summary --------------------------------------------------------------------------------

  void page(LibraryProtocol.Stage stage, LibraryProtocol.Page page) {
    String s = letter(stage);
    pages.merge(s, 1, Integer::sum);
    if (!page.rootFields().isEmpty()) {
      Map<String, Integer> seen =
          roots.computeIfAbsent(s, k -> new TreeMap<>(LibraryShape::byField));
      for (String f : page.rootFields()) seen.merge(f, 1, Integer::sum);
    }
    for (Unknown u : page.unknown()) {
      String label =
          unknown.containsKey(u.label()) || unknown.size() < MAX_LABELS ? u.label() : "OTHER";
      String[] entry = unknown.computeIfAbsent(label, k -> new String[] {"0", u.sketch()});
      entry[0] = Integer.toString(Integer.parseInt(entry[0]) + 1);
    }
  }

  /** Call only after the skipped page has been committed, like {@link #page}. */
  void skipped(LibraryProtocol.Stage stage, String sketch) {
    pages.merge(letter(stage), 1, Integer::sum);
    String key = sketch == null || sketch.isEmpty() ? "?" : sketch;
    if (skipped.containsKey(key) || skipped.size() < MAX_PAGES) skipped.merge(key, 1, Integer::sum);
    else skipped.merge("OTHER", 1, Integer::sum);
  }

  private static String letter(LibraryProtocol.Stage stage) {
    return String.valueOf(stage.name().charAt(0));
  }

  private static int byField(String a, String b) {
    int c = Integer.compare(number(a), number(b));
    return c != 0 ? c : a.compareTo(b);
  }

  private static int number(String fieldWire) {
    try {
      return Integer.parseInt(fieldWire.substring(0, fieldWire.indexOf(':')));
    } catch (RuntimeException e) {
      return Integer.MAX_VALUE;
    }
  }

  /** Stored form: one tab-separated record per line. Sketches never hold tabs or new lines. */
  String encode() {
    StringBuilder out = new StringBuilder("G\t").append(generation).append('\n');
    pages.forEach((s, n) -> out.append("P\t").append(s).append('\t').append(n).append('\n'));
    roots.forEach(
        (s, m) ->
            m.forEach(
                (f, n) ->
                    out.append("R\t").append(s).append('\t').append(f).append('\t').append(n).append('\n')));
    unknown.forEach(
        (l, e) ->
            out.append("U\t").append(l).append('\t').append(e[0]).append('\t').append(e[1]).append('\n'));
    skipped.forEach((k, n) -> out.append("N\t").append(n).append('\t').append(k).append('\n'));
    return out.toString();
  }

  static LibraryShape decode(String stored) {
    LibraryShape shape = new LibraryShape();
    if (stored == null) return shape;
    for (String line : stored.split("\n")) {
      String[] p = line.split("\t", -1);
      try {
        switch (p[0]) {
          case "G" -> shape.generation = p[1];
          case "P" -> shape.pages.put(p[1], Integer.parseInt(p[2]));
          case "R" ->
              shape
                  .roots
                  .computeIfAbsent(p[1], k -> new TreeMap<>(LibraryShape::byField))
                  .put(p[2], Integer.parseInt(p[3]));
          case "U" -> shape.unknown.put(p[1], new String[] {Integer.toString(Integer.parseInt(p[2])), p[3]});
          case "N" -> shape.skipped.put(p[2], Integer.parseInt(p[1]));
          default -> {}
        }
      } catch (RuntimeException ignored) {
        // A damaged line is dropped; the rest of the summary still reads.
      }
    }
    return shape;
  }

  String report() {
    StringBuilder out =
        new StringBuilder(
            "Library shape (field numbers, wire types and length buckets only; no names, dates,"
                + " sizes or keys)\nScan: "
                + (generation.length() > 8 ? generation.substring(0, 8) : generation));
    if (pages.isEmpty()) return out.append("\nNo scan recorded yet.").toString();
    out.append("\nPages:");
    pages.forEach((s, n) -> out.append(' ').append(s).append('=').append(n));
    out.append("\nRoot fields (pages that had each):");
    roots.forEach(
        (s, m) -> {
          out.append("\n  ").append(s).append(':');
          m.forEach((f, n) -> out.append(' ').append(f).append('x').append(n));
        });
    int total = 0;
    for (String[] e : unknown.values()) total += Integer.parseInt(e[0]);
    // A later delta pass re-sends changed items, so this counts occurrences, not items.
    out.append("\nUnreadable item occurrences: ").append(total);
    unknown.forEach(
        (l, e) -> out.append("\n  ").append(l).append(" x").append(e[0]).append(": ").append(e[1]));
    int pagesSkipped = 0;
    for (int n : skipped.values()) pagesSkipped += n;
    out.append("\nSkipped delta pages: ").append(pagesSkipped);
    skipped.forEach((k, n) -> out.append("\n  x").append(n).append(": ").append(k));
    return out.toString();
  }

  // ---- Storage --------------------------------------------------------------------------------

  /** The summary of this scan generation; a different generation starts empty. */
  static LibraryShape load(Context context, String generation) {
    LibraryShape shape = decode(prefs(context).getString("data", null));
    if (!shape.generation.equals(generation)) {
      shape = new LibraryShape();
      shape.generation = generation;
    }
    return shape;
  }

  /** Synchronous: the scan runs on a background thread, and a killed process keeps each page. */
  void save(Context context) {
    prefs(context).edit().putString("data", encode()).commit();
  }

  static String report(Context context) {
    return decode(prefs(context).getString("data", null)).report();
  }

  private static SharedPreferences prefs(Context context) {
    return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
  }
}
