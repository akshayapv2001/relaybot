import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api, Guild, Report } from '../api';

@Component({
  selector: 'app-reports',
  imports: [DatePipe, FormsModule],
  template: `
    <section class="page">
      <header class="page-head">
        <h1>Reports</h1>
        <p>What people reported with <strong>/report</strong>, the priority RelayBot gave it and why.</p>
      </header>

      <div class="row">
        <label class="field">Server
          <select [(ngModel)]="guildFilter" (ngModelChange)="load()">
            <option value="">All servers</option>
            @for (g of guilds(); track g.id) { <option [value]="g.id">{{ g.name }}</option> }
          </select>
        </label>
      </div>

      @if (error()) { <p class="notice error">{{ error() }}</p> }

      <div class="table-wrap">
        <table>
          <thead>
            <tr><th>#</th><th>Priority</th><th>Report</th><th>AI triage</th><th>Status</th><th>Received</th></tr>
          </thead>
          <tbody>
            @for (r of reports(); track r.id) {
              <tr>
                <td class="num">{{ r.id }}</td>
                <td>
                  <span class="priority" [class]="r.priority ?? 'none'">{{ r.priority ? label(r.priority) : 'Pending' }}</span>
                  <div class="small muted">{{ why(r) }}</div>
                </td>
                <td class="wrap">
                  <p>{{ r.text }}</p>
                  <p class="small muted">{{ r.username }} in {{ guildName(r.guildId) }}</p>
                </td>
                <td class="wrap small">
                  @switch (r.aiStatus) {
                    @case ('DONE') { <p>{{ r.aiSummary }}</p><p class="muted">Category: {{ r.aiCategory }}</p> }
                    @case ('FAILED') { <p class="muted">AI unavailable; rules applied without it.</p> }
                    @case ('SKIPPED') { <p class="muted">AI triage off</p> }
                    @default { <p class="muted">Working…</p> }
                  }
                </td>
                <td>{{ statusLabel(r) }}</td>
                <td class="num small">{{ r.createdAt | date: 'MMM d, HH:mm' }}</td>
              </tr>
            } @empty {
              <tr><td colspan="6" class="empty">No reports yet. They appear here as soon as someone runs /report.</td></tr>
            }
          </tbody>
        </table>
      </div>
    </section>
  `,
})
export class ReportsPage implements OnInit {
  private api = inject(Api);
  guilds = signal<Guild[]>([]);
  reports = signal<Report[]>([]);
  error = signal<string | null>(null);
  guildFilter = '';
  private names = computed(() => new Map(this.guilds().map((g) => [g.id, g.name])));

  ngOnInit() {
    this.api.guilds().subscribe((g) => this.guilds.set(g));
    this.load();
  }

  load() {
    this.api.reports(this.guildFilter).subscribe({
      next: (r) => { this.reports.set(r); this.error.set(null); },
      error: () => this.error.set('Could not load reports.'),
    });
  }

  guildName(id: string) { return this.names().get(id) ?? 'a disconnected server'; }

  label(p: string) { return p.charAt(0) + p.slice(1).toLowerCase(); }

  why(r: Report) {
    switch (r.prioritySource) {
      case 'RULE': return `Keyword "${r.matchedKeyword}"`;
      case 'AI': return 'Suggested by AI';
      case 'DEFAULT': return 'Server default';
      default: return '';
    }
  }

  statusLabel(r: Report) {
    if (r.status === 'ACKNOWLEDGED') return `Acknowledged by ${r.actedBy}`;
    if (r.status === 'ESCALATED') return `Escalated by ${r.actedBy}`;
    return 'Open';
  }
}
