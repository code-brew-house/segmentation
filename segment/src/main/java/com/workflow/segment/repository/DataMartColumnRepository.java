package com.workflow.segment.repository;

import com.workflow.segment.model.DataMartColumn;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface DataMartColumnRepository extends JpaRepository<DataMartColumn, UUID> {
}
