package uk.gov.ons.ssdc.notifysvc.utils;

import java.util.Set;

public class Constants {
  public static final String OUTBOUND_EVENT_SCHEMA_VERSION = "0.5.0";
  public static final Set<String> ALLOWED_INBOUND_EVENT_SCHEMA_VERSIONS =
      Set.of("v0.3_RELEASE", "0.4.0-DRAFT", "0.4.0", "0.5.0-DRAFT", "0.5.0", "0.6.0-DRAFT");
  public static final String TEMPLATE_UAC_KEY = "__uac__";
  public static final String TEMPLATE_QID_KEY = "__qid__";
  public static final String TEMPLATE_SENSITIVE_PREFIX = "__sensitive__.";
  public static final String TEMPLATE_REQUEST_PREFIX = "__request__.";
  public static final String RATE_LIMITER_EXCEPTION_MESSAGE =
      "Error: Rate limit exceeded when trying attempting to send";
  public static final int RATE_LIMIT_ERROR_HTTP_STATUS = 429;
}
