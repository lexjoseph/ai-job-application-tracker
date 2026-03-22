import { apiFetch } from "./client";
import type {
  ApplicationStats,
  ApplicationStatus,
  JobApplication,
} from "@/lib/types";

export async function listApplications(): Promise<JobApplication[]> {
  return apiFetch<JobApplication[]>("/api/v1/applications");
}

export async function getStats(): Promise<ApplicationStats> {
  return apiFetch<ApplicationStats>("/api/v1/applications/stats");
}

export type ApplicationPayload = {
  company: string;
  roleTitle: string;
  status: ApplicationStatus;
  notes?: string | null;
  sourceUrl?: string | null;
  appliedOn: string;
};

export type ScrapeJobResponse = {
  company: string;
  roleTitle: string;
  sourceUrl: string;
  hint: string | null;
};

export async function scrapeJobFromUrl(url: string): Promise<ScrapeJobResponse> {
  return apiFetch<ScrapeJobResponse>("/api/v1/applications/scrape-from-url", {
    method: "POST",
    body: JSON.stringify({ url }),
  });
}

export async function createApplication(payload: ApplicationPayload): Promise<JobApplication> {
  return apiFetch<JobApplication>("/api/v1/applications", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export async function updateApplication(
  id: string,
  payload: ApplicationPayload,
): Promise<JobApplication> {
  return apiFetch<JobApplication>(`/api/v1/applications/${id}`, {
    method: "PUT",
    body: JSON.stringify(payload),
  });
}

export async function deleteApplication(id: string): Promise<void> {
  await apiFetch<void>(`/api/v1/applications/${id}`, {
    method: "DELETE",
  });
}
