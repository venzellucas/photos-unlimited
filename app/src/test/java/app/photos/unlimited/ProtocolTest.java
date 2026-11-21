package app.photos.unlimited;

import static org.junit.Assert.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.Test;

public class ProtocolTest {
  private static final String CREDENTIAL =
      "Email=test%40example.com&Token=private-master&androidId=1234";

  @Test
  public void opaqueReceiptAndOriginalPolicySurviveCommit() throws Exception {
    byte[] receipt = {8, 2, 18, 3, 0, (byte) 255, 9, 40, 7};
    byte[] request = PhotosApi.commitBody(receipt, "IMG.jpg", new byte[20], 1700000000L);
    byte[] item = Proto.fields(request, 1).get(0);
    assertArrayEquals(receipt, Proto.fields(item, 1).get(0));
    assertEquals("IMG.jpg", Proto.path(item, 2));
    assertEquals("Pixel XL", Proto.path(request, 2, 3));
    assertTrue(hex(item).endsWith("38035001"));
    // Captured schema fixture generated independently with upstream blackboxprotobuf.
    assertEquals(
        "0a3b0a090802120300ff0928071207494d472e6a70671a140000000000000000000000000000000000000000220b0880e2cfaa061080cff7153803500112141a08506978656c20584c2206476f6f676c65281c1a020103",
        hex(request));
  }

  @Test
  public void corruptedWireDataIsNotTreatedAsSuccessfulUpload() throws Exception {
    for (byte[] malformed :
        new byte[][] {{0}, {10, (byte) 255}, {10, 127, 1}, {(byte) 128}, {15}}) {
      try {
        Proto.fields(malformed, 1);
        fail("Accepted malformed response");
      } catch (IOException expected) {
      }
    }
    assertEquals("", Proto.path(new byte[0], 1, 3, 1));
    byte[] response =
        new Proto().message(1, new Proto().message(3, new Proto().text(1, "CgIIAQ"))).build();
    assertEquals("CgIIAQ", Proto.path(response, 1, 3, 1));
  }

  @Test
  public void retriesCheckLibraryBeforeUploadingAndRefreshTokens() throws Exception {
    AtomicInteger auth = new AtomicInteger(), find = new AtomicInteger();
    PhotosApi.Transport fake =
        (url, method, headers, body, length, stop) -> {
          ByteArrayOutputStream out = new ByteArrayOutputStream();
          body.write(out);
          if (url.equals(PhotosApi.AUTH)) {
            auth.incrementAndGet();
            return response("Auth=test-access\nExpiry=0\n".getBytes(StandardCharsets.UTF_8));
          }
          assertTrue(url.endsWith("5084965799730810217"));
          find.incrementAndGet();
          assertEquals("Bearer test-access", headers.get("Authorization"));
          return response(
              new Proto()
                  .message(
                      1,
                      new Proto()
                          .message(2, new Proto().message(2, new Proto().text(1, "existing-key"))))
                  .build());
        };
    PhotosApi api = new PhotosApi(CREDENTIAL, fake, new AtomicBoolean());
    assertEquals("existing-key", api.find(new byte[20]));
    assertEquals("existing-key", api.find(new byte[20]));
    assertEquals(2, auth.get());
    assertEquals(2, find.get());
  }

  @Test
  public void aChangedFileIsNeverCommitted() throws Exception {
    AtomicInteger commits = new AtomicInteger();
    PhotosApi.Transport fake =
        (url, method, headers, body, length, stop) -> {
          ByteArrayOutputStream out = new ByteArrayOutputStream();
          body.write(out);
          if (url.equals(PhotosApi.AUTH))
            return response("Auth=access\nExpiry=9999999999\n".getBytes(StandardCharsets.UTF_8));
          if (url.equals(PhotosApi.UPLOAD))
            return new PhotosApi.Response(new byte[0], Map.of("x-guploader-uploadid", "session"));
          if (method.equals("PUT"))
            return response(new Proto().number(1, 2).bytes(2, new byte[] {9}).build());
          commits.incrementAndGet();
          return response(new byte[0]);
        };
    PhotosApi api = new PhotosApi(CREDENTIAL, fake, new AtomicBoolean());
    byte[] expected = PhotosApi.sha1().digest("old".getBytes(StandardCharsets.UTF_8));
    try {
      api.upload(
          () -> new ByteArrayInputStream("new".getBytes(StandardCharsets.UTF_8)),
          "photo.jpg",
          3,
          1700000000,
          expected,
          (a, b) -> {});
      fail();
    } catch (IOException expectedFailure) {
      assertEquals(Text.get(R.string.api_file_changed), expectedFailure.getMessage());
    }
    assertEquals(0, commits.get());
  }

  @Test
  public void completeUploadRequiresAConfirmedMediaKey() throws Exception {
    byte[] contents = {1, 2, 3, 4};
    AtomicInteger commits = new AtomicInteger();
    PhotosApi.Transport fake =
        (url, method, headers, body, length, stop) -> {
          ByteArrayOutputStream out = new ByteArrayOutputStream();
          body.write(out);
          if (url.equals(PhotosApi.AUTH))
            return response("Auth=access\nExpiry=9999999999\n".getBytes(StandardCharsets.UTF_8));
          if (url.equals(PhotosApi.UPLOAD))
            return new PhotosApi.Response(new byte[0], Map.of("x-guploader-uploadid", "session"));
          if (method.equals("PUT")) {
            assertArrayEquals(contents, out.toByteArray());
            return response(new Proto().number(1, 2).bytes(2, new byte[] {9}).build());
          }
          commits.incrementAndGet();
          return response(
              new Proto()
                  .message(1, new Proto().message(3, new Proto().text(1, "confirmed")))
                  .build());
        };
    String result =
        new PhotosApi(CREDENTIAL, fake, new AtomicBoolean())
            .upload(
                () -> new ByteArrayInputStream(contents),
                "test.jpg",
                contents.length,
                1700000000,
                PhotosApi.sha1().digest(contents),
                (a, b) -> {});
    assertEquals("confirmed", result);
    assertEquals(1, commits.get());
  }

  @Test
  public void cancelledWorkMakesNoNetworkRequest() throws Exception {
    PhotosApi api =
        new PhotosApi(
            CREDENTIAL,
            (u, m, h, b, l, s) -> {
              fail("Network must not run");
              return null;
            },
            new AtomicBoolean(true));
    try {
      api.find(new byte[20]);
      fail();
    } catch (InterruptedIOException expected) {
    }
  }

  @Test
  public void signInErrorDoesNotExposeServerSecrets() throws Exception {
    PhotosApi.Transport fake =
        (u, m, h, b, l, s) ->
            response(
                "Error=BadAuthentication\nToken=do-not-display\n".getBytes(StandardCharsets.UTF_8));
    try {
      PhotosApi.connect("one-time-cookie-value", fake, new AtomicBoolean());
      fail();
    } catch (PhotosApi.ApiException e) {
      assertTrue(e.signIn);
      assertFalse(e.getMessage().contains("do-not-display"));
    }
  }

  @Test
  public void signInExchangesAndValidatesWithoutMicrog() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    PhotosApi.Transport fake =
        (u, m, h, b, l, s) -> {
          calls.incrementAndGet();
          ByteArrayOutputStream out = new ByteArrayOutputStream();
          b.write(out);
          if (u.equals(PhotosApi.EXCHANGE)) {
            assertEquals(
                "one-time-cookie-value",
                PhotosApi.parseForm(out.toString(StandardCharsets.UTF_8)).get("Token"));
            return response(
                "Email=test@example.com\nToken=long-lived-secret\n"
                    .getBytes(StandardCharsets.UTF_8));
          }
          if (u.equals(PhotosApi.AUTH))
            return response(
                "Auth=short-lived\nExpiry=9999999999\n".getBytes(StandardCharsets.UTF_8));
          return response(new byte[0]);
        };
    String credential =
        PhotosApi.connect("oauth_token=one-time-cookie-value", fake, new AtomicBoolean());
    assertEquals("test@example.com", PhotosApi.email(credential));
    assertEquals("long-lived-secret", PhotosApi.parseForm(credential).get("Token"));
    assertEquals(3, calls.get());
  }

  private static PhotosApi.Response response(byte[] body) {
    return new PhotosApi.Response(body, Map.of());
  }

  private static String hex(byte[] data) {
    StringBuilder b = new StringBuilder();
    for (byte v : data) b.append(String.format("%02x", v & 255));
    return b.toString();
  }
}
