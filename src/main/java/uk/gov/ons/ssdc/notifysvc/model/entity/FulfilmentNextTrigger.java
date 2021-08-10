package uk.gov.ons.ssdc.notifysvc.model.entity;

import java.time.OffsetDateTime;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import lombok.Data;

@Entity
@Data
public class FulfilmentNextTrigger {
  @Id private UUID id;

  @Column(columnDefinition = "timestamp with time zone")
  private OffsetDateTime triggerDateTime;
}
