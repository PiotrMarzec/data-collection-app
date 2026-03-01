import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  EditRequest,
  GenerateLinkResponse,
  LoginResponse,
  PagedResponse,
  SubmissionDto,
  SubmissionUpdateDto,
} from '../models/submission.model';

@Injectable({ providedIn: 'root' })
export class AdminApiService {
  private readonly http = inject(HttpClient);

  readonly token = signal<string | null>(sessionStorage.getItem('admin_token'));

  private get authHeaders(): HttpHeaders {
    return new HttpHeaders({
      'Content-Type': 'application/json',
      Authorization: `Bearer ${this.token()}`,
    });
  }

  setToken(t: string): void {
    this.token.set(t);
    sessionStorage.setItem('admin_token', t);
  }

  clearToken(): void {
    this.token.set(null);
    sessionStorage.removeItem('admin_token');
  }

  login(password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>('/admin/login', { password });
  }

  logout(): Observable<void> {
    return this.http.post<void>('/admin/logout', {}, { headers: this.authHeaders });
  }

  getSubmissions(
    page: number,
    size: number,
    search?: string
  ): Observable<PagedResponse<SubmissionDto>> {
    const params: Record<string, string> = { page: String(page), size: String(size) };
    if (search) params['search'] = search;
    return this.http.get<PagedResponse<SubmissionDto>>('/admin/submissions', {
      headers: this.authHeaders,
      params,
    });
  }

  getHistory(id: number): Observable<SubmissionUpdateDto[]> {
    return this.http.get<SubmissionUpdateDto[]>(`/admin/submissions/${id}/history`, {
      headers: this.authHeaders,
    });
  }

  editSubmission(id: number, req: EditRequest): Observable<SubmissionDto> {
    return this.http.put<SubmissionDto>(`/admin/submissions/${id}`, req, {
      headers: this.authHeaders,
    });
  }

  generateLink(dataId: string): Observable<GenerateLinkResponse> {
    return this.http.post<GenerateLinkResponse>('/admin/generate-link', { dataId }, { headers: this.authHeaders });
  }
}
