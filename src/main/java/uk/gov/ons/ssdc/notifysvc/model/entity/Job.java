package uk.gov.ons.ssdc.notifysvc.model.entity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import javax.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Data
@Entity
public class Job {
  @Id private UUID id;

  @ManyToOne private CollectionExercise collectionExercise;

  @Column(columnDefinition = "timestamp with time zone")
  @CreationTimestamp
  private OffsetDateTime createdAt;

  @Column private String createdBy;

  @Column(columnDefinition = "timestamp with time zone")
  @UpdateTimestamp
  private OffsetDateTime lastUpdatedAt;

  @Column private String fileName;

  @Column private UUID fileId;

  @Column private int fileRowCount;

  @Column private int stagingRowNumber;

  @Column private int processingRowNumber;

  @Enumerated(EnumType.STRING)
  @Column
  private JobStatus jobStatus;

  @OneToMany(mappedBy = "job")
  private List<JobRow> jobRows;

  @Column private String fatalErrorDescription;
}
