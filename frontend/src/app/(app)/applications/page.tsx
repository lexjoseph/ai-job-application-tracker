"use client";

import { useCallback, useEffect, useState } from "react";
import {
  createApplication,
  deleteApplication,
  listApplications,
  scrapeJobFromUrl,
  updateApplication,
} from "@/lib/api/applications";
import { ApiError } from "@/lib/api/client";
import type { ApplicationStatus, JobApplication } from "@/lib/types";
import { Spinner } from "@/components/Spinner";

const STATUSES: ApplicationStatus[] = [
  "APPLIED",
  "INTERVIEW",
  "OFFER",
  "REJECTED",
];

function statusBadgeClass(s: ApplicationStatus) {
  switch (s) {
    case "OFFER":
      return "bg-emerald-100 text-emerald-900 dark:bg-emerald-950 dark:text-emerald-200";
    case "INTERVIEW":
      return "bg-sky-100 text-sky-900 dark:bg-sky-950 dark:text-sky-200";
    case "REJECTED":
      return "bg-zinc-200 text-zinc-800 dark:bg-zinc-800 dark:text-zinc-200";
    default:
      return "bg-amber-100 text-amber-900 dark:bg-amber-950 dark:text-amber-200";
  }
}

function formatStatus(s: ApplicationStatus) {
  return s.charAt(0) + s.slice(1).toLowerCase();
}

const emptyForm = {
  company: "",
  roleTitle: "",
  status: "APPLIED" as ApplicationStatus,
  notes: "",
  sourceUrl: "",
  appliedOn: new Date().toISOString().slice(0, 10),
};

export default function ApplicationsPage() {
  const [items, setItems] = useState<JobApplication[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [form, setForm] = useState(emptyForm);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const [scrapePending, setScrapePending] = useState(false);
  const [scrapeHint, setScrapeHint] = useState<string | null>(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      const data = await listApplications();
      setItems(data);
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) {
        return;
      }
      setError(
        e instanceof ApiError ? e.message : "Could not load applications",
      );
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  function startEdit(app: JobApplication) {
    setEditingId(app.id);
    setForm({
      company: app.company,
      roleTitle: app.roleTitle,
      status: app.status,
      notes: app.notes ?? "",
      sourceUrl: app.sourceUrl ?? "",
      appliedOn: app.appliedOn,
    });
    setScrapeHint(null);
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  function cancelEdit() {
    setEditingId(null);
    setForm(emptyForm);
    setScrapeHint(null);
  }

  async function onScrapeFromUrl() {
    const url = form.sourceUrl.trim();
    if (!url) {
      setError("Paste a job listing URL first.");
      return;
    }
    setScrapePending(true);
    setScrapeHint(null);
    setError(null);
    try {
      const r = await scrapeJobFromUrl(url);
      setForm((f) => ({
        ...f,
        company: r.company,
        roleTitle: r.roleTitle,
        sourceUrl: r.sourceUrl,
      }));
      if (r.hint) {
        setScrapeHint(r.hint);
      }
    } catch (err) {
      setError(
        err instanceof ApiError ? err.message : "Could not import from URL",
      );
    } finally {
      setScrapePending(false);
    }
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setPending(true);
    setError(null);
    try {
      const payload = {
        company: form.company,
        roleTitle: form.roleTitle,
        status: form.status,
        notes: form.notes || null,
        sourceUrl: form.sourceUrl.trim() ? form.sourceUrl.trim() : null,
        appliedOn: form.appliedOn,
      };
      if (editingId) {
        await updateApplication(editingId, payload);
      } else {
        await createApplication(payload);
      }
      cancelEdit();
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Save failed");
    } finally {
      setPending(false);
    }
  }

  async function onDelete(id: string) {
    if (!confirm("Delete this application?")) return;
    setError(null);
    try {
      await deleteApplication(id);
      if (editingId === id) cancelEdit();
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Delete failed");
    }
  }

  if (!items) {
    return <Spinner label="Loading applications" />;
  }

  return (
    <div className="space-y-10">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-50">
          Applications
        </h1>
        <p className="mt-1 text-sm text-zinc-500">
          Add roles you have applied for, track status, and keep interview
          notes.
        </p>
      </div>

      {error && (
        <p
          className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800 dark:border-red-900 dark:bg-red-950/40 dark:text-red-200"
          role="alert"
        >
          {error}
        </p>
      )}

      <section className="rounded-2xl border border-zinc-200 bg-white p-6 shadow-sm dark:border-zinc-800 dark:bg-zinc-900">
        <h2 className="text-lg font-medium text-zinc-900 dark:text-zinc-50">
          {editingId ? "Edit application" : "New application"}
        </h2>
        <form onSubmit={onSubmit} className="mt-4 grid gap-4 sm:grid-cols-2">
          <label className="flex flex-col gap-1 text-sm sm:col-span-1">
            <span className="font-medium text-zinc-700 dark:text-zinc-300">
              Company
            </span>
            <input
              required
              value={form.company}
              onChange={(e) =>
                setForm((f) => ({ ...f, company: e.target.value }))
              }
              className="rounded-lg border border-zinc-200 bg-white px-3 py-2 text-zinc-900 outline-none ring-zinc-400 focus:ring-2 dark:border-zinc-700 dark:bg-zinc-950 dark:text-zinc-50"
            />
          </label>
          <label className="flex flex-col gap-1 text-sm sm:col-span-1">
            <span className="font-medium text-zinc-700 dark:text-zinc-300">
              Role
            </span>
            <input
              required
              value={form.roleTitle}
              onChange={(e) =>
                setForm((f) => ({ ...f, roleTitle: e.target.value }))
              }
              className="rounded-lg border border-zinc-200 bg-white px-3 py-2 text-zinc-900 outline-none ring-zinc-400 focus:ring-2 dark:border-zinc-700 dark:bg-zinc-950 dark:text-zinc-50"
            />
          </label>
          <label className="flex flex-col gap-1 text-sm sm:col-span-1">
            <span className="font-medium text-zinc-700 dark:text-zinc-300">
              Status
            </span>
            <select
              value={form.status}
              onChange={(e) =>
                setForm((f) => ({
                  ...f,
                  status: e.target.value as ApplicationStatus,
                }))
              }
              className="rounded-lg border border-zinc-200 bg-white px-3 py-2 text-zinc-900 outline-none ring-zinc-400 focus:ring-2 dark:border-zinc-700 dark:bg-zinc-950 dark:text-zinc-50"
            >
              {STATUSES.map((s) => (
                <option key={s} value={s}>
                  {formatStatus(s)}
                </option>
              ))}
            </select>
          </label>
          <label className="flex flex-col gap-1 text-sm sm:col-span-1">
            <span className="font-medium text-zinc-700 dark:text-zinc-300">
              Applied on
            </span>
            <input
              type="date"
              required
              value={form.appliedOn}
              onChange={(e) =>
                setForm((f) => ({ ...f, appliedOn: e.target.value }))
              }
              className="rounded-lg border border-zinc-200 bg-white px-3 py-2 text-zinc-900 outline-none ring-zinc-400 focus:ring-2 dark:border-zinc-700 dark:bg-zinc-950 dark:text-zinc-50"
            />
          </label>
          <label className="flex flex-col gap-1 text-sm sm:col-span-1">
            <span className="font-medium text-zinc-700 dark:text-zinc-300">
              Listing URL
            </span>
            <input
              type="url"
              value={form.sourceUrl}
              onChange={(e) =>
                setForm((f) => ({ ...f, sourceUrl: e.target.value }))
              }
              placeholder="https://example.com/job-posting"
              className="rounded-lg border border-zinc-200 bg-white px-3 py-2 text-zinc-900 outline-none ring-zinc-400 focus:ring-2 dark:border-zinc-700 dark:bg-zinc-950 dark:text-zinc-50"
            />
          </label>
          <label className="flex flex-col gap-1 text-sm sm:col-span-2">
            <span className="font-medium text-zinc-700 dark:text-zinc-300">
              Notes
            </span>
            <textarea
              rows={4}
              value={form.notes}
              onChange={(e) =>
                setForm((f) => ({ ...f, notes: e.target.value }))
              }
              placeholder="Recruiter name, interview prep, follow-up dates…"
              className="resize-y rounded-lg border border-zinc-200 bg-white px-3 py-2 text-zinc-900 outline-none ring-zinc-400 focus:ring-2 dark:border-zinc-700 dark:bg-zinc-950 dark:text-zinc-50"
            />
          </label>
          <div className="flex flex-wrap gap-2 sm:col-span-2">
            <button
              type="submit"
              disabled={pending}
              className="rounded-lg bg-zinc-900 px-4 py-2 text-sm font-medium text-white disabled:opacity-50 dark:bg-zinc-100 dark:text-zinc-900"
            >
              {pending ? "Saving…" : editingId ? "Update" : "Add application"}
            </button>
            {editingId && (
              <button
                type="button"
                onClick={cancelEdit}
                className="rounded-lg border border-zinc-200 px-4 py-2 text-sm font-medium text-zinc-700 dark:border-zinc-700 dark:text-zinc-300"
              >
                Cancel
              </button>
            )}
          </div>
        </form>
      </section>

      <section>
        <h2 className="mb-3 text-lg font-medium text-zinc-900 dark:text-zinc-50">
          Your pipeline
        </h2>
        <div className="overflow-x-auto rounded-2xl border border-zinc-200 bg-white shadow-sm dark:border-zinc-800 dark:bg-zinc-900">
          <table className="min-w-full text-left text-sm">
            <thead className="border-b border-zinc-200 bg-zinc-50 dark:border-zinc-800 dark:bg-zinc-950/50">
              <tr>
                <th className="px-4 py-3 font-medium text-zinc-600 dark:text-zinc-400">
                  Company
                </th>
                <th className="px-4 py-3 font-medium text-zinc-600 dark:text-zinc-400">
                  Role
                </th>
                <th className="px-4 py-3 font-medium text-zinc-600 dark:text-zinc-400">
                  Status
                </th>
                <th className="px-4 py-3 font-medium text-zinc-600 dark:text-zinc-400">
                  Applied
                </th>
                <th className="px-4 py-3 font-medium text-zinc-600 dark:text-zinc-400">
                  Listing
                </th>
                <th className="px-4 py-3 font-medium text-zinc-600 dark:text-zinc-400">
                  Notes
                </th>
                <th className="px-4 py-3 font-medium text-zinc-600 dark:text-zinc-400">
                  Actions
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-zinc-200 dark:divide-zinc-800">
              {items.length === 0 ? (
                <tr>
                  <td
                    colSpan={7}
                    className="px-4 py-10 text-center text-zinc-500"
                  >
                    No applications yet. Add your first role above.
                  </td>
                </tr>
              ) : (
                items.map((app) => (
                  <tr
                    key={app.id}
                    className="hover:bg-zinc-50/80 dark:hover:bg-zinc-950/50"
                  >
                    <td className="px-4 py-3 font-medium text-zinc-900 dark:text-zinc-100">
                      {app.company}
                    </td>
                    <td className="px-4 py-3 text-zinc-700 dark:text-zinc-300">
                      {app.roleTitle}
                    </td>
                    <td className="px-4 py-3">
                      <span
                        className={`inline-flex rounded-full px-2.5 py-0.5 text-xs font-medium ${statusBadgeClass(app.status)}`}
                      >
                        {formatStatus(app.status)}
                      </span>
                    </td>
                    <td className="px-4 py-3 tabular-nums text-zinc-600 dark:text-zinc-400">
                      {app.appliedOn}
                    </td>
                    <td className="px-4 py-3">
                      {app.sourceUrl ? (
                        <a
                          href={app.sourceUrl}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="text-sm font-medium text-sky-700 underline-offset-2 hover:underline dark:text-sky-400"
                        >
                          Open
                        </a>
                      ) : (
                        <span className="text-zinc-400">—</span>
                      )}
                    </td>
                    <td className="max-w-xs truncate px-4 py-3 text-zinc-600 dark:text-zinc-400">
                      {app.notes || "—"}
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex flex-wrap gap-2">
                        <button
                          type="button"
                          onClick={() => startEdit(app)}
                          className="text-sm font-medium text-zinc-900 underline-offset-2 hover:underline dark:text-zinc-100"
                        >
                          Edit
                        </button>
                        <button
                          type="button"
                          onClick={() => void onDelete(app.id)}
                          className="text-sm font-medium text-red-600 hover:underline dark:text-red-400"
                        >
                          Delete
                        </button>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
