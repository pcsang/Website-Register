/**
 * Status values a submission can have, mirroring the backend's
 * `com.register.backend.enums.SubmissionStatus` enum exactly.
 */
export type SubmissionStatus = 'NEW' | 'IN_PROGRESS' | 'COMPLETED';

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
  createdAt: string;
  updatedAt: string;
}

/**
 * Request body for creating a new submission, mirroring
 * `com.register.backend.dto.request.CreateSubmissionRequest`.
 * `fullName` is required; `email`, `phone`, and `message` are optional on the backend.
 */
export interface CreateSubmissionRequest {
  fullName: string;
  email?: string;
  phone?: string;
  message?: string;
}

/**
 * Request body for updating a submission's status, mirroring
 * `com.register.backend.dto.request.UpdateSubmissionStatusRequest`.
 */
export interface UpdateSubmissionStatusRequest {
  status: SubmissionStatus;
}
