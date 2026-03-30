package com.bank.audit.repository;

import com.bank.audit.model.AuditEventDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Read-only query repository for audit events.
 * All writes go through AuditServiceImpl.recordEvent() which uses
 * MongoTemplate.insert() (never save/update) to enforce append-only semantics.
 */
@Repository
public interface AuditEventRepository extends MongoRepository<AuditEventDocument, String> {

    List<AuditEventDocument> findByCorrelationId(String correlationId);

    List<AuditEventDocument> findByActorId(String actorId);

    List<AuditEventDocument> findByResourceId(String resourceId);

    @Query("{ 'timestamp': { $gte: ?0, $lte: ?1 } }")
    List<AuditEventDocument> findByTimestampBetween(LocalDateTime from, LocalDateTime to);

    @Query("{ $and: [ " +
           "  { $or: [ { 'correlationId': ?0 }, { $expr: { $eq: [?0, null] } } ] }, " +
           "  { $or: [ { 'actorId': ?1 },      { $expr: { $eq: [?1, null] } } ] }, " +
           "  { $or: [ { 'resourceId': ?2 },   { $expr: { $eq: [?2, null] } } ] }, " +
           "  { 'timestamp': { $gte: ?3, $lte: ?4 } } " +
           "] }")
    List<AuditEventDocument> findByFilters(String correlationId,
                                           String actorId,
                                           String resourceId,
                                           LocalDateTime from,
                                           LocalDateTime to);
}
