package uk.gov.ons.ssdc.notifysvc.utils;

import java.util.Set;

public class Constants {
  public static final String OUTBOUND_EVENT_SCHEMA_VERSION = "v0.3_RELEASE";
  public static final Set<String> ALLOWED_INBOUND_EVENT_SCHEMA_VERSIONS =
      Set.of("v0.3_RELEASE", "0.4.0-DRAFT", "0.4.0", "0.5.0-DRAFT");
  public static final String SMS_TEMPLATE_UAC_KEY = "__uac__";
  public static final String SMS_TEMPLATE_QID_KEY = "__qid__";
  public static final String SMS_TEMPLATE_SENSITIVE_PREFIX = "__sensitive__.";
  public static final int QID_TYPE = 1;
}
