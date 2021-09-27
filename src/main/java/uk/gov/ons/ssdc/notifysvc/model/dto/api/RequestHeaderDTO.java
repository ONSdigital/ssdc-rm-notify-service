package uk.gov.ons.ssdc.notifysvc.model.dto.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.Data;

@Data
public class RequestHeaderDTO {
  @Schema(
      description = "The microservice that is calling the API",
      example = "Survey Enquiry Line API")
  private String source;

  @Schema(description = "The product that is calling the API", example = "RH")
  private String channel;

  @Schema(
      description =
          "The ID that connects all the way from the public web load balancer to the backend, and back again")
  private UUID correlationId;

  @Schema(
      description = "The ONS user who is triggering this API request via an internal UI",
      example = "fred.bloggs@ons.gov.uk")
  private String originatingUser;
}
