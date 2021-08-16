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
  private OffsetDateTime dateTime;

  @Column private String description;

  @Column(columnDefinition = "timestamp with time zone")
  private OffsetDateTime processedAt;

  @Enumerated(EnumType.STRING)
  @Column
  private EventType type;

  @Column private String channel;
  @Column private String source;
  @Column private UUID messageId;
  @Column private UUID correlationId;

  @Type(type = "jsonb")
  @Column(columnDefinition = "jsonb")
  private String payload;

  @Column(columnDefinition = "Timestamp with time zone")
  private OffsetDateTime messageTimestamp;

  @Column private String createdBy;
}
