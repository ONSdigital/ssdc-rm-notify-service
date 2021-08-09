package uk.gov.ons.ssdc.notifysvc.model.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Data
public class FulfilmentNextTrigger {
  @Id private UUID id;

  @Column(columnDefinition = "timestamp with time zone")
  private OffsetDateTime triggerDateTime;
}
