package com.workflow.segment.controller;

import com.workflow.segment.dto.*;
import com.workflow.segment.repository.DataMartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/data-marts")
@RequiredArgsConstructor
public class DataMartController {
    private final DataMartRepository dataMartRepository;

    @GetMapping
    public List<DataMartResponse> listAll() {
        return dataMartRepository.findAll().stream()
                .map(dm -> new DataMartResponse(dm.getId(), dm.getTableName(), dm.getSchemaName(), dm.getDescription(), dm.getColumns().size()))
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<DataMartDetailResponse> getById(@PathVariable UUID id) {
        return dataMartRepository.findById(id)
                .map(dm -> ResponseEntity.ok(new DataMartDetailResponse(
                        dm.getId(), dm.getTableName(), dm.getSchemaName(), dm.getDescription(),
                        dm.getColumns().stream()
                                .map(col -> new DataMartColumnResponse(col.getId(), col.getColumnName(), col.getDataType(), col.getDescription(), col.getOrdinalPosition()))
                                .toList())))
                .orElse(ResponseEntity.notFound().build());
    }
}
