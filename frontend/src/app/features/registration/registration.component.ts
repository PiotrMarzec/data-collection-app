import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { INITIAL_SEARCH } from '../../app.config';
import { SubmissionApiService } from '../../core/services/submission-api.service';
import { SubmitResponse, SubmissionStatus, VerifyResponse } from '../../core/models/submission.model';

type Step = 'loading' | 'welcome' | 'welcome-update' | 'email' | 'locked' | 'success' | 'error';

interface LockedState {
  status: SubmissionStatus;
  email: string;
  resultUrl: string | null;
  expirationDate: string | null;
}

@Component({
  selector: 'app-registration',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './registration.component.html',
  styleUrl: './registration.component.css',
})
export class RegistrationComponent implements OnInit {
  private readonly initialSearch = inject(INITIAL_SEARCH);
  private readonly api = inject(SubmissionApiService);

  step = signal<Step>('loading');
  isUpdate = signal(false);
  currentEmail = signal('');
  updatesRemaining = signal(0);
  errorMessage = signal('');
  emailError = signal('');
  emailValue = '';
  submitting = signal(false);

  successTitle = signal("You're all set!");
  successMessage = signal(
    'Your email has been registered successfully. You can close this page.'
  );
  lockedState = signal<LockedState | null>(null);

  readonly updatesRemainingText = computed(() => {
    const r = this.updatesRemaining();
    return `You have ${r} email update${r !== 1 ? 's' : ''} remaining.`;
  });

  private dataId = '';
  private signature = '';

  ngOnInit(): void {
    const params = new URLSearchParams(this.initialSearch);
    this.dataId = params.get('dataId') ?? '';
    this.signature = params.get('signature') ?? '';
    this.verifyLink();
  }

  verifyLink(): void {
    if (!this.dataId || !this.signature) {
      this.showError('Missing data ID or signature in the link.');
      return;
    }
    this.api.verify(this.dataId, this.signature).subscribe({
      next: data => this.handleVerifyResponse(data),
      error: err => {
        const msg = (err.error as VerifyResponse)?.error ?? 'Unable to verify your link. Please try again later.';
        this.showError(msg);
      },
    });
  }

  private handleVerifyResponse(data: VerifyResponse): void {
    if (data.locked) {
      this.lockedState.set({
        status: data.status!,
        email: data.currentEmail ?? '',
        resultUrl: data.resultUrl ?? null,
        expirationDate: data.expirationDate ?? null,
      });
      this.step.set('locked');
      return;
    }

    if (data.isUpdate) {
      this.isUpdate.set(true);
      this.currentEmail.set(data.currentEmail ?? '');
      this.updatesRemaining.set(data.updatesRemaining ?? 0);
      this.step.set('welcome-update');
    } else {
      this.step.set('welcome');
    }
  }

  showError(msg: string): void {
    this.errorMessage.set(msg);
    this.step.set('error');
  }

  goToEmail(): void {
    this.step.set('email');
  }

  submitEmail(): void {
    const email = this.emailValue.trim();
    this.emailError.set('');

    if (!email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      this.emailError.set('Please enter a valid email address.');
      return;
    }

    this.submitting.set(true);
    this.api.submit(this.dataId, this.signature, email).subscribe({
      next: data => {
        this.submitting.set(false);
        if (data.success) {
          if (this.isUpdate()) {
            this.successTitle.set('Email updated!');
            let msg = 'Your email has been updated successfully.';
            if (data.updatesRemaining !== undefined) {
              const r = data.updatesRemaining;
              msg += ` You have ${r} update${r !== 1 ? 's' : ''} remaining.`;
            }
            this.successMessage.set(msg);
          }
          this.step.set('success');
        } else {
          this.handleSubmitError(data);
        }
      },
      error: err => {
        this.submitting.set(false);
        const data = err.error as Partial<SubmitResponse>;
        if (data?.maxUpdatesReached || data?.rateLimited || data?.error) {
          this.handleSubmitError(data);
        } else {
          this.emailError.set('Network error. Please try again.');
        }
      },
    });
  }

  private handleSubmitError(data: Partial<SubmitResponse>): void {
    if (data.maxUpdatesReached) {
      this.showError('Maximum number of email updates reached. No further changes are allowed.');
    } else if (data.rateLimited) {
      this.showError('Too many submissions from your IP address. Please try again later.');
    } else {
      this.emailError.set(data.error ?? 'Submission failed. Please try again.');
    }
  }

  formatExpiryInfo(expirationDate: string): { text: string; expiringSoon: boolean } {
    const expiry = new Date(expirationDate);
    const daysLeft = Math.ceil((expiry.getTime() - Date.now()) / 86_400_000);
    const dateStr = expiry.toLocaleDateString(undefined, {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
    });
    const text =
      daysLeft > 0
        ? `Results available until ${dateStr} — ${daysLeft} day${daysLeft !== 1 ? 's' : ''} remaining`
        : `Results expired on ${dateStr}`;
    return { text, expiringSoon: daysLeft <= 7 };
  }
}
