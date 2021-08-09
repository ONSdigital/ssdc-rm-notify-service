package uk.gov.ons.ssdc.notifysvc.model.entity;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import lombok.Data;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.TypeDefs;

import javax.persistence.*;
import java.util.Map;
import java.util.UUID;

@Data
@Entity
@TypeDefs({@TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)})
public class JobRow {
  @Id private UUID id;

  @ManyToOne private Job job;

  @Type(type = "jsonb")
  @Column(columnDefinition = "jsonb")
  private Map<String, String> rowData;

  @Column private String[] originalRowData;

  @Column private int originalRowLineNumber;

  @Enumerated(EnumType.STRING)
  @Column
  private JobRowStatus jobRowStatus;

  @Column private String validationErrorDescriptions;
}
