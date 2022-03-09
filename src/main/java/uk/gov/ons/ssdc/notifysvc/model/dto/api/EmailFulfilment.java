package uk.gov.ons.ssdc.notifysvc.model.dto.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import java.util.UUID;
import lombok.Data;

@Data
public class EmailFulfilment {
  @Schema(description = "The case, which must exist in RM")
  private UUID caseId;

  @Schema(
      description = "The target email address, to which we will send a fulfilment",
      example = "example@example.com")
  private String email;

  @Schema(
      description =
          "The pack code, which must exist in RM and the pack code must be allowed on the survey the case belongs to")
  private String packCode;

  @Schema(description = "Metadata for UACQIDLinks")
  private Object uacMetadata;

  @Schema(
      description =
          "Optional personalisation keys/value pairs to include in the sent email. Keys must match \"__request__.\" prefixed fields in the selected template, or they will be ignored",
      example = "{\"name\":\"Joe Bloggs\"}",
      nullable = true)
  private Map<String, String> personalisation;
}
