package app.photos.unlimited;

import java.io.IOException;

/** A library scan failure with a fixed, secret-free label for diagnosis. */
final class ScanFailure extends IOException {
  private final String label;

  ScanFailure(String label, String message) {
    super(message);
    if (label == null || !label.matches("[A-Z0-9_]{1,48}"))
      throw new IllegalArgumentException("Invalid scan failure label");
    this.label = label;
  }

  String label() {
    return label;
  }
}
