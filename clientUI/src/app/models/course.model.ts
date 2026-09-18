/**
 * Driving license classes a course can be offered for, mirroring
 * `com.register.backend.enums.LicenseClass` exactly.
 */
export type LicenseClass = 'B1' | 'B2' | 'C';

/**
 * Derived (never persisted) seat-availability label for a course, mirroring
 * `com.register.backend.enums.CourseAvailabilityStatus` exactly.
 */
export type CourseAvailabilityStatus = 'AVAILABLE' | 'FILLING_UP' | 'FULL';

/**
 * A course as returned by the backend, mirroring `com.register.backend.dto.response.CourseResponse`.
 * `seatsRegistered`/`availabilityStatus` are computed server-side at read time, never stored.
 * `createdAt`/`updatedAt`/`startDate` are ISO-8601 date/timestamp strings as serialized by Jackson,
 * kept as `string`, not parsed into `Date`.
 */
export interface Course {
  id: number;
  name: string;
  licenseClass: LicenseClass;
  price: number;
  durationMonths: number;
  practiceHours: number;
  description: string | null;
  branch: string | null;
  teacherName: string | null;
  seatsTotal: number;
  seatsRegistered: number;
  availabilityStatus: CourseAvailabilityStatus;
  startDate: string;
  createdAt: string;
  updatedAt: string;
}

/**
 * Request body for creating a new course, mirroring
 * `com.register.backend.dto.request.CreateCourseRequest` exactly.
 */
export interface CreateCourseRequest {
  name: string;
  licenseClass: LicenseClass;
  price: number;
  durationMonths: number;
  practiceHours: number;
  description?: string;
  branch?: string;
  teacherName?: string;
  seatsTotal: number;
  startDate: string;
}

/**
 * Request body for updating an existing course (full field replacement), mirroring
 * `com.register.backend.dto.request.UpdateCourseRequest` exactly.
 */
export interface UpdateCourseRequest {
  name: string;
  licenseClass: LicenseClass;
  price: number;
  durationMonths: number;
  practiceHours: number;
  description?: string;
  branch?: string;
  teacherName?: string;
  seatsTotal: number;
  startDate: string;
}
