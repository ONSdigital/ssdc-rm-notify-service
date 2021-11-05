package uk.gov.ons.ssdc.notifysvc.model.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.gov.ons.ssdc.common.model.entity.EmailTemplate;

public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, String> {}
