package uk.gov.ons.ssdc.notifysvc.model.entity;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import java.time.OffsetDateTime;
import java.util.UUID;
import javax.persistence.*;
import lombok.Data;
import lombok.ToString;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.TypeDefs;

// The bidirectional relationships with other entities can cause stack overflows with the default
// toString
@ToString(onlyExplicitlyIncluded = true)
@Data
@TypeDefs({@TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)})
@Entity
public class Event {
  @Id private UUID id;

  @ManyToOne private UacQidLink uacQidLink;

  @ManyToOne private Case caze;

  @Column(columnDefinition = "timestamp with time zone")
  private OffsetDateTime eventDate;

  @Column private String eventDescription;

  @Column(name = "rm_event_processed", columnDefinition = "timestamp with time zone")
  private OffsetDateTime rmEventProcessed;

  @Column(name = "event_type")
  @Enumerated(EnumType.STRING)
  private EventType eventType;

  @Column private String eventChannel;
  @Column private String eventSource;
  @Column private UUID eventTransactionId;

  @Type(type = "jsonb")
  @Column(columnDefinition = "jsonb")
  private String eventPayload;

  @Column(columnDefinition = "Timestamp with time zone")
  private OffsetDateTime messageTimestamp;
}
