package com.bank.audit.config;

import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.ValidationAction;
import com.mongodb.client.model.ValidationLevel;
import com.mongodb.client.model.ValidationOptions;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

/**
 * Ensures the audit_events collection exists with a schema validator
 * that enforces all required fields are present (Req 6.2, 6.3).
 *
 * Append-only enforcement strategy:
 * 1. The validator requires all mandatory fields on insert.
 * 2. AuditServiceImpl uses MongoTemplate.insert() exclusively — never update/replace.
 * 3. No application code calls update or delete on this collection.
 * 4. In production, the MongoDB user for audit-service has INSERT-only privileges
 *    (configured via MongoDB role-based access control at the infrastructure level).
 *
 * Retention: TTL index on timestamp with 7-year expiry (Req 6.5).
 * 7 years = 2555 days = 220,752,000 seconds.
 */
@Component
public class MongoCollectionConfig {

    private static final Logger log = LoggerFactory.getLogger(MongoCollectionConfig.class);
    private static final String COLLECTION = "audit_events";

    // 7 years in seconds: 7 * 365 * 24 * 3600 = 220,752,000
    private static final long SEVEN_YEARS_SECONDS = 220_752_000L;

    private final MongoTemplate mongoTemplate;

    public MongoCollectionConfig(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureAuditCollection() {
        if (!mongoTemplate.collectionExists(COLLECTION)) {
            // Create collection with JSON Schema validator requiring all mandatory fields
            Document validator = new Document("$jsonSchema", new Document()
                    .append("bsonType", "object")
                    .append("required", java.util.List.of(
                            "eventId", "correlationId", "serviceOrigin", "action",
                            "actorId", "resourceId", "ipAddress", "timestamp"))
                    .append("properties", new Document()
                            .append("eventId",       new Document("bsonType", "string"))
                            .append("correlationId", new Document("bsonType", "string"))
                            .append("serviceOrigin", new Document("bsonType", "string"))
                            .append("action",        new Document("bsonType", "string"))
                            .append("actorId",       new Document("bsonType", "string"))
                            .append("resourceId",    new Document("bsonType", "string"))
                            .append("ipAddress",     new Document("bsonType", "string"))
                            .append("timestamp",     new Document("bsonType", "date"))
                            .append("immutable",     new Document("bsonType", "bool"))
                    )
            );

            mongoTemplate.getDb().createCollection(COLLECTION, new CreateCollectionOptions()
                    .validationOptions(new ValidationOptions()
                            .validator(validator)
                            .validationLevel(ValidationLevel.STRICT)
                            .validationAction(ValidationAction.ERROR)));

            log.info("Created audit_events collection with schema validator (append-only enforcement)");
        } else {
            log.info("audit_events collection already exists");
        }

        // Ensure TTL index for 7-year retention (Req 6.5)
        ensureTtlIndex();
    }

    /**
     * Creates a TTL index on the timestamp field with 7-year expiry (Req 6.5).
     * MongoDB will automatically delete documents older than 7 years.
     * expireAfterSeconds = 220,752,000 (7 * 365 * 24 * 3600)
     */
    private void ensureTtlIndex() {
        try {
            mongoTemplate.indexOps(COLLECTION)
                    .ensureIndex(new Index()
                            .on("timestamp", Sort.Direction.ASC)
                            .expire(java.time.Duration.ofSeconds(SEVEN_YEARS_SECONDS))
                            .named("idx_timestamp_ttl_7years"));
            log.info("TTL index ensured on audit_events.timestamp (expireAfterSeconds={})", SEVEN_YEARS_SECONDS);
        } catch (Exception e) {
            log.warn("Could not ensure TTL index on audit_events — may already exist with different options: {}", e.getMessage());
        }
    }
}
