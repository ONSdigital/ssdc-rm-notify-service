package uk.gov.ons.ssdc.notifysvc.model.entity;

import java.time.OffsetDateTime;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import lombok.Data;

@Data
@Entity
public class ClusterLeader {
  @Id private UUID id;

  @Column(nullable = false)
  private String hostName;

  @Column(nullable = false, columnDefinition = "timestamp with time zone")
  private OffsetDateTime hostLastSeenAliveAt;
}
