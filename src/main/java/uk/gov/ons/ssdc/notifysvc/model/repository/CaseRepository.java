package uk.gov.ons.ssdc.notifysvc.model.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.gov.ons.ssdc.notifysvc.model.entity.Case;

import java.util.UUID;

public interface CaseRepository extends JpaRepository<Case, UUID> {

}
