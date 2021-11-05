package uk.gov.ons.ssdc.notifysvc.model.dto.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@JsonInclude(Include.NON_NULL)
public class EmailFulfilmentResponseSuccess implements EmailFulfilmentResponse {
  private String uacHash;
  private String qid;
}
