package com.register.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Single-row admin-configurable settings backing the dashboard overview's non-transactional KPIs.
 *
 * <p>{@code passRatePercent} is not computed from any real data - nothing in this system tracks exam
 * results - it is entered directly by an admin via {@code PATCH /api/admin/dashboard/settings}.
 * {@code examCount} is an optional companion figure for the "trên N lượt thi" framing. Both are nullable,
 * meaning "not yet configured", rather than a fabricated default.
 *
 * <p>There is always exactly one row, with a fixed id of {@code 1} (see {@code DashboardService}), seeded
 * by {@code V4__add_dashboard_settings.sql} - not an auto-generated identity, since a single-row table has
 * no need for one.
 */
@Entity
@Table(name = "dashboard_settings")
public class DashboardSettings {

    @Id
    private Long id;

    @Column(name = "pass_rate_percent", precision = 5, scale = 2)
    private BigDecimal passRatePercent;

    @Column(name = "exam_count")
    private Integer examCount;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public BigDecimal getPassRatePercent() {
        return passRatePercent;
    }

    public void setPassRatePercent(BigDecimal passRatePercent) {
        this.passRatePercent = passRatePercent;
    }

    public Integer getExamCount() {
        return examCount;
    }

    public void setExamCount(Integer examCount) {
        this.examCount = examCount;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

}
