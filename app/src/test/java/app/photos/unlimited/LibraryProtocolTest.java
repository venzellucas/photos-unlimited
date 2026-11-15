package app.photos.unlimited;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.List;
import org.junit.Test;

public class LibraryProtocolTest {
  private static Proto item(String key, Long charged) {
    Proto details = new Proto().text(4, "photo.jpg").number(10, 500).number(7, 123);
    if (charged != null) details.message(35, new Proto().number(2, charged));
    return new Proto().text(1, key).message(2, details).message(5, new Proto().number(1, 1));
  }

  private static LibraryProtocol.Page page(String resume, String sync, boolean more) {
    return new LibraryProtocol.Page(List.of(), List.of(), resume.getBytes(), sync.getBytes(), more);
  }

  @Test
  public void absentQuotaRemainsUnknownAndExplicitZeroIsRetained() throws Exception {
    byte[] response =
        new Proto()
            .message(
                1,
                new Proto()
                    .text(6, "sync")
                    .message(2, item("a", null))
                    .message(2, item("b", 0L))
                    .message(2, item("c", 123L)))
            .build();
    var p = LibraryProtocol.parse(response);
    assertNull(p.items().get(0).charged());
    assertEquals(Long.valueOf(0), p.items().get(1).charged());
    assertEquals(Long.valueOf(123), p.items().get(2).charged());
  }

  @Test
  public void negativeQuotaIsUnknown() throws Exception {
    var p =
        LibraryProtocol.parse(
            new Proto().message(1, new Proto().text(6, "sync").message(2, item("a", -1L))).build());
    assertNull(p.items().get(0).charged());
  }

  @Test
  public void deletionsAndFlagsAreReadWithoutMutatingRemoteData() throws Exception {
    Proto media = item("a", 42L);
    byte[] response =
        new Proto()
            .message(
                1,
                new Proto()
                    .text(6, "sync")
                    .message(2, media)
                    .message(
                        9,
                        new Proto()
                            .message(
                                1,
                                new Proto()
                                    .number(1, 1)
                                    .message(2, new Proto().text(1, "deleted")))))
            .build();
    var p = LibraryProtocol.parse(response);
    assertEquals(List.of("deleted"), p.deletions());
    assertEquals(1, p.items().size());
  }

  @Test
  public void initialAndDeltaPagesKeepTheirOwnCursors() throws Exception {
    var c =
        LibraryProtocol.advance(
            LibraryProtocol.Cursor.fresh(), page("initial-page", "sync-a", false));
    assertEquals(LibraryProtocol.Stage.INITIAL, c.stage());
    c = LibraryProtocol.advance(c, page("", "", false));
    assertEquals(LibraryProtocol.Stage.SYNC, c.stage());
    c = LibraryProtocol.advance(c, page("delta-page", "sync-b", false));
    assertEquals(LibraryProtocol.Stage.DELTA, c.stage());
    assertArrayEquals("sync-a".getBytes(), c.sync());
    c = LibraryProtocol.advance(c, page("", "", false));
    assertEquals(LibraryProtocol.Stage.DONE, c.stage());
    assertArrayEquals("sync-b".getBytes(), c.sync());
  }

  @Test
  public void continuationRequiresAdvancingSync() throws Exception {
    var c =
        new LibraryProtocol.Cursor(
            LibraryProtocol.Stage.SYNC, new byte[0], "same".getBytes(), "same".getBytes(), false);
    assertThrows(IOException.class, () -> LibraryProtocol.advance(c, page("", "same", true)));
    assertEquals(
        LibraryProtocol.Stage.SYNC, LibraryProtocol.advance(c, page("", "new", true)).stage());
  }

  @Test
  public void repeatedPagesAreRejected() {
    var c =
        new LibraryProtocol.Cursor(
            LibraryProtocol.Stage.INITIAL,
            "same".getBytes(),
            "s".getBytes(),
            "s".getBytes(),
            false);
    assertThrows(IOException.class, () -> LibraryProtocol.advance(c, page("same", "", false)));
  }

  @Test
  public void unexpectedOrTruncatedResponsesAreNotEmptySuccess() {
    assertThrows(IOException.class, () -> LibraryProtocol.parse(new byte[0]));
    assertThrows(
        IOException.class,
        () -> LibraryProtocol.parse(new Proto().message(1, new Proto().number(90, 1)).build()));
    assertThrows(IOException.class, () -> LibraryProtocol.parse(new byte[] {10, 5, 8}));
    assertThrows(
        IOException.class,
        () ->
            LibraryProtocol.parse(
                new Proto()
                    .message(
                        1,
                        new Proto()
                            .text(6, "s")
                            .message(2, new Proto().message(2, new Proto().text(4, "no-key"))))
                    .build()));
  }

  @Test
  public void requestChangesOnlyCursorFields() throws Exception {
    byte[] template =
        new Proto()
            .message(
                1,
                new Proto()
                    .message(1, new Proto().number(7, 9))
                    .text(4, "old")
                    .text(6, "old")
                    .number(7, 2))
            .message(2, new Proto().number(8, 3))
            .build();
    var c =
        new LibraryProtocol.Cursor(
            LibraryProtocol.Stage.DELTA,
            "resume".getBytes(),
            "sync".getBytes(),
            new byte[0],
            false);
    byte[] result = LibraryProtocol.request(template, c), body = Proto.message(result, 1);
    assertArrayEquals("resume".getBytes(), Proto.message(body, 4));
    assertArrayEquals("sync".getBytes(), Proto.message(body, 6));
    assertArrayEquals(Proto.message(Proto.message(template, 1), 1), Proto.message(body, 1));
    assertArrayEquals(Proto.message(template, 2), Proto.message(result, 2));
  }
}
