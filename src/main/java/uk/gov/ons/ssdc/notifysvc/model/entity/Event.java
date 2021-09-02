package uk.gov.ons.ssdc.notifysvc.model.entity;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import java.time.OffsetDateTime;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
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

  @Column(nullable = false, columnDefinition = "timestamp with time zone")
  private OffsetDateTime dateTime;

  @Column(nullable = false)
  private String description;

  @Column(nullable = false, columnDefinition = "timestamp with time zone")
  private OffsetDateTime processedAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private EventType type;

  @Column(nullable = false)
  private String channel;

  @Column(nullable = false)
  private String source;

  @Column(nullable = false)
  private UUID messageId;

  @Column(nullable = false)
  private UUID correlationId;

  @Type(type = "jsonb")
  @Column(columnDefinition = "jsonb")
  private String payload;

  @Column(nullable = false, columnDefinition = "Timestamp with time zone")
  private OffsetDateTime messageTimestamp;

  @Column private String createdBy;
}
