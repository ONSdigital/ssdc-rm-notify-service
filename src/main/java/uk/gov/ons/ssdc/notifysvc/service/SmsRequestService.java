package uk.gov.ons.ssdc.notifysvc.service;

import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import uk.gov.ons.ssdc.notifysvc.client.UacQidServiceClient;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.UacQidCreatedPayloadDTO;

@Service
public class SmsRequestService {
  private static final int QID_TYPE = 1; // TODO replace hardcoded QID type
  private static final String SMS_TEMPLATE_UAC_KEY = "__uac__";
  private static final String SMS_TEMPLATE_QID_KEY = "__qid__";

  private final UacQidServiceClient uacQidServiceClient;

  public SmsRequestService(UacQidServiceClient uacQidServiceClient) {
    this.uacQidServiceClient = uacQidServiceClient;
  }

  public UacQidCreatedPayloadDTO fetchNewUacQidPairIfRequired(String[] smsTemplate) {
    if (CollectionUtils.containsAny(
        Arrays.asList(smsTemplate), List.of(SMS_TEMPLATE_UAC_KEY, SMS_TEMPLATE_QID_KEY))) {
      return uacQidServiceClient.generateUacQid(QID_TYPE);
    }
    return null;
  }
}
