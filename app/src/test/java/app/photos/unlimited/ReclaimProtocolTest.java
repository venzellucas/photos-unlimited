package app.photos.unlimited;

import static org.junit.Assert.*;

import java.util.HexFormat;
import java.util.List;
import org.junit.Test;

/**
 * Expected bytes were generated independently from the pinned upstream GPMC message types with
 * blackboxprotobuf (client version 49029607, Android API 28).
 */
public class ReclaimProtocolTest {
  private static String hex(byte[] b) {
    return HexFormat.of().formatHex(b);
  }

  @Test
  public void mutationRequestsMatchUpstreamEncoding() {
    assertEquals(
        "10011a026b311a026b322001420e220c12001a020a0022002a020a004a0d0805120908e7c3b01712023238",
        hex(PhotosApi.mutationBody(1, List.of("k1", "k2"))));
    assertEquals(
        "10021a026b312002420e220c12001a020a0022002a020a004a00",
        hex(PhotosApi.mutationBody(2, List.of("k1"))));
    assertEquals(
        "10031a026b3120024208220612001a020a004a0d0805120908e7c3b01712023238",
        hex(PhotosApi.mutationBody(3, List.of("k1"))));
    assertThrows(IllegalArgumentException.class, () -> PhotosApi.mutationBody(2, List.of()));
  }

  @Test
  public void downloadAndMetadataRequestsMatchUpstreamEncoding() {
    assertEquals(
        "0a060a040a026d6b12120a043a0212002a0a12001a002a040a001800",
        hex(PhotosApi.downloadBody("mk")));
    assertEquals("120248691a026b31", hex(PhotosApi.captionBody("k1", "Hi")));
    assertEquals("0a0412026b31120208011a050a039a0100", hex(PhotosApi.favoriteBody("k1", true)));
    assertEquals("0a080a026b31120208011801", hex(PhotosApi.archiveBody(List.of("k1"), true)));
  }

  @Test
  public void downloadLinksPreferTheOriginalAndKeepEditedLast() {
    byte[] response =
        new Proto()
            .message(
                1,
                new Proto()
                    .message(
                        5,
                        new Proto()
                            .message(
                                2,
                                new Proto()
                                    .text(5, "https://video.googleusercontent.com/edited")
                                    .text(6, "https://video.googleusercontent.com/original"))
                            .message(
                                3,
                                new Proto().text(5, "https://video.googleusercontent.com/video")))
                    .text(9, "not a url")
                    .text(10, "https://lh3.googleusercontent.com/other"))
            .build();
    List<PhotosApi.Link> links = PhotosApi.links(response);
    assertEquals(4, links.size());
    assertEquals("1.5.2.6", links.get(0).path());
    assertEquals("1.5.3.5", links.get(1).path());
    assertEquals("1.10", links.get(2).path());
    assertEquals("1.5.2.5", links.get(3).path());
  }

  @Test
  public void onlyGoogleMediaHostsAreDownloaded() {
    assertTrue(PhotosApi.downloadHost("video-downloads.googleusercontent.com"));
    assertTrue(PhotosApi.downloadHost("lh3.googleusercontent.com"));
    assertFalse(PhotosApi.downloadHost("googleusercontent.com.evil.example"));
    assertFalse(PhotosApi.downloadHost("evilgoogleusercontent.com"));
    assertFalse(PhotosApi.downloadHost(null));
  }

  private static byte[] sha(int seed) {
    byte[] b = new byte[20];
    b[0] = (byte) seed;
    return b;
  }

  @Test
  public void reclaimFieldsAreParsedAndOddShapesDoNotFailTheScan() throws Exception {
    Proto details =
        new Proto()
            .text(3, "Sunset")
            .text(4, "IMG_1.jpg")
            .number(10, 500)
            .number(7, 1700000000000L)
            .message(13, new Proto().bytes(1, sha(7)))
            .message(21, new Proto().text(1, "dedup-key-123"))
            .message(31, new Proto().number(1, 1))
            .message(35, new Proto().number(2, 500));
    Proto kind = new Proto().number(1, 1).message(2, new Proto().message(4, new Proto()));
    byte[] response =
        new Proto()
            .message(
                1,
                new Proto()
                    .text(6, "sync")
                    .message(2, new Proto().text(1, "key").message(2, details).message(5, kind)))
            .build();
    LibraryProtocol.Extra x = LibraryProtocol.parse(response).items().get(0).extra();
    assertEquals("dedup-key-123", x.dedup());
    assertArrayEquals(sha(7), x.sha1());
    assertEquals("Sunset", x.caption());
    assertTrue(x.favorite());
    assertTrue(x.edited());
    assertTrue(x.canonical());
    assertFalse(x.locked());
    assertFalse(x.motion());

    // A nested message where text was expected, an alternate version, and no dedup field.
    Proto odd =
        new Proto()
            .message(3, new Proto().number(1, 1).number(2, 3))
            .text(4, "IMG_2.jpg")
            .message(5, new Proto().number(1, 27))
            .message(13, new Proto().bytes(1, sha(9)))
            .message(39, new Proto().number(1, 1));
    Proto motion =
        new Proto()
            .number(1, 1)
            .message(5, new Proto().number(1, 3).message(2, new Proto().number(3, 1)));
    byte[] second =
        new Proto()
            .message(
                1,
                new Proto()
                    .text(6, "sync")
                    .message(2, new Proto().text(1, "key2").message(2, odd).message(5, motion)))
            .build();
    LibraryProtocol.Extra y = LibraryProtocol.parse(second).items().get(0).extra();
    assertEquals("", y.caption());
    assertFalse(y.canonical());
    assertTrue(y.locked());
    assertTrue(y.motion());
    // Upstream derives the dedup key from the content hash when Google omits it.
    assertEquals(
        java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(sha(9)), y.dedup());
  }

  @Test
  public void storedQualityIsReadFromTheQuotaRecord() throws Exception {
    assertEquals(
        LibraryProtocol.Quality.ORIGINAL,
        LibraryProtocol.quality(new Proto().message(35, new Proto().number(2, 5).number(3, 2)).build()));
    assertEquals(
        LibraryProtocol.Quality.REDUCED,
        LibraryProtocol.quality(new Proto().message(35, new Proto().number(2, 0).number(3, 1)).build()));
    assertEquals(
        LibraryProtocol.Quality.UNKNOWN,
        LibraryProtocol.quality(new Proto().message(35, new Proto().number(2, 5)).build()));
  }

  @Test
  public void controlCharactersNeverBecomeCaptions() {
    assertEquals("", LibraryProtocol.cleanCaption("a\u0001b"));
    assertEquals("", LibraryProtocol.cleanCaption("bad�"));
    assertEquals("Two\nlines", LibraryProtocol.cleanCaption("Two\nlines"));
  }
}
