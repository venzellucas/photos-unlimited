package app.photos.unlimited;

import static org.junit.Assert.*;

import java.util.List;
import org.junit.Test;

public class LibraryShapeTest {
  private static Proto media(String key, Proto details) {
    return new Proto().text(1, key).message(2, details).message(5, new Proto().number(1, 1));
  }

  private static LibraryProtocol.Page parse(Proto... items) throws Exception {
    Proto root = new Proto().text(6, "sync");
    for (Proto item : items) root.message(2, item);
    return LibraryProtocol.parse(new Proto().message(1, root).build());
  }

  @Test
  public void unreadableItemsCarryTheStepReasonAndFieldShape() throws Exception {
    var p =
        parse(
            media("a", new Proto().text(4, "fine.jpg").message(35, new Proto().number(2, 0))),
            media(
                "b",
                new Proto()
                    .text(4, "b.jpg")
                    .message(35, new Proto().number(2, 5))
                    .message(35, new Proto().number(2, 5))),
            media("c", new Proto().text(4, "c.jpg").number(10, 500).number(10, 600)),
            media("d", new Proto().text(4, "d.jpg").message(16, new Proto().text(3, "yes"))),
            // Four duplicated taken times: three are shown, then the total.
            media(
                "e",
                new Proto().text(4, "e.jpg").number(7, 1).number(7, 2).number(7, 3).number(7, 4)),
            // A damaged details body is blamed on the body, not on the file name.
            new Proto().text(1, "f").bytes(2, new byte[] {0x22, 9, 'x'}),
            new Proto()
                .text(1, "g")
                .message(2, new Proto().text(4, "g.jpg"))
                .message(5, new Proto().text(1, "photo")));
    assertEquals(7, p.items().size());
    assertEquals(
        List.of(
            new LibraryShape.Unknown("D35_DUP_F35", "35:2M(s){2:0=5} 35:2M(s){2:0=5}"),
            new LibraryShape.Unknown("D10_DUP_F10", "10:0=n 10:0=n"),
            new LibraryShape.Unknown("D16_3_WIRE_F3_W2", "3:2T(s)"),
            new LibraryShape.Unknown("D7_DUP_F7", "7:0=1 7:0=2 7:0=3 x4"),
            new LibraryShape.Unknown("D_CUT_F4", "?"),
            new LibraryShape.Unknown("K1_WIRE_F1_W2", "1:2T(s)")),
        p.unknown());
  }

  @Test
  public void theFileNameIsNeverOpened() throws Exception {
    byte[] latin1 = {'S', (byte) 0xE3, 'o', '.', 'j', 'p', 'g'};
    var p =
        parse(
            media("a", new Proto().bytes(4, latin1)),
            media("b", new Proto().text(4, "n".repeat(16385))),
            // A duplicated name that parses as a message still shows only its kind.
            media(
                "c",
                new Proto()
                    .message(4, new Proto().text(1, "secret"))
                    .message(4, new Proto().text(1, "secret"))));
    assertEquals(
        List.of(
            new LibraryShape.Unknown("D4_UTF8", "4:2B(s)"),
            new LibraryShape.Unknown("D4_LONG", "4:2B(x)"),
            new LibraryShape.Unknown("D4_DUP_F4", "4:2M(s) 4:2M(s)")),
        p.unknown());
  }

  @Test
  public void sketchesShowOnlyFieldsWireTypesBucketsAndSmallNumbers() {
    byte[] data =
        new Proto()
            .text(3, "hello")
            .number(7, 1234)
            .number(8, 2)
            .message(4, new Proto().text(1, "x").number(2, 3))
            .message(4, new Proto().text(1, "y").number(2, 3))
            .bytes(9, new byte[0])
            .text(5, "z".repeat(300))
            .build();
    String sketch = LibraryShape.sketch(data, 4);
    assertEquals("3:2T(s) 7:0=n 8:0=2 4:2x2M(s){1:2T(s) 2:0=3} 9:2(0) 5:2T(l)", sketch);
    assertFalse(sketch.contains("hello"));
    assertFalse(sketch.contains("1234"));
    byte[] twice =
        new Proto()
            .message(4, new Proto().number(2, 3))
            .message(4, new Proto().number(2, 3))
            .build();
    assertEquals("4:2x2M(s)", LibraryShape.sketch(twice, 1));
    assertEquals("?", LibraryShape.sketch(new byte[] {0x0a, 5, 1}, 4));
    // Small numbers only, and nothing for fixed-width values.
    byte[] numbers =
        new Proto().number(1, 15).number(2, 16).number(3, -1).build();
    assertEquals("1:0=15 2:0=n 3:0=n", LibraryShape.sketch(numbers, 4));
    // Text with a line break also parses as a message ("@" is field 8, varint 10); it must stay
    // text, or its field numbers would spell its bytes.
    byte[] lines = new Proto().text(1, "@\n").build();
    assertEquals("1:2T(s)", LibraryShape.sketch(lines, 4));
    // Random bytes with a large field number are not read as a message.
    byte[] random = new Proto().bytes(1, new Proto().number(5000, 1).build()).build();
    assertEquals("1:2B(s)", LibraryShape.sketch(random, 4));
    Proto wide = new Proto();
    for (int f = 1; f <= 500; f++) wide.number(f, 1);
    String capped = LibraryShape.sketch(wide.build(), 4);
    assertTrue(capped.endsWith("…"));
    assertTrue(capped.length() <= LibraryShape.MAX_SKETCH + 1);
  }

  @Test
  public void aPageWithNoKnownFieldsCarriesTheRootSketchThroughTheStage() {
    byte[] response =
        new Proto()
            .message(
                1,
                new Proto()
                    .message(5, new Proto().number(1, 2).text(2, "token"))
                    .message(12, new Proto().number(1, 1)))
            .build();
    ScanFailure failure = assertThrows(ScanFailure.class, () -> LibraryProtocol.parse(response));
    assertEquals("SCAN_FORMAT_PAGE_SHAPE_NONE_F5", failure.label());
    assertEquals("5:2M(s){1:0=2 2:2T(s)} 12:2M(s){1:0=1}", failure.shape());
    ScanFailure staged = LibraryProtocol.withStage(failure, LibraryProtocol.Stage.DELTA);
    assertEquals("SCAN_FORMAT_PAGE_SHAPE_NONE_F5_D", staged.label());
    assertEquals(failure.shape(), staged.shape());
  }

  @Test
  public void aPageWithOnlyTheCollectionFieldIsAnEmptyLastPage() throws Exception {
    // The shape the author's phone reported for the last delta page (issue #2), shortened.
    byte[] response =
        new Proto()
            .message(
                1,
                new Proto()
                    .message(
                        4,
                        new Proto()
                            .text(1, "id")
                            .text(13, "title")
                            .message(7, new Proto().message(8, new Proto().number(1, 1)))))
            .build();
    var p = LibraryProtocol.parse(response);
    assertTrue(p.items().isEmpty());
    assertTrue(p.deletions().isEmpty());
    assertEquals(0, p.resume().length);
    assertEquals(0, p.sync().length);
    assertFalse(p.more());
    assertEquals(List.of("4:2"), p.rootFields());
    // A varint field 4 is not the record: the page still has no known fields.
    ScanFailure failure =
        assertThrows(
            ScanFailure.class,
            () -> LibraryProtocol.parse(new Proto().message(1, new Proto().number(4, 1)).build()));
    assertEquals("SCAN_FORMAT_PAGE_SHAPE_NONE_F4", failure.label());
  }

  @Test
  public void pagesReportTheirRootFields() throws Exception {
    var p =
        LibraryProtocol.parse(
            new Proto()
                .message(
                    1,
                    new Proto()
                        .text(6, "sync")
                        .message(2, media("a", new Proto().text(4, "a.jpg")))
                        .message(2, media("b", new Proto().text(4, "b.jpg")))
                        .number(7, 2)
                        .text(12, "e"))
                .build());
    assertEquals(List.of("6:2", "2:2", "7:0", "12:2"), p.rootFields());
    assertTrue(p.unknown().isEmpty());
  }

  private static LibraryProtocol.Page page(List<String> roots, LibraryShape.Unknown... unknown) {
    return new LibraryProtocol.Page(
        List.of(), List.of(), new byte[0], new byte[0], false, roots, List.of(unknown));
  }

  @Test
  public void theSummaryAddsUpKeepsItsCapsAndSurvivesItsStoredForm() {
    LibraryShape shape = new LibraryShape();
    shape.page(LibraryProtocol.Stage.SYNC, page(List.of("6:2", "2:2", "12:2")));
    shape.page(
        LibraryProtocol.Stage.SYNC,
        page(
            List.of("2:2", "6:2"),
            new LibraryShape.Unknown("D35_DUP_F35", "35:2M(s) 35:2M(s)"),
            new LibraryShape.Unknown("D35_DUP_F35", "other")));
    for (int i = 0; i < 25; i++)
      shape.page(
          LibraryProtocol.Stage.DELTA, page(List.of(), new LibraryShape.Unknown("L" + i, "s")));
    for (int i = 0; i < 5; i++) shape.skipped(LibraryProtocol.Stage.DELTA, "4:2M(s)" + i);
    shape.skipped(LibraryProtocol.Stage.DELTA, "4:2M(s)0");
    shape.skipped(LibraryProtocol.Stage.DELTA, null);

    String report = shape.report();
    assertTrue(report, report.contains("\nPages: D=32 S=2"));
    assertTrue(report, report.contains("\n  S: 2:2x2 6:2x2 12:2x1"));
    assertTrue(report, report.contains("\nUnreadable item occurrences: 27"));
    // The first sketch of a label is kept.
    assertTrue(report, report.contains("\n  D35_DUP_F35 x2: 35:2M(s) 35:2M(s)"));
    assertTrue(report, report.contains("\n  L18 x1: s"));
    assertFalse(report, report.contains("L19"));
    assertTrue(report, report.contains("\n  OTHER x6: s"));
    assertTrue(report, report.contains("\nSkipped delta pages: 7"));
    assertTrue(report, report.contains("\n  x2: 4:2M(s)0"));
    assertTrue(report, report.contains("\n  x3: OTHER"));

    assertEquals(report, LibraryShape.decode(shape.encode()).report());
    // A later run of the same scan adds to the stored summary.
    LibraryShape later = LibraryShape.decode(shape.encode());
    later.page(LibraryProtocol.Stage.SYNC, page(List.of("6:2")));
    assertTrue(later.report().contains("\nPages: D=32 S=3"));
    assertTrue(later.report().contains("\n  S: 2:2x2 6:2x3 12:2x1"));
    assertEquals(shape.encode(), LibraryShape.decode(shape.encode()).encode());
    // A damaged line is dropped, and the rest still reads.
    assertTrue(
        LibraryShape.decode("P\tS\tnot-a-number\nP\tD\t3\n").report().contains("Pages: D=3"));
    assertTrue(new LibraryShape().report().endsWith("No scan recorded yet."));
  }
}
