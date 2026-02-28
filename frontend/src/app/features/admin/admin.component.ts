import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AdminApiService } from '../../core/services/admin-api.service';
import { AdminPanelComponent } from './admin-panel.component';

@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [FormsModule, AdminPanelComponent],
  templateUrl: './admin.component.html',
  styleUrl: './admin.component.css',
})
export class AdminComponent {
  protected readonly adminApi = inject(AdminApiService);

  password = '';
  loginError = signal('');
  loggingIn = signal(false);

  get isLoggedIn(): boolean {
    return !!this.adminApi.token();
  }

  doLogin(): void {
    this.loginError.set('');
    this.loggingIn.set(true);
    this.adminApi.login(this.password).subscribe({
      next: data => {
        this.loggingIn.set(false);
        this.adminApi.setToken(data.token);
        this.password = '';
      },
      error: () => {
        this.loggingIn.set(false);
        this.loginError.set('Incorrect password.');
      },
    });
  }

  doLogout(): void {
    this.adminApi.logout().subscribe({
      complete: () => this.adminApi.clearToken(),
      error: () => this.adminApi.clearToken(),
    });
  }

  onSessionExpired(): void {
    this.loginError.set('Session expired. Please sign in again.');
  }
}
