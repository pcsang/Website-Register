/**
 * A single internal note on a submission's timeline, mirroring
 * `com.register.backend.dto.response.SubmissionNoteResponse`. `createdAt` is an ISO-8601 timestamp
 * string as serialized by Jackson, kept as `string`, not parsed into `Date`.
 */
export interface SubmissionNote {
  id: number;
  submissionId: number;
  authorId: number;
  authorUsername: string;
  content: string;
  createdAt: string;
}

/**
 * Request body for adding a new internal note to a submission, mirroring
 * `com.register.backend.dto.request.CreateSubmissionNoteRequest`.
 */
export interface CreateSubmissionNoteRequest {
  content: string;
}
