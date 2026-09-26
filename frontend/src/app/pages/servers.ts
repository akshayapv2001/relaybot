import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Api, Guild } from '../api';

const CONNECT_MESSAGES: Record<string, { text: string; bad: boolean }> = {
  cancelled: { text: 'Connecting was cancelled in Discord. Nothing changed.', bad: false },
  invalid: { text: 'That connection link expired or was already used. Start again with "Connect a server".', bad: true },
  failed: { text: 'Discord did not confirm the connection. Try again, and make sure you pick a server where you can add bots.', bad: true },
};

@Component({
  selector: 'app-servers',
  imports: [DatePipe, RouterLink],
  template: `
    <section class="page">
      <header class="page-head row">
        <div>
          <h1>Servers</h1>
          <p>Discord servers where RelayBot answers <strong>/report</strong> and <strong>/status</strong>.</p>
        </div>
        <span class="spacer"></span>
        <!-- A full page load: this goes to Discord's "add bot" screen and comes back. -->
        <a class="button" href="/api/discord/connect">Connect a server</a>
      </header>

      @if (notice(); as n) { <p class="notice" [class.error]="n.bad" [class.warn]="!n.bad" role="status">{{ n.text }}</p> }

      <div class="table-wrap">
        <table>
          <thead><tr><th>Server</th><th>Report channel</th><th>Notifications</th><th>Connected</th><th></th></tr></thead>
          <tbody>
            @for (g of guilds(); track g.id) {
              <tr>
                <td><strong>{{ g.name }}</strong></td>
                <td>{{ g.postChannelId ? 'Set' : 'Not chosen yet' }}</td>
                <td>{{ g.mirrorConfigured ? (g.mirrorKind === 'SLACK' ? 'Slack' : 'Discord webhook') : 'Off' }}</td>
                <td class="num small">{{ g.connectedAt | date: 'MMM d, y' }}</td>
                <td><a [routerLink]="['/servers', g.id]">Configure</a></td>
              </tr>
            } @empty {
              <tr><td colspan="5" class="empty">No servers yet. Use <strong>Connect a server</strong> to add RelayBot to one you manage.</td></tr>
            }
          </tbody>
        </table>
      </div>
    </section>
  `,
  styles: `.page-head.row { align-items: flex-end; }`,
})
export class ServersPage implements OnInit {
  private api = inject(Api);
  /** ?connect=… from the OAuth callback redirect. */
  connect = input<string>();
  guilds = signal<Guild[]>([]);
  notice = signal<{ text: string; bad: boolean } | null>(null);

  ngOnInit() {
    const c = this.connect();
    if (c && CONNECT_MESSAGES[c]) this.notice.set(CONNECT_MESSAGES[c]);
    this.api.guilds().subscribe({
      next: (g) => this.guilds.set(g),
      error: () => this.notice.set({ text: 'Could not load servers.', bad: true }),
    });
  }
}
