package uk.gov.ons.ssdc.notifysvc.model.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.gov.ons.ssdc.common.model.entity.EmailTemplate;
import uk.gov.ons.ssdc.common.model.entity.FulfilmentSurveyEmailTemplate;
import uk.gov.ons.ssdc.common.model.entity.Survey;

import java.util.UUID;

public interface FulfilmentSurveyEmailTemplateRepository
    extends JpaRepository<FulfilmentSurveyEmailTemplate, UUID> {

  boolean existsByEmailTemplateAndSurvey(EmailTemplate emailTemplate, Survey survey);
}
