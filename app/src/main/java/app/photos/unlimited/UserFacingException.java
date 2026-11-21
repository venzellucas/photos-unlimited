package app.photos.unlimited;

import java.io.IOException;

/** An error whose message is translated text meant for the user, safe to show and to store. */
class UserFacingException extends IOException {
  UserFacingException(String message) {
    super(message);
  }
}
