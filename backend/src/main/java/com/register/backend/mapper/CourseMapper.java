package com.register.backend.mapper;

import com.register.backend.dto.request.CreateCourseRequest;
import com.register.backend.dto.request.UpdateCourseRequest;
import com.register.backend.dto.response.CourseResponse;
import com.register.backend.entity.Course;
import com.register.backend.enums.CourseAvailabilityStatus;
import org.springframework.stereotype.Component;

@Component
public class CourseMapper {

    /** Seat-fill ratio (percent) at/above which a course is considered "Sắp đầy" (filling up). */
    private static final int FILLING_UP_THRESHOLD_PERCENT = 70;

    /**
     * Maps a create request into a new, unpersisted {@link Course} entity.
     *
     * @param request the validated create request
     * @return a new entity populated from the request
     */
    public Course toEntity(CreateCourseRequest request) {
        Course course = new Course();
        course.setName(request.name());
        course.setLicenseClass(request.licenseClass());
        course.setPrice(request.price());
        course.setDurationMonths(request.durationMonths());
        course.setPracticeHours(request.practiceHours());
        course.setDescription(request.description());
        course.setBranch(request.branch());
        course.setTeacherName(request.teacherName());
        course.setSeatsTotal(request.seatsTotal());
        course.setStartDate(request.startDate());
        return course;
    }

    /**
     * Applies every editable field from an update request onto an existing, already-persisted
     * {@link Course} entity (full replacement of editable fields).
     *
     * @param course  the existing entity to update in place
     * @param request the validated update request
     */
    public void applyUpdate(Course course, UpdateCourseRequest request) {
        course.setName(request.name());
        course.setLicenseClass(request.licenseClass());
        course.setPrice(request.price());
        course.setDurationMonths(request.durationMonths());
        course.setPracticeHours(request.practiceHours());
        course.setDescription(request.description());
        course.setBranch(request.branch());
        course.setTeacherName(request.teacherName());
        course.setSeatsTotal(request.seatsTotal());
        course.setStartDate(request.startDate());
    }

    /**
     * Maps a {@link Course} entity to its response DTO, including a live {@code seatsRegistered} count and
     * the seat-availability status derived from it.
     *
     * @param course          the entity to map
     * @param seatsRegistered the number of submissions currently counting as a registered seat for this
     *                        course (see {@code CourseService})
     * @return the mapped response
     */
    public CourseResponse toResponse(Course course, long seatsRegistered) {
        return new CourseResponse(
                course.getId(),
                course.getName(),
                course.getLicenseClass(),
                course.getPrice(),
                course.getDurationMonths(),
                course.getPracticeHours(),
                course.getDescription(),
                course.getBranch(),
                course.getTeacherName(),
                course.getSeatsTotal(),
                seatsRegistered,
                deriveAvailabilityStatus(seatsRegistered, course.getSeatsTotal()),
                course.getStartDate(),
                course.getCreatedAt(),
                course.getUpdatedAt()
        );
    }

    /**
     * Derives the seat-availability status from a course's registered/total seat counts - "Đã đầy" once
     * registrations reach or exceed capacity, "Sắp đầy" from 70% up to (but not reaching) capacity,
     * otherwise "Còn chỗ". Integer arithmetic is used (rather than a floating-point ratio) to avoid any
     * rounding ambiguity at the exact 70%/100% boundaries.
     *
     * @param seatsRegistered the number of registered seats
     * @param seatsTotal      the course's total seat capacity
     * @return the derived availability status
     */
    private CourseAvailabilityStatus deriveAvailabilityStatus(long seatsRegistered, int seatsTotal) {
        if (seatsTotal <= 0 || seatsRegistered >= seatsTotal) {
            return CourseAvailabilityStatus.FULL;
        }
        if (seatsRegistered * 100L >= (long) seatsTotal * FILLING_UP_THRESHOLD_PERCENT) {
            return CourseAvailabilityStatus.FILLING_UP;
        }
        return CourseAvailabilityStatus.AVAILABLE;
    }

}
