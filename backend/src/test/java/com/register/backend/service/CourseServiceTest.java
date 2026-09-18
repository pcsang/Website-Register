package com.register.backend.service;

import com.register.backend.dto.request.CreateCourseRequest;
import com.register.backend.dto.request.UpdateCourseRequest;
import com.register.backend.dto.response.CourseResponse;
import com.register.backend.dto.response.PageResponse;
import com.register.backend.entity.Course;
import com.register.backend.enums.CourseAvailabilityStatus;
import com.register.backend.enums.LicenseClass;
import com.register.backend.enums.SubmissionStatus;
import com.register.backend.exception.ResourceNotFoundException;
import com.register.backend.mapper.CourseMapper;
import com.register.backend.repository.CourseRepository;
import com.register.backend.repository.SubmissionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseMapper courseMapper;

    @Mock
    private SubmissionRepository submissionRepository;

    @InjectMocks
    private CourseService courseService;

    private static CreateCourseRequest sampleCreateRequest() {
        return new CreateCourseRequest(
                "B1 Automatic - Downtown", LicenseClass.B1, new BigDecimal("12000000"), 3, 40,
                "Beginner-friendly automatic course", "Downtown", "Ms. Lan", 30, LocalDate.now().plusMonths(1));
    }

    private static Course sampleCourse(Long id) {
        Course course = new Course();
        course.setId(id);
        course.setName("B1 Automatic - Downtown");
        course.setLicenseClass(LicenseClass.B1);
        course.setPrice(new BigDecimal("12000000"));
        course.setDurationMonths(3);
        course.setPracticeHours(40);
        course.setDescription("Beginner-friendly automatic course");
        course.setBranch("Downtown");
        course.setTeacherName("Ms. Lan");
        course.setSeatsTotal(30);
        course.setStartDate(LocalDate.now().plusMonths(1));
        return course;
    }

    private static CourseResponse sampleResponse(Long id) {
        return new CourseResponse(
                id, "B1 Automatic - Downtown", LicenseClass.B1, new BigDecimal("12000000"), 3, 40,
                "Beginner-friendly automatic course", "Downtown", "Ms. Lan", 30, 0L,
                CourseAvailabilityStatus.AVAILABLE, LocalDate.now().plusMonths(1),
                LocalDateTime.now(), LocalDateTime.now());
    }

    @Test
    void createCourseSavesAndReturnsMappedResponseWhenRequestIsValid() {
        CreateCourseRequest request = sampleCreateRequest();
        Course mappedEntity = sampleCourse(null);
        Course savedCourse = sampleCourse(1L);
        CourseResponse expectedResponse = sampleResponse(1L);

        when(courseMapper.toEntity(request)).thenReturn(mappedEntity);
        when(courseRepository.save(mappedEntity)).thenReturn(savedCourse);
        when(submissionRepository.countByCourseIdAndStatusIn(eq(1L), any())).thenReturn(0L);
        when(courseMapper.toResponse(savedCourse, 0L)).thenReturn(expectedResponse);

        CourseResponse actual = courseService.createCourse(request);

        assertThat(actual).isEqualTo(expectedResponse);
        verify(courseMapper).toEntity(request);
        verify(courseRepository).save(mappedEntity);
        verify(courseMapper).toResponse(savedCourse, 0L);
    }

    @Test
    void listCoursesReturnsMappedPageResponse() {
        Pageable pageable = PageRequest.of(0, 10);
        Course course = sampleCourse(1L);
        Page<Course> page = new PageImpl<>(List.of(course), pageable, 1);
        CourseResponse mappedResponse = sampleResponse(1L);

        when(courseRepository.findAll(pageable)).thenReturn(page);
        when(submissionRepository.countByCourseIdAndStatusIn(eq(1L), any())).thenReturn(0L);
        when(courseMapper.toResponse(course, 0L)).thenReturn(mappedResponse);

        PageResponse<CourseResponse> actual = courseService.listCourses(pageable);

        assertThat(actual.content()).containsExactly(mappedResponse);
        assertThat(actual.page()).isEqualTo(0);
        assertThat(actual.size()).isEqualTo(10);
        assertThat(actual.totalElements()).isEqualTo(1L);
        assertThat(actual.totalPages()).isEqualTo(1);
        verify(courseRepository).findAll(pageable);
    }

    @Test
    void listCoursesForAdminPassesFiltersToRepositorySearch() {
        Pageable pageable = PageRequest.of(0, 10);
        Course course = sampleCourse(1L);
        Page<Course> page = new PageImpl<>(List.of(course), pageable, 1);
        CourseResponse mappedResponse = sampleResponse(1L);

        when(courseRepository.search(LicenseClass.B1, "Downtown", pageable)).thenReturn(page);
        when(submissionRepository.countByCourseIdAndStatusIn(eq(1L), any())).thenReturn(0L);
        when(courseMapper.toResponse(course, 0L)).thenReturn(mappedResponse);

        PageResponse<CourseResponse> actual = courseService.listCoursesForAdmin(LicenseClass.B1, "Downtown", pageable);

        assertThat(actual.content()).containsExactly(mappedResponse);
        verify(courseRepository).search(LicenseClass.B1, "Downtown", pageable);
    }

    @Test
    void getCourseByIdReturnsMappedResponseWhenCourseExists() {
        Long id = 1L;
        Course course = sampleCourse(id);
        CourseResponse expectedResponse = sampleResponse(id);

        when(courseRepository.findById(id)).thenReturn(Optional.of(course));
        when(submissionRepository.countByCourseIdAndStatusIn(eq(id), any())).thenReturn(0L);
        when(courseMapper.toResponse(course, 0L)).thenReturn(expectedResponse);

        CourseResponse actual = courseService.getCourseById(id);

        assertThat(actual).isEqualTo(expectedResponse);
        verify(courseRepository).findById(id);
        verify(courseMapper).toResponse(course, 0L);
    }

    @Test
    void getCourseByIdComputesSeatsRegisteredFromConfirmedInProgressAndGraduatedSubmissionsOnly() {
        Long id = 1L;
        Course course = sampleCourse(id);
        CourseResponse expectedResponse = sampleResponse(id);

        when(courseRepository.findById(id)).thenReturn(Optional.of(course));
        when(submissionRepository.countByCourseIdAndStatusIn(eq(id), any())).thenReturn(12L);
        when(courseMapper.toResponse(course, 12L)).thenReturn(expectedResponse);

        courseService.getCourseById(id);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<SubmissionStatus>> statusesCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(submissionRepository).countByCourseIdAndStatusIn(eq(id), statusesCaptor.capture());
        assertThat(statusesCaptor.getValue()).containsExactlyInAnyOrder(
                SubmissionStatus.CONFIRMED, SubmissionStatus.IN_PROGRESS, SubmissionStatus.GRADUATED);
    }

    @Test
    void getCourseByIdThrowsResourceNotFoundExceptionWhenCourseDoesNotExist() {
        Long id = 999L;
        when(courseRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.getCourseById(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Course not found with id: " + id);

        verify(courseRepository).findById(id);
        verifyNoInteractions(courseMapper);
    }

    @Test
    void updateCourseAppliesChangesAndReturnsMappedResponseWhenCourseExists() {
        Long id = 1L;
        Course course = sampleCourse(id);
        UpdateCourseRequest request = new UpdateCourseRequest(
                "B1 Automatic - Uptown", LicenseClass.B1, new BigDecimal("13000000"), 3, 42,
                "Updated description", "Uptown", "Mr. Nam", 25, LocalDate.now().plusMonths(2));
        CourseResponse expectedResponse = sampleResponse(id);

        when(courseRepository.findById(id)).thenReturn(Optional.of(course));
        when(courseRepository.saveAndFlush(course)).thenReturn(course);
        when(submissionRepository.countByCourseIdAndStatusIn(eq(id), any())).thenReturn(0L);
        when(courseMapper.toResponse(course, 0L)).thenReturn(expectedResponse);

        CourseResponse actual = courseService.updateCourse(id, request);

        assertThat(actual).isEqualTo(expectedResponse);
        verify(courseMapper).applyUpdate(course, request);
        verify(courseRepository).findById(id);
        verify(courseRepository).saveAndFlush(course);
        verify(courseMapper).toResponse(course, 0L);
    }

    @Test
    void updateCourseThrowsResourceNotFoundExceptionWhenCourseDoesNotExist() {
        Long id = 999L;
        UpdateCourseRequest request = new UpdateCourseRequest(
                "B1 Automatic - Uptown", LicenseClass.B1, new BigDecimal("13000000"), 3, 42,
                "Updated description", "Uptown", "Mr. Nam", 25, LocalDate.now().plusMonths(2));

        when(courseRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.updateCourse(id, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Course not found with id: " + id);

        verify(courseRepository).findById(id);
        verifyNoInteractions(courseMapper);
    }

}
