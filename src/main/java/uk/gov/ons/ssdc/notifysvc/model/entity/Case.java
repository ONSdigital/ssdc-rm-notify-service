package uk.gov.ons.ssdc.notifysvc.model.entity;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import lombok.Data;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.GenerationTime;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.TypeDefs;
import org.hibernate.annotations.UpdateTimestamp;

@ToString(onlyExplicitlyIncluded = true) // Bidirectional relationship causes IDE stackoverflow
@Data
@Entity
@TypeDefs({@TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)})
@Table(
    name = "cases",
    indexes = {@Index(name = "cases_case_ref_idx", columnList = "case_ref")})
public class Case {

  @Id private UUID id;

  // This incrementing column allows us to generate a pseudorandom unique (non-colliding) caseRef
  @Column(columnDefinition = "serial")
  @Generated(GenerationTime.INSERT)
  private int secretSequenceNumber;

  @Column(name = "case_ref")
  private Long caseRef;

  @Column(columnDefinition = "timestamp with time zone")
  @CreationTimestamp
  private OffsetDateTime createdAt;

  @Column(columnDefinition = "timestamp with time zone")
  @UpdateTimestamp
  private OffsetDateTime lastUpdatedAt;

  @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT false")
  private boolean receiptReceived;

  @Enumerated(EnumType.STRING)
  @Column
  private RefusalType refusalReceived;

  @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT false")
  private boolean invalid;

  @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT false")
  private boolean surveyLaunched;

  @Type(type = "jsonb")
  @Column(columnDefinition = "jsonb")
  private Map<String, String> sample;

  @Type(type = "jsonb")
  @Column(columnDefinition = "jsonb")
  private Map<String, String> sampleSensitive;

  @ManyToOne private CollectionExercise collectionExercise;

  @OneToMany(mappedBy = "caze")
  List<UacQidLink> uacQidLinks;

  @OneToMany(mappedBy = "caze")
  List<Event> events;
}
