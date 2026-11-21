package app.photos.unlimited;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Minimal protobuf wire codec. Opaque upload receipts are never decoded/re-encoded. */
public final class Proto {
  private final ByteArrayOutputStream out = new ByteArrayOutputStream();

  public Proto number(int field, long value) {
    varint(((long) field << 3));
    varint(value);
    return this;
  }

  public Proto bytes(int field, byte[] value) {
    varint(((long) field << 3) | 2);
    varint(value.length);
    out.write(value, 0, value.length);
    return this;
  }

  public Proto text(int field, String value) {
    return bytes(field, value.getBytes(StandardCharsets.UTF_8));
  }

  public Proto message(int field, Proto value) {
    return bytes(field, value.build());
  }

  public byte[] build() {
    return out.toByteArray();
  }

  private void varint(long value) {
    while ((value & ~127L) != 0) {
      out.write(((int) value & 127) | 128);
      value >>>= 7;
    }
    out.write((int) value);
  }

  /**
   * A decoding error that also says why (CUT, TAG, WIRE or DUP) and where (field 0 = unknown,
   * wire -1 = unknown), so a scan failure can name its cause. The message texts are unchanged.
   */
  public static final class Malformed extends IOException {
    final String reason;
    final int field;
    final int wire;

    Malformed(String message, String reason, int field, int wire) {
      super(message);
      this.reason = reason;
      this.field = field;
      this.wire = wire;
    }

    /** The same failure with the field filled in, unless one is already known. */
    Malformed at(int field) {
      return this.field != 0 ? this : new Malformed(getMessage(), reason, field, wire);
    }
  }

  /** Field numbers outside protobuf's range are recorded as unknown, never wrapped. */
  private static int fieldOf(long tag) {
    long field = tag >>> 3;
    return field >= 1 && field <= 536870911 ? (int) field : 0;
  }

  private static long read(byte[] data, int[] pos) throws IOException {
    long value = 0;
    for (int shift = 0; shift < 64; shift += 7) {
      if (pos[0] >= data.length)
        throw new Malformed("Incomplete protocol response", "CUT", 0, -1);
      int b = data[pos[0]++] & 255;
      if (shift == 63 && (b & 254) != 0)
        throw new Malformed("Invalid protocol integer", "TAG", 0, -1);
      value |= (long) (b & 127) << shift;
      if ((b & 128) == 0) return value;
    }
    throw new Malformed("Invalid protocol integer", "TAG", 0, -1);
  }

  public static List<byte[]> fields(byte[] data, int wanted) throws IOException {
    List<byte[]> result = new ArrayList<>();
    int[] p = {0};
    while (p[0] < data.length) {
      long tag = read(data, p);
      if ((tag >>> 3) == 0) throw new Malformed("Invalid protocol field", "TAG", 0, -1);
      int wire = (int) (tag & 7), field = fieldOf(tag);
      long count;
      try {
        switch (wire) {
          case 0:
            read(data, p);
            continue;
          case 1:
            count = 8;
            break;
          case 5:
            count = 4;
            break;
          case 2:
            count = read(data, p);
            break;
          default:
            throw new Malformed("Unsupported protocol field", "WIRE", field, wire);
        }
      } catch (Malformed m) {
        throw m.at(field);
      }
      if (count < 0 || count > data.length - p[0])
        throw new Malformed("Incomplete protocol response", "CUT", field, -1);
      int end = p[0] + (int) count;
      if (wire == 2 && (tag >>> 3) == wanted)
        result.add(java.util.Arrays.copyOfRange(data, p[0], end));
      p[0] = end;
    }
    return result;
  }

  public static String path(byte[] data, int... path) throws IOException {
    List<byte[]> values = List.of(data);
    for (int field : path) {
      List<byte[]> next = new ArrayList<>();
      for (byte[] value : values) next.addAll(fields(value, field));
      values = next;
    }
    for (byte[] value : values)
      if (value.length > 0) return new String(value, StandardCharsets.UTF_8);
    return "";
  }

  public record Value(int field, int wire, Long number, byte[] bytes) {}

  /** Presence is retained: a missing quota is not a numeric zero. */
  public static List<Value> values(byte[] data) throws IOException {
    List<Value> values = new ArrayList<>();
    int[] p = {0};
    while (p[0] < data.length) {
      long tag = read(data, p);
      if ((tag >>> 3) == 0 || (tag >>> 3) > 536870911)
        throw new Malformed("Invalid protocol field", "TAG", 0, -1);
      int wire = (int) (tag & 7), field = (int) (tag >>> 3);
      long n;
      try {
        if (wire == 0) {
          values.add(new Value(field, wire, read(data, p), null));
          continue;
        }
        n =
            switch (wire) {
              case 1 -> 8;
              case 5 -> 4;
              case 2 -> read(data, p);
              default -> throw new Malformed("Unsupported protocol field", "WIRE", field, wire);
            };
      } catch (Malformed m) {
        throw m.at(field);
      }
      if (n < 0 || n > data.length - p[0])
        throw new Malformed("Incomplete protocol response", "CUT", field, -1);
      values.add(
          new Value(field, wire, null, java.util.Arrays.copyOfRange(data, p[0], p[0] + (int) n)));
      p[0] += (int) n;
    }
    return values;
  }

  public static byte[] message(byte[] data, int field) throws IOException {
    byte[] found = null;
    for (Value v : values(data))
      if (v.field == field) {
        // The wire check runs first, so a wrong-type duplicate reports WIRE.
        if (v.wire != 2) throw new Malformed("Ambiguous protocol message", "WIRE", field, v.wire);
        if (found != null) throw new Malformed("Ambiguous protocol message", "DUP", field, -1);
        found = v.bytes;
      }
    return found == null ? new byte[0] : found;
  }

  public static Long integer(byte[] data, int field) throws IOException {
    Long found = null;
    for (Value v : values(data))
      if (v.field == field) {
        if (v.wire != 0) throw new Malformed("Ambiguous protocol integer", "WIRE", field, v.wire);
        if (found != null) throw new Malformed("Ambiguous protocol integer", "DUP", field, -1);
        found = v.number;
      }
    return found;
  }

  static byte[] without(byte[] data, int... fields) throws IOException {
    Proto result = new Proto();
    for (Value v : values(data)) {
      boolean skip = false;
      for (int field : fields) if (v.field == field) skip = true;
      if (skip) continue;
      if (v.wire == 0) result.number(v.field, v.number);
      else if (v.wire == 2) result.bytes(v.field, v.bytes);
      else {
        result.varint(((long) v.field << 3) | v.wire);
        result.out.writeBytes(v.bytes);
      }
    }
    return result.build();
  }
}
