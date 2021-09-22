package uk.gov.ons.ssdc.notifysvc.model.dto.api;

import lombok.Data;

@Data
public class ExceptionReportResponse {
  private boolean peek;
  private boolean logIt;
  private boolean skipIt;
}
