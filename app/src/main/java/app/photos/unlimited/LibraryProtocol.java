package app.photos.unlimited;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Read-only library sync, adapted from the pinned MIT GPMC parser. */
public final class LibraryProtocol {
  /** Library data, not interface text: the name is cached and can name a re-uploaded file. */
  static final String UNNAMED = "Unnamed media";

  public record Item(
      String key,
      String name,
      int type,
      Long size,
      Long charged,
      long taken,
      boolean trash,
      boolean archived,
      int origin,
      Extra extra) {
    public Item(
        String key,
        String name,
        int type,
        Long size,
        Long charged,
        long taken,
        boolean trash,
        boolean archived,
        int origin) {
      this(key, name, type, size, charged, taken, trash, archived, origin, Extra.NONE);
    }
  }

  /** How Google stores the bytes: the original, a reduced copy (Storage saver), or unreported. */
  public enum Quality {
    UNKNOWN,
    ORIGINAL,
    REDUCED
  }

  /**
   * Fields needed to reclaim storage. Every value is optional: an unexpected shape leaves the
   * value empty and makes the item ineligible instead of failing the whole scan.
   */
  public record Extra(
      String dedup,
      byte[] sha1,
      String caption,
      boolean favorite,
      boolean edited,
      boolean canonical,
      boolean locked,
      boolean motion,
      Quality quality) {
    public static final Extra NONE =
        new Extra("", null, "", false, false, true, false, false, Quality.UNKNOWN);

    public Extra(
        String dedup,
        byte[] sha1,
        String caption,
        boolean favorite,
        boolean edited,
        boolean canonical,
        boolean locked,
        boolean motion) {
      this(dedup, sha1, caption, favorite, edited, canonical, locked, motion, Quality.UNKNOWN);
    }

    public boolean hashKnown() {
      return sha1 != null && sha1.length == 20;
    }
  }

  public record Page(
      List<Item> items, List<String> deletions, byte[] resume, byte[] sync, boolean more) {}

  /** A page with no items, deletions or cursors: what a delta page with no known fields means. */
  static final Page EMPTY = new Page(List.of(), List.of(), new byte[0], new byte[0], false);

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
    if (bytes.length > 16384) throw new ScanFailure("SCAN_ITEM_TEXT_LONG", "Library text is too long");
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

  /** Any decoding error that is not already labelled is a page-level format problem. */
  public static Page parse(byte[] response) throws IOException {
    try {
      return parsePage(response);
    } catch (ScanFailure e) {
      throw e;
    } catch (java.io.InterruptedIOException e) {
      throw e;
    } catch (IOException e) {
      throw new ScanFailure("SCAN_FORMAT_PAGE", e.getMessage());
    }
  }

  /**
   * A page-level label: SCAN_FORMAT_PAGE_<STEP>_<REASON>[_F<field>][_W<wire>]. ENV decodes the
   * whole outer response, so its field numbers are fields of that response, not of the root.
   * ROOT decodes the root, SHAPE checks its field types, END reads the cursor fields.
   */
  private static ScanFailure page(String step, IOException e) {
    if (e instanceof ScanFailure failure) return failure;
    if (!(e instanceof Proto.Malformed m)) return new ScanFailure("SCAN_FORMAT_PAGE", e.getMessage());
    String label = "SCAN_FORMAT_PAGE_" + step + "_" + m.reason;
    if (m.field > 0) label += "_F" + m.field;
    if ("WIRE".equals(m.reason) && m.wire >= 0) label += "_W" + m.wire;
    return new ScanFailure(label, m.getMessage());
  }

  /** Adds the scan stage's first letter to a page-level label; other failures are unchanged. */
  static ScanFailure withStage(ScanFailure e, Stage stage) {
    if (!e.label().startsWith("SCAN_FORMAT_PAGE")) return e;
    ScanFailure staged = new ScanFailure(e.label() + "_" + stage.name().charAt(0), e.getMessage());
    staged.initCause(e);
    return staged;
  }

  /*
   * Strictness is deliberate. The page structure, item envelopes, item keys and deletions stay
   * strict: a lost deletion or key could make reclaim trust a cloud copy that is gone. Any other
   * field of an item body in an unexpected shape (including its file name) only makes that item
   * "unknown" (no storage value, no hash), which ReclaimEngine.ineligible always refuses. The one
   * exception is an item with no details at all, which is still SCAN_ITEM_INVALID.
   */
  private static Page parsePage(byte[] response) throws IOException {
    byte[] root;
    try {
      root = Proto.message(response, 1);
    } catch (IOException e) {
      throw page("ENV", e);
    }
    if (root.length == 0)
      throw new ScanFailure("SCAN_FORMAT_PAGE_ENV_NONE", "Unrecognized library response");
    List<Proto.Value> top;
    List<byte[]> mediaList;
    List<byte[]> deletions;
    try {
      top = Proto.values(root);
      mediaList = Proto.fields(root, 2);
      deletions = Proto.fields(root, 9);
    } catch (IOException e) {
      throw page("ROOT", e);
    }
    boolean recognized = false;
    int lowest = 0;
    for (Proto.Value v : top) {
      if (lowest == 0 || v.field() < lowest) lowest = v.field();
      if (v.field() == 1 || v.field() == 2 || v.field() == 6 || v.field() == 7 || v.field() == 9) {
        recognized = true;
        if (v.wire() != (v.field() == 7 ? 0 : 2))
          throw new ScanFailure(
              "SCAN_FORMAT_PAGE_SHAPE_WIRE_F" + v.field() + "_W" + v.wire(),
              "Unexpected library field type");
      }
    }
    if (!recognized)
      // Names the root's lowest field (always 1 or more here: the root isn't empty and
      // Proto.values rejects field 0). LibrarySync reads this as an empty page in DELTA only.
      throw new ScanFailure(
          "SCAN_FORMAT_PAGE_SHAPE_NONE_F" + lowest, "Unrecognized library response");
    List<Item> items = new ArrayList<>();
    for (byte[] media : mediaList) {
      try {
        Proto.values(media);
      } catch (IOException e) {
        // Without a decodable envelope there is no trustworthy key, so this stays strict.
        throw new ScanFailure("SCAN_FORMAT_ITEM", e.getMessage());
      }
      String key;
      try {
        key = text(media, 1);
      } catch (ScanFailure e) {
        throw e;
      } catch (IOException e) {
        throw new ScanFailure("SCAN_FORMAT_KEY", e.getMessage());
      }
      if (key.isBlank()) throw new ScanFailure("SCAN_ITEM_INVALID", "Library item has no identifier");
      Item item;
      try {
        item = item(key, media);
      } catch (ScanFailure e) {
        // An item with no details at all is malformed, not merely unusual.
        if ("SCAN_ITEM_INVALID".equals(e.label())) throw e;
        item = unknown(key);
      } catch (java.io.InterruptedIOException e) {
        throw e;
      } catch (IOException e) {
        item = unknown(key);
      }
      items.add(item);
    }
    List<String> deleted = new ArrayList<>();
    for (byte[] deletion : deletions) {
      String key;
      try {
        byte[] d = Proto.message(deletion, 1);
        if (number(d, 1, 0) != 1) continue;
        key = text(Proto.message(d, 2), 1);
      } catch (ScanFailure e) {
        throw e;
      } catch (IOException e) {
        throw new ScanFailure("SCAN_FORMAT_DELETION", e.getMessage());
      }
      if (key.isBlank()) throw new ScanFailure("SCAN_ITEM_INVALID", "Invalid library deletion");
      deleted.add(key);
    }
    byte[] resume;
    byte[] sync;
    boolean more;
    try {
      resume = Proto.message(root, 1);
      sync = Proto.message(root, 6);
      more = number(root, 7, 0) == 2;
    } catch (IOException e) {
      throw page("END", e);
    }
    return new Page(List.copyOf(items), List.copyOf(deleted), resume, sync, more);
  }

  /**
   * Reads one item's body. A decoding error here makes the item unknown, not the page; only
   * missing details throw SCAN_ITEM_INVALID, which the caller rethrows.
   */
  private static Item item(String key, byte[] media) throws IOException {
    byte[] details = Proto.message(media, 2);
    if (details.length == 0) throw new ScanFailure("SCAN_ITEM_INVALID", "Library item has no details");
    // Strict: the name is also the re-upload name, so a damaged one must never be freed.
    String name = text(details, 4);
    if (name.isBlank()) name = UNNAMED;
    byte[] kind = Proto.message(media, 5);
    int type = (int) number(kind, 1, 0);
    // Use media envelopes as the fallback if the type enum is absent.
    if (type == 0) {
      if (Proto.message(kind, 3).length > 0) type = 2;
      else if (Proto.message(kind, 2).length > 0) type = 1;
    }
    Long charged = nonnegative(Proto.message(details, 35), 2);
    return new Item(
        key,
        name,
        type,
        nonnegative(details, 10),
        charged,
        number(details, 7, 0),
        number(Proto.message(details, 16), 3, 0) > 0,
        number(Proto.message(details, 29), 1, 0) == 1,
        (int) number(Proto.message(details, 30), 1, 0),
        extra(details, kind));
  }

  /** An item whose body could not be read: kept so the key stays known, but never eligible. */
  static Item unknown(String key) {
    return new Item(key, UNNAMED, 0, null, null, 0, false, false, 0, Extra.NONE);
  }


  static Extra extra(byte[] details, byte[] kind) {
    byte[] sha1 = null;
    try {
      byte[] h = Proto.message(Proto.message(details, 13), 1);
      if (h.length == 20) sha1 = h;
    } catch (IOException ignored) {
    }
    String dedup = lenientText(details, 21, 1);
    if (!dedup.matches("[A-Za-z0-9_-]{8,200}")) dedup = "";
    if (dedup.isEmpty() && sha1 != null)
      dedup =
          java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(sha1);
    boolean canonical = true;
    try {
      for (byte[] prop : Proto.fields(details, 5)) {
        Long kindValue = Proto.integer(prop, 1);
        if (kindValue != null && kindValue == 27) canonical = false;
      }
    } catch (IOException e) {
      canonical = false;
    }
    return new Extra(
        dedup,
        sha1,
        cleanCaption(lenientText(details, 3)),
        lenientFlag(details, 31),
        lenientPresent(kind, 2, 4),
        canonical,
        lenientFlag(details, 39),
        // Motion/Live Photos carry a field-5 envelope for their video (live-verified
        // 2026-11-19); upstream GPMC's 5.5.2.4 sub-path is not sent by the server.
        lenientPresent(kind, 5),
        quality(details));
  }

  static Quality quality(byte[] details) {
    try {
      Long value = Proto.integer(Proto.message(details, 35), 3);
      if (value == null) return Quality.UNKNOWN;
      return value == 2 ? Quality.ORIGINAL : Quality.REDUCED;
    } catch (IOException e) {
      return Quality.UNKNOWN;
    }
  }

  private static String lenientText(byte[] data, int... path) {
    try {
      byte[] value = data;
      for (int field : path) value = Proto.message(value, field);
      if (value.length == 0 || value.length > 16384) return "";
      return StandardCharsets.UTF_8
          .newDecoder()
          .decode(java.nio.ByteBuffer.wrap(value))
          .toString();
    } catch (Exception e) {
      return "";
    }
  }

  /** A nested message misread as text would contain control bytes; never restore that. */
  static String cleanCaption(String text) {
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c < 0x20 && c != '\n' && c != '\t') return "";
      if (c == 0xfffd) return "";
    }
    return text.length() > 4096 ? "" : text;
  }

  private static boolean lenientFlag(byte[] data, int field) {
    try {
      Long value = Proto.integer(Proto.message(data, field), 1);
      return value != null && value == 1;
    } catch (IOException e) {
      return false;
    }
  }

  private static boolean lenientPresent(byte[] data, int... path) {
    try {
      byte[] value = data;
      for (int field : path) {
        boolean found = false;
        for (Proto.Value v : Proto.values(value))
          if (v.field() == field && v.wire() == 2) found = true;
        if (!found) return false;
        value = Proto.message(value, field);
      }
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  /**
   * Advance only after a whole page has been parsed; the database commits page and cursor together.
   */
  public static Cursor advance(Cursor before, Page page) throws IOException {
    if (before.stage == Stage.DONE) throw new ScanFailure("SCAN_CURSOR", "Scan is already complete");
    if (page.resume.length > 0 && java.util.Arrays.equals(page.resume, before.resume))
      throw new ScanFailure("SCAN_CURSOR", "Library pagination did not advance");
    byte[] nextSync = page.sync.length > 0 ? page.sync : before.nextSync;
    return switch (before.stage) {
      case NEW -> {
        if (page.sync.length == 0) throw new ScanFailure("SCAN_CURSOR", "Library sync cursor is missing");
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
        if (page.sync.length == 0) throw new ScanFailure("SCAN_CURSOR", "Library sync cursor is missing");
        if (page.resume.length > 0)
          yield new Cursor(Stage.DELTA, page.resume, before.sync, page.sync, page.more);
        if (page.more && java.util.Arrays.equals(page.sync, before.sync))
          throw new ScanFailure("SCAN_CURSOR", "Library sync did not advance");
        yield new Cursor(
            page.more ? Stage.SYNC : Stage.DONE, page.resume, page.sync, page.sync, page.more);
      }
      case DELTA -> {
        boolean more = before.more || page.more;
        if (page.resume.length > 0)
          yield new Cursor(Stage.DELTA, page.resume, before.sync, nextSync, more);
        if (more && java.util.Arrays.equals(nextSync, before.sync))
          throw new ScanFailure("SCAN_CURSOR", "Library sync did not advance");
        yield new Cursor(more ? Stage.SYNC : Stage.DONE, page.resume, nextSync, nextSync, more);
      }
      case DONE -> throw new ScanFailure("SCAN_CURSOR", "Scan is already complete");
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
