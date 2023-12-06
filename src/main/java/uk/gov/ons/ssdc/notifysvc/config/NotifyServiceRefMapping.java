package uk.gov.ons.ssdc.notifysvc.config;

import java.util.Map;
import uk.gov.service.notify.NotificationClient;

public class NotifyServiceRefMapping {

  private Map<String, NotificationClient> notifyRefClientMapping;
  private Map<String, String> notifyRefSenderIdMapping;

  public NotificationClient getNotifyClient(String notifyServiceRef) {
    return notifyRefClientMapping.get(notifyServiceRef);
  }

  public String getSenderId(String notifyServiceRef) {
    return notifyRefSenderIdMapping.get(notifyServiceRef);
  }

  public void addNotifyClient(
      String notifyServiceRef, String baseUrl, String apiKey, String senderId) {
    notifyRefSenderIdMapping.put(notifyServiceRef, senderId);
    notifyRefClientMapping.put(notifyServiceRef, new NotificationClient(apiKey, baseUrl));
  }
}
