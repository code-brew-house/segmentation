package com.workflow.segment.repository;

import com.workflow.segment.model.DataMart;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface DataMartRepository extends JpaRepository<DataMart, UUID> {
    Optional<DataMart> findByTableName(String tableName);
    boolean existsByTableNameAndSchemaName(String tableName, String schemaName);
}
