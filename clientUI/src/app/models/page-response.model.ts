/**
 * Generic paginated response envelope for list endpoints, mirroring
 * `com.register.backend.dto.response.PageResponse<T>`.
 */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
