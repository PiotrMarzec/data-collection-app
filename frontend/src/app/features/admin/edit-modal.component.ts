import { Component, EventEmitter, inject, Input, OnInit, Output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AdminApiService } from '../../core/services/admin-api.service';
import { SubmissionDto } from '../../core/models/submission.model';

@Component({
  selector: 'app-edit-modal',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './edit-modal.component.html',
  styleUrl: './edit-modal.component.css',
})
export class EditModalComponent implements OnInit {
  @Input({ required: true }) submission!: SubmissionDto;
  @Output() saved = new EventEmitter<void>();
  @Output() cancelled = new EventEmitter<void>();
  @Output() sessionExpired = new EventEmitter<void>();

  private readonly fb = inject(FormBuilder);
  private readonly adminApi = inject(AdminApiService);

  editError = signal('');
  saving = signal(false);

  form = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    status: ['NEW'],
    resultUrl: [''],
    expirationDate: [''],
  });

  ngOnInit(): void {
    const expDate = this.submission.expirationDate
      ? new Date(this.submission.expirationDate).toISOString().slice(0, 10)
      : '';
    this.form.patchValue({
      email: this.submission.email,
      status: this.submission.status,
      resultUrl: this.submission.resultUrl ?? '',
      expirationDate: expDate,
    });
  }

  get isDone(): boolean {
    return this.form.get('status')?.value === 'DONE';
  }

  save(): void {
    this.editError.set('');
    const { email, status, resultUrl, expirationDate } = this.form.value;

    if (!email || !email.includes('@')) {
      this.editError.set('Please enter a valid email address.');
      return;
    }
    if (status === 'DONE' && !resultUrl) {
      this.editError.set('Result URL is required when status is DONE.');
      return;
    }

    this.saving.set(true);
    this.adminApi
      .editSubmission(this.submission.id, {
        email: email!,
        status: status ?? undefined,
        resultUrl: resultUrl || null,
        expirationDate: expirationDate || null,
      })
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.saved.emit();
        },
        error: err => {
          this.saving.set(false);
          if (err.status === 401) {
            this.sessionExpired.emit();
          } else {
            this.editError.set('Failed to save. Please try again.');
          }
        },
      });
  }

  cancel(): void {
    this.cancelled.emit();
  }
}
