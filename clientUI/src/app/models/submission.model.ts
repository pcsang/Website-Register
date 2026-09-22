/**
 * Status values a submission can have, mirroring the backend's
 * `com.register.backend.enums.SubmissionStatus` enum exactly (4-state model as of the D2 backend
 * change: `PENDING_CONSULTATION` → `CONFIRMED` → `IN_PROGRESS` → `GRADUATED`).
 */
export type SubmissionStatus = 'PENDING_CONSULTATION' | 'CONFIRMED' | 'IN_PROGRESS' | 'GRADUATED';

/**
 * A submission as returned by the backend, mirroring
 * `com.register.backend.dto.response.SubmissionResponse`.
 * `createdAt`/`updatedAt` are ISO-8601 timestamp strings as serialized by Jackson
 * (e.g. "2026-09-08T10:15:30.123456") — kept as `string`, not parsed into `Date`.
 */
export interface Submission {
  id: number;
  fullName: string;
  email: string | null;
  phone: string | null;
  message: string | null;
  status: SubmissionStatus;
  courseId: number | null;
  assignedToId: number | null;
  createdAt: string;
  updatedAt: string;
}

/**
 * Request body for creating a new submission, mirroring
 * `com.register.backend.dto.request.CreateSubmissionRequest`.
 * `fullName` is required; `email`, `phone`, `message`, and `courseId` are all optional on the
 * backend (`courseId` isn't used by `/form` yet — it's wired here for the future landing-page
 * registration flow).
 */
export interface CreateSubmissionRequest {
  fullName: string;
  email?: string;
  phone?: string;
  message?: string;
  courseId?: number;
}

/**
 * Request body for updating a submission's status, mirroring
 * `com.register.backend.dto.request.UpdateSubmissionStatusRequest`.
 */
export interface UpdateSubmissionStatusRequest {
  status: SubmissionStatus;
}

/**
 * Request body for assigning (or un-assigning, via `null`) a submission to an admin/consultant
 * account, mirroring `com.register.backend.dto.request.AssignSubmissionRequest`.
 */
export interface AssignSubmissionRequest {
  adminUserId: number | null;
}
