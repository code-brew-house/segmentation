package com.workflow.segment.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.UUID;

@Entity
@Table(name = "data_mart_column")
@Getter @Setter @NoArgsConstructor
public class DataMartColumn {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "data_mart_id", nullable = false)
    @JsonIgnore
    private DataMart dataMart;

    @Column(nullable = false)
    private String columnName;

    @Column(nullable = false)
    private String dataType;

    @Column(columnDefinition = "TEXT")
    private String description;

    private Integer ordinalPosition;
}
