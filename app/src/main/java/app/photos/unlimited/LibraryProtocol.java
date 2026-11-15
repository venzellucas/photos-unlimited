package app.photos.unlimited;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Read-only library sync, adapted from the pinned MIT GPMC parser. */
public final class LibraryProtocol {
  public record Item(
      String key,
      String name,
      int type,
      Long size,
      Long charged,
      long taken,
      boolean trash,
      boolean archived,
      int origin) {}

  public record Page(
      List<Item> items, List<String> deletions, byte[] resume, byte[] sync, boolean more) {}

  public enum Stage {
    NEW,
    INITIAL,
    SYNC,
    DELTA,
    DONE
  }

  public record Cursor(Stage stage, byte[] resume, byte[] sync, byte[] nextSync, boolean more) {
    public static Cursor fresh() {
      return new Cursor(Stage.NEW, new byte[0], new byte[0], new byte[0], false);
    }
  }

  static String text(byte[] data, int field) throws IOException {
    byte[] bytes = Proto.message(data, field);
    if (bytes.length > 16384) throw new IOException("Library text is too long");
    return StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
  }

  static Long nonnegative(byte[] data, int field) throws IOException {
    Long value = Proto.integer(data, field);
    return value == null || value < 0 ? null : value;
  }

  static long number(byte[] data, int field, long fallback) throws IOException {
    Long value = Proto.integer(data, field);
    return value == null ? fallback : value;
  }

  public static Page parse(byte[] response) throws IOException {
    byte[] root = Proto.message(response, 1);
    if (root.length == 0) throw new IOException("Unrecognized library response");
    boolean recognized = false;
    for (Proto.Value v : Proto.values(root)) {
      if (v.field() == 1 || v.field() == 2 || v.field() == 6 || v.field() == 7 || v.field() == 9) {
        recognized = true;
        if (v.wire() != (v.field() == 7 ? 0 : 2))
          throw new IOException("Unexpected library field type");
      }
    }
    if (!recognized) throw new IOException("Unrecognized library response");
    List<Item> items = new ArrayList<>();
    for (byte[] media : Proto.fields(root, 2)) {
      String key = text(media, 1);
      if (key.isBlank()) throw new IOException("Library item has no identifier");
      byte[] details = Proto.message(media, 2);
      if (details.length == 0) throw new IOException("Library item has no details");
      String name = text(details, 4);
      if (name.isBlank()) name = "Unnamed media";
      byte[] kind = Proto.message(media, 5);
      int type = (int) number(kind, 1, 0);
      // Use media envelopes as the fallback if the type enum is absent.
      if (type == 0) {
        if (Proto.message(kind, 3).length > 0) type = 2;
        else if (Proto.message(kind, 2).length > 0) type = 1;
      }
      Long charged = nonnegative(Proto.message(details, 35), 2);
      items.add(
          new Item(
              key,
              name,
              type,
              nonnegative(details, 10),
              charged,
              number(details, 7, 0),
              number(Proto.message(details, 16), 3, 0) > 0,
              number(Proto.message(details, 29), 1, 0) == 1,
              (int) number(Proto.message(details, 30), 1, 0)));
    }
    List<String> deleted = new ArrayList<>();
    for (byte[] deletion : Proto.fields(root, 9)) {
      byte[] d = Proto.message(deletion, 1);
      if (number(d, 1, 0) == 1) {
        String key = text(Proto.message(d, 2), 1);
        if (key.isBlank()) throw new IOException("Invalid library deletion");
        deleted.add(key);
      }
    }
    return new Page(
        List.copyOf(items),
        List.copyOf(deleted),
        Proto.message(root, 1),
        Proto.message(root, 6),
        number(root, 7, 0) == 2);
  }

  /**
   * Advance only after a whole page has been parsed; the database commits page and cursor together.
   */
  public static Cursor advance(Cursor before, Page page) throws IOException {
    if (before.stage == Stage.DONE) throw new IOException("Scan is already complete");
    if (page.resume.length > 0 && java.util.Arrays.equals(page.resume, before.resume))
      throw new IOException("Library pagination did not advance");
    byte[] nextSync = page.sync.length > 0 ? page.sync : before.nextSync;
    return switch (before.stage) {
      case NEW -> {
        if (page.sync.length == 0) throw new IOException("Library sync cursor is missing");
        yield new Cursor(
            page.resume.length > 0 ? Stage.INITIAL : Stage.SYNC,
            page.resume,
            page.sync,
            page.sync,
            false);
      }
      case INITIAL ->
          new Cursor(
              page.resume.length > 0 ? Stage.INITIAL : Stage.SYNC,
              page.resume,
              before.sync,
              before.nextSync,
              false);
      case SYNC -> {
        if (page.sync.length == 0) throw new IOException("Library sync cursor is missing");
        if (page.resume.length > 0)
          yield new Cursor(Stage.DELTA, page.resume, before.sync, page.sync, page.more);
        if (page.more && java.util.Arrays.equals(page.sync, before.sync))
          throw new IOException("Library sync did not advance");
        yield new Cursor(
            page.more ? Stage.SYNC : Stage.DONE, page.resume, page.sync, page.sync, page.more);
      }
      case DELTA -> {
        boolean more = before.more || page.more;
        if (page.resume.length > 0)
          yield new Cursor(Stage.DELTA, page.resume, before.sync, nextSync, more);
        if (more && java.util.Arrays.equals(nextSync, before.sync))
          throw new IOException("Library sync did not advance");
        yield new Cursor(more ? Stage.SYNC : Stage.DONE, page.resume, nextSync, nextSync, more);
      }
      case DONE -> throw new IOException("Scan is already complete");
    };
  }

  public static byte[] request(byte[] template, Cursor cursor) throws IOException {
    byte[] body = Proto.without(Proto.message(template, 1), 4, 6);
    Proto fields = new Proto();
    if (cursor.stage == Stage.NEW || cursor.stage == Stage.SYNC) fields.bytes(6, cursor.sync);
    else {
      fields.bytes(4, cursor.resume);
      if (cursor.stage == Stage.DELTA) fields.bytes(6, cursor.sync);
    }
    java.io.ByteArrayOutputStream merged = new java.io.ByteArrayOutputStream();
    merged.writeBytes(body);
    merged.writeBytes(fields.build());
    byte[] tail = Proto.without(template, 1);
    java.io.ByteArrayOutputStream request = new java.io.ByteArrayOutputStream();
    request.writeBytes(new Proto().bytes(1, merged.toByteArray()).build());
    request.writeBytes(tail);
    return request.toByteArray();
  }
}
