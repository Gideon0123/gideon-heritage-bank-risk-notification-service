package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.RiskOutboxStatus;
import jakarta.persistence.*;import lombok.AccessLevel;import lombok.Getter;import lombok.NoArgsConstructor;import org.hibernate.annotations.CreationTimestamp;import org.hibernate.annotations.UpdateTimestamp;import org.hibernate.annotations.UuidGenerator;
import java.time.Instant;import java.util.UUID;
@Getter @Entity @Table(name="risk_decision_outbox",uniqueConstraints=@UniqueConstraint(name="uq_risk_decision_outbox_event",columnNames="event_id"),indexes=@Index(name="idx_risk_decision_outbox_available",columnList="status, available_at, created_at")) @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class RiskDecisionOutbox {
    @Id @GeneratedValue @UuidGenerator private UUID id;
    @Column(name="event_id",nullable=false) private UUID eventId;
    @Column(name="assessment_id",nullable=false) private UUID assessmentId;
    @Column(name="exchange_name",nullable=false,length=200) private String exchangeName;
    @Column(name="routing_key",nullable=false,length=200) private String routingKey;
    @Column(nullable=false,columnDefinition="TEXT") private String payload;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private RiskOutboxStatus status=RiskOutboxStatus.PENDING;
    @Column(name="attempt_count",nullable=false) private int attemptCount=0;
    @Column(name="max_attempts",nullable=false) private int maxAttempts=8;
    @Column(name="available_at",nullable=false) private Instant availableAt;
    @Column(name="locked_until") private Instant lockedUntil;
    @Column(name="locked_by",length=150) private String lockedBy;
    @Column(name="published_at") private Instant publishedAt;
    @Column(name="last_error",columnDefinition="TEXT") private String lastError;
    @CreationTimestamp @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @UpdateTimestamp @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @Version @Column(nullable=false) private long version;
    public static RiskDecisionOutbox create(UUID eventId,UUID assessmentId,String exchange,String key,String payload,int maxAttempts){if(eventId==null||assessmentId==null||exchange==null||exchange.isBlank()||key==null||key.isBlank()||payload==null||payload.isBlank()||maxAttempts<1)throw new IllegalArgumentException("Invalid risk decision outbox data.");RiskDecisionOutbox o=new RiskDecisionOutbox();o.eventId=eventId;o.assessmentId=assessmentId;o.exchangeName=exchange;o.routingKey=key;o.payload=payload;o.maxAttempts=maxAttempts;o.availableAt=Instant.now();return o;}
    public void claim(String worker,Instant until){if(status!=RiskOutboxStatus.PENDING&&status!=RiskOutboxStatus.RETRY_SCHEDULED)throw new IllegalStateException("Only available outbox records can be claimed.");status=RiskOutboxStatus.PROCESSING;lockedBy=worker;lockedUntil=until;attemptCount++;}
    public void published(){status=RiskOutboxStatus.PUBLISHED;publishedAt=Instant.now();lockedBy=null;lockedUntil=null;lastError=null;}
    public void retry(Instant next,String error){lockedBy=null;lockedUntil=null;lastError=error;if(attemptCount>=maxAttempts){status=RiskOutboxStatus.DEAD_LETTERED;}else{status=RiskOutboxStatus.RETRY_SCHEDULED;availableAt=next;}}
    public void recover(Instant next){lockedBy=null;lockedUntil=null;lastError="Recovered after worker lease expiry.";if(attemptCount>=maxAttempts)status=RiskOutboxStatus.DEAD_LETTERED;else{status=RiskOutboxStatus.RETRY_SCHEDULED;availableAt=next;}}
}
