package com.workflow.segment.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "data_mart")
@Getter @Setter @NoArgsConstructor
public class DataMart {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String tableName;

    @Column(nullable = false)
    private String schemaName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @OneToMany(mappedBy = "dataMart", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordinalPosition")
    private List<DataMartColumn> columns = new ArrayList<>();
}
