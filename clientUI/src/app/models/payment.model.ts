/**
 * Status values a payment can have, mirroring the backend's
 * `com.register.backend.enums.PaymentStatus` enum exactly.
 */
export type PaymentStatus = 'PENDING' | 'PAID' | 'CANCELLED';

/**
 * A payment as returned by the backend, mirroring
 * `com.register.backend.dto.response.PaymentResponse`.
 * `paidAt` is `null` until the SePay webhook marks the payment `PAID`. `createdAt`/`updatedAt`/
 * `paidAt` are ISO-8601 timestamp strings as serialized by Jackson, kept as `string`, not parsed
 * into `Date`.
 */
export interface Payment {
  id: number;
  submissionId: number;
  amount: number;
  status: PaymentStatus;
  paymentCode: string;
  qrImageUrl: string;
  paidAt: string | null;
  createdAt: string;
  updatedAt: string;
}
