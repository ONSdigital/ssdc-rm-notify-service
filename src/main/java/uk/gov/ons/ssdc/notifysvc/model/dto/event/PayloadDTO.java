package uk.gov.ons.ssdc.notifysvc.model.dto.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.Data;

@Data
@JsonInclude(Include.NON_NULL)
public class PayloadDTO {

  private EnrichedSmsFulfilment enrichedSmsFulfilment;
  private SmsRequest smsRequest;
  private SmsRequestEnriched smsRequestEnriched;
}
