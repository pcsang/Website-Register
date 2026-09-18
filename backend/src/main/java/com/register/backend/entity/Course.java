package com.register.backend.entity;

import com.register.backend.enums.LicenseClass;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A driving course offering (e.g. a B1/B2/C license class run at a given branch). Deliberately does not
 * store {@code seatsRegistered} or a derived "Còn chỗ/Sắp đầy/Đã đầy" status column - both are computed at
 * read time from a {@code COUNT} of {@code Submission} rows by {@code courseId} (see
 * {@code CourseService}/{@code CourseMapper}), to avoid a second source of truth that could drift from the
 * real count.
 */
@Entity
@Table(name = "courses")
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 200)
    private String name;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "license_class", nullable = false, length = 20)
    private LicenseClass licenseClass;

    @NotNull
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @NotNull
    @Column(name = "duration_months", nullable = false)
    private Integer durationMonths;

    @NotNull
    @Column(name = "practice_hours", nullable = false)
    private Integer practiceHours;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 100)
    private String branch;

    @Column(name = "teacher_name", length = 200)
    private String teacherName;

    @NotNull
    @Column(name = "seats_total", nullable = false)
    private Integer seatsTotal;

    @NotNull
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LicenseClass getLicenseClass() {
        return licenseClass;
    }

    public void setLicenseClass(LicenseClass licenseClass) {
        this.licenseClass = licenseClass;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getDurationMonths() {
        return durationMonths;
    }

    public void setDurationMonths(Integer durationMonths) {
        this.durationMonths = durationMonths;
    }

    public Integer getPracticeHours() {
        return practiceHours;
    }

    public void setPracticeHours(Integer practiceHours) {
        this.practiceHours = practiceHours;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public String getTeacherName() {
        return teacherName;
    }

    public void setTeacherName(String teacherName) {
        this.teacherName = teacherName;
    }

    public Integer getSeatsTotal() {
        return seatsTotal;
    }

    public void setSeatsTotal(Integer seatsTotal) {
        this.seatsTotal = seatsTotal;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

}
