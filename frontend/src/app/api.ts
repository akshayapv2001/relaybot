import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

export type Priority = 'LOW' | 'MEDIUM' | 'HIGH';
export const PRIORITIES: Priority[] = ['LOW', 'MEDIUM', 'HIGH'];

export interface Activity {
  id: number;
  guildId: string | null;
  interactionId: string | null;
  reportId: number | null;
  jobId: number | null;
  level: 'INFO' | 'WARN' | 'ERROR';
  event: string;
  message: string;
  createdAt: string;
}

export interface Report {
  id: number;
  guildId: string;
  username: string;
  text: string;
  priority: Priority | null;
  prioritySource: 'RULE' | 'AI' | 'DEFAULT' | null;
  matchedKeyword: string | null;
  aiStatus: string;
  aiSummary: string | null;
  aiCategory: string | null;
  status: 'OPEN' | 'ESCALATED' | 'ACKNOWLEDGED';
  actedBy: string | null;
  createdAt: string;
}

export interface Job {
  id: number;
  type: string;
  guildId: string | null;
  reportId: number | null;
  status: 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED';
  attempts: number;
  maxAttempts: number;
  nextRunAt: string;
  lastError: string | null;
  updatedAt: string;
}

export interface Rejection { at: string; reason: string; remoteAddress: string; }

export interface Overview {
  jobsPending: number;
  jobsFailed: number;
  errorsLast24h: number;
  rejectedRequests: number;
  recentRejections: Rejection[];
}

export interface Rule { keyword: string; priority: Priority; }

export interface Guild {
  id: string;
  name: string;
  postChannelId: string | null;
  reportEnabled: boolean;
  statusEnabled: boolean;
  aiEnabled: boolean;
  ephemeralReplies: boolean;
  defaultPriority: Priority;
  mirrorMinPriority: Priority;
  mirrorConfigured: boolean;
  mirrorKind: 'SLACK' | 'DISCORD' | null;
  aiAvailable: boolean;
  connectedAt: string;
  rules: Rule[];
}

export interface GuildSettings {
  postChannelId: string | null;
  reportEnabled: boolean;
  statusEnabled: boolean;
  aiEnabled: boolean;
  ephemeralReplies: boolean;
  defaultPriority: Priority;
  mirrorMinPriority: Priority;
}

export interface Channel { id: string; name: string; }

export const JOB_LABELS: Partial<Record<string, string>> = {
  TRIAGE_REPORT: 'Triage',
  REPLY_TO_USER: 'Reply in Discord',
  POST_TO_CHANNEL: 'Post to channel',
  MIRROR: 'Mirror notification',
  REFRESH_REPORT_MESSAGE: 'Update report card',
};

/** Pulls the server's { error } message out of an HttpErrorResponse. */
export function errorMessage(err: unknown, fallback = 'Something went wrong.'): string {
  const e = err as { error?: { error?: string }; status?: number };
  if (e?.error?.error) return e.error.error;
  if (e?.status === 0) return 'Could not reach the server. Check your connection.';
  return fallback;
}

@Injectable({ providedIn: 'root' })
export class Api {
  private http = inject(HttpClient);

  overview() { return this.http.get<Overview>('/api/overview'); }

  activity(guildId?: string, level?: string) {
    return this.http.get<Activity[]>('/api/activity', { params: params({ guildId, level, limit: 150 }) });
  }

  reports(guildId?: string) {
    return this.http.get<Report[]>('/api/reports', { params: params({ guildId, limit: 200 }) });
  }

  jobs(status?: string) {
    return this.http.get<Job[]>('/api/jobs', { params: params({ status, limit: 200 }) });
  }

  retryJob(id: number) { return this.http.post<{ result: string }>(`/api/jobs/${id}/retry`, {}); }

  guilds() { return this.http.get<Guild[]>('/api/guilds'); }
  guild(id: string) { return this.http.get<Guild>(`/api/guilds/${id}`); }
  channels(id: string) { return this.http.get<Channel[]>(`/api/guilds/${id}/channels`); }
  saveSettings(id: string, s: GuildSettings) { return this.http.put<Guild>(`/api/guilds/${id}/settings`, s); }
  saveRules(id: string, rules: Rule[]) { return this.http.put<Guild>(`/api/guilds/${id}/rules`, rules); }
  setMirror(id: string, url: string) { return this.http.put<Guild>(`/api/guilds/${id}/mirror`, { url }); }
  removeMirror(id: string) { return this.http.delete<Guild>(`/api/guilds/${id}/mirror`); }
  testMirror(id: string) { return this.http.post<{ result: string }>(`/api/guilds/${id}/mirror/test`, {}); }
  disconnect(id: string) { return this.http.delete<void>(`/api/guilds/${id}`); }
}

function params(values: Record<string, string | number | undefined | null>): HttpParams {
  let p = new HttpParams();
  for (const [k, v] of Object.entries(values)) {
    if (v !== undefined && v !== null && v !== '') p = p.set(k, String(v));
  }
  return p;
}
