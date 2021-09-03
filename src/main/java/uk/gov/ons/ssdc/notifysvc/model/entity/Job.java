package uk.gov.ons.ssdc.notifysvc.model.entity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Data
@Entity
public class Job {
  @Id private UUID id;

  @ManyToOne(optional = false)
  private CollectionExercise collectionExercise;

  @Column(columnDefinition = "timestamp with time zone")
  @CreationTimestamp
  private OffsetDateTime createdAt;

  @Column(nullable = false)
  private String createdBy;

  @Column(columnDefinition = "timestamp with time zone")
  @UpdateTimestamp
  private OffsetDateTime lastUpdatedAt;

  @Column(nullable = false)
  private String fileName;

  @Column(nullable = false)
  private UUID fileId;

  @Column(nullable = false)
  private int fileRowCount;

  @Column(nullable = false)
  private int stagingRowNumber;

  @Column(nullable = false)
  private int processingRowNumber;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private JobStatus jobStatus;

  @OneToMany(mappedBy = "job")
  private List<JobRow> jobRows;

  @Column private String fatalErrorDescription;
}
