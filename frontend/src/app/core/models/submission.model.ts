export type SubmissionStatus = 'NEW' | 'PROCESSING' | 'DONE' | 'EXPIRED';

export interface VerifyResponse {
  valid: boolean;
  dataId: string;
  locked?: boolean;
  status?: SubmissionStatus;
  currentEmail?: string;
  resultUrl?: string;
  expirationDate?: string;
  isUpdate?: boolean;
  updatesRemaining?: number;
  maxUpdatesReached?: boolean;
  error?: string;
}

export interface SubmitResponse {
  success: boolean;
  message?: string;
  updatesRemaining?: number;
  error?: string;
  maxUpdatesReached?: boolean;
  rateLimited?: boolean;
}

export interface SubmissionDto {
  id: number;
  dataId: string;
  email: string;
  status: SubmissionStatus;
  updateCount: number;
  ipAddress: string | null;
  submittedAt: string;
  createdAt: string;
  expirationDate: string | null;
  resultUrl: string | null;
}

export interface SubmissionUpdateDto {
  id: number;
  email: string;
  ipAddress: string | null;
  userAgent: string | null;
  createdAt: string;
}

export interface PagedResponse<T> {
  data: T[];
  total: number;
}

export interface LoginResponse {
  token: string;
}

export interface EditRequest {
  email: string;
  status?: string;
  resultUrl?: string | null;
  expirationDate?: string | null;
}
