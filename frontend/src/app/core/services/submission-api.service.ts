import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { VerifyResponse, SubmitResponse } from '../models/submission.model';

@Injectable({ providedIn: 'root' })
export class SubmissionApiService {
  private readonly http = inject(HttpClient);

  verify(dataId: string, signature: string): Observable<VerifyResponse> {
    return this.http.get<VerifyResponse>('/api/verify', {
      params: { dataId, signature },
    });
  }

  submit(dataId: string, signature: string, email: string): Observable<SubmitResponse> {
    return this.http.post<SubmitResponse>('/api/submit', { dataId, signature, email });
  }
}
