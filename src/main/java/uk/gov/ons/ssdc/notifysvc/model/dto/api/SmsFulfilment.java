package uk.gov.ons.ssdc.notifysvc.model.dto.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import java.util.UUID;
import lombok.Data;

@Data
public class SmsFulfilment {
  @Schema(description = "The case, which must exist in RM")
  private UUID caseId;

  @Schema(
      description =
          "The phone number, which must be a UK number consisting of 9 digits, preceded by a `7` and optionally a UK country code or zero (`0`, `044` or `+44`).",
      example = "+447123456789")
  private String phoneNumber;

  @Schema(
      description =
          "The pack code, which must exist in RM and the pack code must be allowed on the survey the case belongs to")
  private String packCode;

  @Schema(description = "Metadata for UACQIDLinks")
  private Map<String, String> uacMetadata;
}
