package uk.gov.ons.ssdc.notifysvc.model.entity;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import java.util.Map;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import lombok.Data;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.TypeDefs;

@Data
@Entity
@TypeDefs({@TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)})
public class JobRow {
  @Id private UUID id;

  @ManyToOne(optional = false)
  private Job job;

  @Type(type = "jsonb")
  @Column(columnDefinition = "jsonb")
  private Map<String, String> rowData;

  @Column(nullable = false)
  private String[] originalRowData;

  @Column(nullable = false)
  private int originalRowLineNumber;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private JobRowStatus jobRowStatus;

  @Column private String validationErrorDescriptions;
}
