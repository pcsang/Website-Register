package com.register.backend.mapper;

import com.register.backend.entity.Course;
import com.register.backend.enums.CourseAvailabilityStatus;
import com.register.backend.enums.LicenseClass;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers {@link CourseMapper#toResponse(Course, long)}'s derived {@code seatsRegistered}/
 * {@code availabilityStatus} mapping, including the exact 70%/100% fill-ratio boundaries.
 */
class CourseMapperTest {

    private final CourseMapper courseMapper = new CourseMapper();

    private static Course sampleCourse(int seatsTotal) {
        Course course = new Course();
        course.setId(1L);
        course.setName("B1 Automatic - Downtown");
        course.setLicenseClass(LicenseClass.B1);
        course.setPrice(new BigDecimal("12000000"));
        course.setDurationMonths(3);
        course.setPracticeHours(40);
        course.setSeatsTotal(seatsTotal);
        course.setStartDate(LocalDate.now().plusMonths(1));
        return course;
    }

    @Test
    void zeroSeatsRegisteredIsAvailable() {
        CourseAvailabilityStatus status = courseMapper.toResponse(sampleCourse(30), 0L).availabilityStatus();

        assertThat(status).isEqualTo(CourseAvailabilityStatus.AVAILABLE);
    }

    @Test
    void justBelowSeventyPercentIsAvailable() {
        // 20/30 = 66.6% - just under the 70% "filling up" threshold.
        CourseAvailabilityStatus status = courseMapper.toResponse(sampleCourse(30), 20L).availabilityStatus();

        assertThat(status).isEqualTo(CourseAvailabilityStatus.AVAILABLE);
    }

    @Test
    void exactlySeventyPercentIsFillingUp() {
        CourseAvailabilityStatus status = courseMapper.toResponse(sampleCourse(30), 21L).availabilityStatus();

        assertThat(status).isEqualTo(CourseAvailabilityStatus.FILLING_UP);
    }

    @Test
    void justBelowFullCapacityIsFillingUp() {
        CourseAvailabilityStatus status = courseMapper.toResponse(sampleCourse(30), 29L).availabilityStatus();

        assertThat(status).isEqualTo(CourseAvailabilityStatus.FILLING_UP);
    }

    @Test
    void exactlyFullCapacityIsFull() {
        CourseAvailabilityStatus status = courseMapper.toResponse(sampleCourse(30), 30L).availabilityStatus();

        assertThat(status).isEqualTo(CourseAvailabilityStatus.FULL);
    }

    @Test
    void overCapacityIsStillFull() {
        CourseAvailabilityStatus status = courseMapper.toResponse(sampleCourse(30), 35L).availabilityStatus();

        assertThat(status).isEqualTo(CourseAvailabilityStatus.FULL);
    }

    @Test
    void seatsRegisteredIsCarriedThroughToTheResponseUnchanged() {
        long seatsRegistered = courseMapper.toResponse(sampleCourse(30), 21L).seatsRegistered();

        assertThat(seatsRegistered).isEqualTo(21L);
    }

}
