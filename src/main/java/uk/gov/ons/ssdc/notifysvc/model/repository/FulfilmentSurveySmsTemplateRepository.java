package uk.gov.ons.ssdc.notifysvc.model.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uk.gov.ons.ssdc.common.model.entity.FulfilmentSurveySmsTemplate;
import uk.gov.ons.ssdc.common.model.entity.SmsTemplate;
import uk.gov.ons.ssdc.common.model.entity.Survey;

public interface FulfilmentSurveySmsTemplateRepository
    extends JpaRepository<FulfilmentSurveySmsTemplate, UUID> {

  boolean existsBySmsTemplateAndSurvey(SmsTemplate smsTemplate, Survey survey);
}
