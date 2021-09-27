package uk.gov.ons.ssdc.notifysvc.testUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import uk.gov.ons.ssdc.notifysvc.utils.ObjectMapperFactory;

public class JsonTestHelper {
  private static final ObjectMapper objectMapper = ObjectMapperFactory.objectMapper();

  public static String convertObjectToJson(Object obj) {
    try {
      return objectMapper.writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed converting Object To Json", e);
    }
  }
}
