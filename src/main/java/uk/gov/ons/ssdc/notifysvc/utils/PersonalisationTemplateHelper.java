package uk.gov.ons.ssdc.notifysvc.utils;

import static uk.gov.ons.ssdc.notifysvc.utils.Constants.TEMPLATE_QID_KEY;
import static uk.gov.ons.ssdc.notifysvc.utils.Constants.TEMPLATE_REQUEST_PREFIX;
import static uk.gov.ons.ssdc.notifysvc.utils.Constants.TEMPLATE_SENSITIVE_PREFIX;
import static uk.gov.ons.ssdc.notifysvc.utils.Constants.TEMPLATE_UAC_KEY;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.CollectionUtils;
import uk.gov.ons.ssdc.common.model.entity.Case;

public class PersonalisationTemplateHelper {
  public static Map<String, String> buildPersonalisationFromTemplate(
      String[] template,
      Case caze,
      String uac,
      String qid,
      Map<String, String> requestPersonalisation) {
    Map<String, String> templateValues = new HashMap<>();

    for (String templateItem : template) {

      if (templateItem.equals(TEMPLATE_UAC_KEY)) {
        templateValues.put(TEMPLATE_UAC_KEY, uac);

      } else if (templateItem.equals(TEMPLATE_QID_KEY)) {
        templateValues.put(TEMPLATE_QID_KEY, qid);

      } else if (templateItem.startsWith(TEMPLATE_SENSITIVE_PREFIX)) {
        templateValues.put(
            templateItem,
            caze.getSampleSensitive()
                .get(templateItem.substring(TEMPLATE_SENSITIVE_PREFIX.length())));

      } else if (templateItem.startsWith(TEMPLATE_REQUEST_PREFIX)) {
        if (requestPersonalisation != null
            && requestPersonalisation.containsKey(
                templateItem.substring(TEMPLATE_REQUEST_PREFIX.length()))) {
          templateValues.put(
              templateItem,
              requestPersonalisation.get(templateItem.substring(TEMPLATE_REQUEST_PREFIX.length())));
        }
      } else {
        templateValues.put(templateItem, caze.getSample().get(templateItem));
      }
    }

    return templateValues;
  }

  public static Map<String, String> buildPersonalisationFromTemplate(
      String[] template, Case caze, Map<String, String> requestPersonalisation) {
    return buildPersonalisationFromTemplate(template, caze, null, null, requestPersonalisation);
  }

  public static boolean doesTemplateRequireNewUacQid(String[] template) {
    return CollectionUtils.containsAny(
        Arrays.asList(template), List.of(TEMPLATE_UAC_KEY, TEMPLATE_QID_KEY));
  }
}
