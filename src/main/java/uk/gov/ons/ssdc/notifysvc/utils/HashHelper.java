package uk.gov.ons.ssdc.notifysvc.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.xml.bind.DatatypeConverter;

public class HashHelper {
  public static String hash(String stringToHash) {
    return hash(stringToHash.getBytes(StandardCharsets.UTF_8));
  }

  public static String hash(byte[] bytesToHash) {
    byte[] hash;

    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      hash = digest.digest(bytesToHash);
      return DatatypeConverter.printHexBinary(hash).toLowerCase();
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("Could not initialise hashing", e);
    }
  }
}
