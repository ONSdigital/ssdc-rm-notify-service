package uk.gov.ons.ssdc.notifysvc.model.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Entity
public class ClusterLeader {
  @Id private UUID id;

  @Column private String hostName;

  @Column(columnDefinition = "timestamp with time zone")
  private OffsetDateTime hostLastSeenAliveAt;
}
