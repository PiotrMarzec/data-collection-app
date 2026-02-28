import {
  Component,
  computed,
  EventEmitter,
  inject,
  OnDestroy,
  OnInit,
  Output,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subject, Subscription } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
import { AdminApiService } from '../../core/services/admin-api.service';
import { SubmissionDto, SubmissionUpdateDto } from '../../core/models/submission.model';
import { EditModalComponent } from './edit-modal.component';

const PAGE_SIZE = 20;

@Component({
  selector: 'app-admin-panel',
  standalone: true,
  imports: [FormsModule, EditModalComponent],
  templateUrl: './admin-panel.component.html',
  styleUrl: './admin-panel.component.css',
})
export class AdminPanelComponent implements OnInit, OnDestroy {
  @Output() logout = new EventEmitter<void>();
  @Output() sessionExpired = new EventEmitter<void>();

  private readonly adminApi = inject(AdminApiService);
  private readonly searchSubject = new Subject<void>();
  private readonly sub = new Subscription();

  submissions = signal<SubmissionDto[]>([]);
  totalRecords = signal(0);
  currentPage = signal(0);
  searchValue = '';
  historyMap = signal<Map<number, SubmissionUpdateDto[]>>(new Map());
  openHistoryId = signal<number | null>(null);
  editingSubmission = signal<SubmissionDto | null>(null);

  readonly totalPages = computed(() =>
    Math.max(1, Math.ceil(this.totalRecords() / PAGE_SIZE))
  );
  readonly pageInfo = computed(
    () =>
      `Page ${this.currentPage() + 1} of ${this.totalPages()} (${this.totalRecords()} total)`
  );

  ngOnInit(): void {
    this.sub.add(
      this.searchSubject.pipe(debounceTime(350)).subscribe(() => {
        this.currentPage.set(0);
        this.loadSubmissions();
      })
    );
    this.loadSubmissions();
  }

  ngOnDestroy(): void {
    this.sub.unsubscribe();
  }

  onSearch(): void {
    this.searchSubject.next();
  }

  changePage(delta: number): void {
    const next = this.currentPage() + delta;
    if (next < 0 || next >= this.totalPages()) return;
    this.currentPage.set(next);
    this.loadSubmissions();
  }

  loadSubmissions(): void {
    const search = this.searchValue.trim() || undefined;
    this.adminApi.getSubmissions(this.currentPage(), PAGE_SIZE, search).subscribe({
      next: ({ data, total }) => {
        this.submissions.set(data);
        this.totalRecords.set(total);
        this.openHistoryId.set(null);
        this.historyMap.set(new Map());
      },
      error: err => {
        if (err.status === 401) {
          this.adminApi.clearToken();
          this.sessionExpired.emit();
        }
      },
    });
  }

  toggleHistory(id: number): void {
    if (this.openHistoryId() === id) {
      this.openHistoryId.set(null);
      return;
    }
    this.adminApi.getHistory(id).subscribe({
      next: history => {
        const map = new Map(this.historyMap());
        map.set(id, history);
        this.historyMap.set(map);
        this.openHistoryId.set(id);
      },
      error: err => {
        if (err.status === 401) {
          this.adminApi.clearToken();
          this.sessionExpired.emit();
        }
      },
    });
  }

  openEdit(submission: SubmissionDto): void {
    this.editingSubmission.set(submission);
  }

  onEditSaved(): void {
    this.editingSubmission.set(null);
    this.loadSubmissions();
  }

  onEditCancelled(): void {
    this.editingSubmission.set(null);
  }

  onEditSessionExpired(): void {
    this.editingSubmission.set(null);
    this.adminApi.clearToken();
    this.sessionExpired.emit();
  }

  getHistory(id: number): SubmissionUpdateDto[] {
    return this.historyMap().get(id) ?? [];
  }

  formatDate(iso: string | null): string {
    if (!iso) return '—';
    return new Date(iso).toLocaleString(undefined, {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  }

  truncate(str: string | null, max: number): string {
    if (!str) return '—';
    return str.length > max ? str.slice(0, max) + '…' : str;
  }

  doLogout(): void {
    this.logout.emit();
  }
}
