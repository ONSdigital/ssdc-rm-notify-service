package uk.gov.ons.ssdc.notifysvc.model.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uk.gov.ons.ssdc.notifysvc.model.entity.Survey;

public interface SurveyRepository extends JpaRepository<Survey, UUID> {}
