package uk.gov.ons.ssdc.notifysvc.utils;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.ons.ssdc.notifysvc.utils.Constants.TEMPLATE_QID_KEY;
import static uk.gov.ons.ssdc.notifysvc.utils.Constants.TEMPLATE_SENSITIVE_PREFIX;
import static uk.gov.ons.ssdc.notifysvc.utils.Constants.TEMPLATE_UAC_KEY;

import java.util.Map;
import org.junit.jupiter.api.Test;
import uk.gov.ons.ssdc.common.model.entity.Case;

class PersonalisationTemplateHelperTest {
  private static final String TEST_UAC = "TEST_UAC";
  private static final String TEST_QID = "TEST_QID";

  @Test
  void testBuildPersonalisationFromTemplate() {
    // Given
    String[] template =
        new String[] {TEMPLATE_UAC_KEY, TEMPLATE_QID_KEY, "foo", TEMPLATE_SENSITIVE_PREFIX + "foo"};

    Case testCase = new Case();
    testCase.setSample(Map.ofEntries(entry("foo", "bar")));
    testCase.setSampleSensitive(Map.ofEntries(entry("foo", "secretBar")));

    // When
    Map<String, String> personalisationValues =
        PersonalisationTemplateHelper.buildPersonalisationFromTemplate(
            template, testCase, TEST_UAC, TEST_QID);

    // Then
    assertThat(personalisationValues)
        .containsEntry(TEMPLATE_UAC_KEY, TEST_UAC)
        .containsEntry(TEMPLATE_QID_KEY, TEST_QID)
        .containsEntry("foo", "bar")
        .containsEntry(TEMPLATE_SENSITIVE_PREFIX + "foo", "secretBar");
  }

  @Test
  void testBuildPersonalisationFromTemplateJustUac() {
    // Given
    String[] template = new String[] {TEMPLATE_UAC_KEY};

    Case testCase = new Case();

    // When
    Map<String, String> personalisationValues =
        PersonalisationTemplateHelper.buildPersonalisationFromTemplate(
            template, testCase, TEST_UAC, TEST_QID);

    // Then
    assertThat(personalisationValues)
        .containsEntry(TEMPLATE_UAC_KEY, TEST_UAC)
        .containsOnlyKeys(TEMPLATE_UAC_KEY);
  }

  @Test
  void testBuildPersonalisationFromTemplateJustQid() {
    // Given
    String[] template = new String[] {TEMPLATE_QID_KEY};

    Case testCase = new Case();

    // When
    Map<String, String> personalisationValues =
        PersonalisationTemplateHelper.buildPersonalisationFromTemplate(
            template, testCase, TEST_UAC, TEST_QID);

    // Then
    assertThat(personalisationValues)
        .containsEntry(TEMPLATE_QID_KEY, TEST_QID)
        .containsOnlyKeys(TEMPLATE_QID_KEY);
  }

  @Test
  void testBuildPersonalisationFromTemplateJustSampleFields() {
    // Given
    String[] template = new String[] {"foo", "spam"};

    Case testCase = new Case();
    testCase.setSample(Map.ofEntries(entry("foo", "bar"), entry("spam", "eggs")));

    // When
    Map<String, String> personalisationValues =
        PersonalisationTemplateHelper.buildPersonalisationFromTemplate(
            template, testCase, TEST_UAC, TEST_QID);

    // Then
    assertThat(personalisationValues).containsEntry("foo", "bar").containsEntry("spam", "eggs");
  }

  @Test
  void testBuildPersonalisationFromTemplateJustSampleSensitiveFields() {
    // Given
    String[] template =
        new String[] {TEMPLATE_SENSITIVE_PREFIX + "foo", TEMPLATE_SENSITIVE_PREFIX + "spam"};

    Case testCase = new Case();
    testCase.setSampleSensitive(
        Map.ofEntries(entry("foo", "secretBar"), entry("spam", "secretEggs")));

    // When
    Map<String, String> personalisationValues =
        PersonalisationTemplateHelper.buildPersonalisationFromTemplate(
            template, testCase, TEST_UAC, TEST_QID);

    // Then
    assertThat(personalisationValues)
        .containsEntry(TEMPLATE_SENSITIVE_PREFIX + "foo", "secretBar")
        .containsEntry(TEMPLATE_SENSITIVE_PREFIX + "spam", "secretEggs");
  }

  @Test
  void testBuildPersonalisationFromTemplateNoUacQidGiven() {
    // Given
    String[] template = new String[] {"foo", "spam"};

    Case testCase = new Case();
    testCase.setSample(Map.ofEntries(entry("foo", "bar"), entry("spam", "eggs")));

    // When
    Map<String, String> personalisationValues =
        PersonalisationTemplateHelper.buildPersonalisationFromTemplate(template, testCase);

    // Then
    assertThat(personalisationValues).containsEntry("foo", "bar").containsEntry("spam", "eggs");
  }
}
