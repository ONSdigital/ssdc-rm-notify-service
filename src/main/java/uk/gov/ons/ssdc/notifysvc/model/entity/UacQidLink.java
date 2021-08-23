package uk.gov.ons.ssdc.notifysvc.model.entity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import javax.persistence.*;
import lombok.Data;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@ToString(onlyExplicitlyIncluded = true) // Bidirectional relationship causes IDE stackoverflow
@Data
@Entity
public class UacQidLink {
  @Id private UUID id;

  @Column(
      name = "qid", // "name" annotation is required for index on this column to work
      nullable = false)
  private String qid;

  @Column(nullable = false)
  private String uac;

  @ManyToOne private Case caze;

  @OneToMany(mappedBy = "uacQidLink")
  private List<Event> events;

  @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT true")
  private boolean active = true;

  @Column(columnDefinition = "timestamp with time zone")
  @CreationTimestamp
  private OffsetDateTime createdAt;

  @Column(columnDefinition = "timestamp with time zone")
  @UpdateTimestamp
  private OffsetDateTime lastUpdatedAt;
}
