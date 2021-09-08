package uk.gov.ons.ssdc.notifysvc.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.xml.bind.DatatypeConverter;

public class HashHelper {
  private static final MessageDigest digest;

  static {
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("Could not initialise hashing", e);
    }
  }

  public static String hash(String stringToHash) {

    return hash(stringToHash.getBytes(StandardCharsets.UTF_8));
  }

  public static String hash(byte[] bytesToHash) {
    byte[] hash;

    // Digest is not thread safe
    synchronized (digest) {
      hash = digest.digest(bytesToHash);
    }

    return DatatypeConverter.printHexBinary(hash).toLowerCase();
  }
}
