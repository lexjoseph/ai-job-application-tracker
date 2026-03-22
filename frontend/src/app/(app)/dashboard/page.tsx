"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { getStats } from "@/lib/api/applications";
import { ApiError } from "@/lib/api/client";
import type { ApplicationStats } from "@/lib/types";
import { Spinner } from "@/components/Spinner";

const cards: {
  key: keyof Omit<ApplicationStats, "total">;
  label: string;
  hint: string;
}[] = [
  { key: "applied", label: "Applied", hint: "Waiting to hear back" },
  { key: "interview", label: "Interview", hint: "In conversation" },
  { key: "offer", label: "Offer", hint: "Congratulations" },
  { key: "rejected", label: "Rejected", hint: "On to the next" },
];

export default function DashboardPage() {
  const [stats, setStats] = useState<ApplicationStats | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      const data = await getStats();
      setStats(data);
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) {
        return;
      }
      setError(e instanceof ApiError ? e.message : "Could not load stats");
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  if (!stats && !error) {
    return <Spinner label="Loading dashboard" />;
  }

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-50">
          Dashboard
        </h1>
        <p className="mt-1 text-sm text-zinc-500">
          Overview of your pipeline. Manage details on the applications page.
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

      {stats && (
        <>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {cards.map(({ key, label, hint }) => (
              <div
                key={key}
                className="rounded-2xl border border-zinc-200 bg-white p-5 shadow-sm dark:border-zinc-800 dark:bg-zinc-900"
              >
                <p className="text-sm font-medium text-zinc-500">{label}</p>
                <p className="mt-2 text-3xl font-semibold tabular-nums text-zinc-900 dark:text-zinc-50">
                  {stats[key]}
                </p>
                <p className="mt-1 text-xs text-zinc-400">{hint}</p>
              </div>
            ))}
          </div>
          <div className="rounded-2xl border border-zinc-200 bg-white p-6 shadow-sm dark:border-zinc-800 dark:bg-zinc-900">
            <p className="text-sm font-medium text-zinc-500">Total applications</p>
            <p className="mt-1 text-4xl font-semibold tabular-nums text-zinc-900 dark:text-zinc-50">
              {stats.total}
            </p>
            <Link
              href="/applications"
              className="mt-4 inline-flex text-sm font-medium text-zinc-900 underline-offset-4 hover:underline dark:text-zinc-100"
            >
              View all applications →
            </Link>
          </div>
        </>
      )}
    </div>
  );
}
