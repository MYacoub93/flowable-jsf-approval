package com.example.approval.mapper;

import com.example.approval.clearance.model.AuditRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * MyBatis mapper for the {@code CLRT_AUDIT_LOG} table (primary/MySQL
 * datasource). Used exclusively by the clearance {@code AuditService}
 * implementation.
 */
@Mapper
public interface AuditLogMapper {

    int insertAuditRecord(AuditRecord record);

    List<AuditRecord> findByProcessInstanceId(@Param("processInstanceId") String processInstanceId);
}