<div className="flex flex-col gap-2 sm:col-span-2">
            <label className="flex flex-col gap-1 text-sm">
              <span className="font-medium text-zinc-700 dark:text-zinc-300">Job listing URL</span>
              <div className="flex flex-col gap-2 sm:flex-row sm:items-stretch">
                <input
                  type="url"
                  placeholder="https://…"
                  value={form.sourceUrl}
                  onChange={(e) => setForm((f) => ({ ...f, sourceUrl: e.target.value }))}
                  className="min-w-0 flex-1 rounded-lg border border-zinc-200 bg-white px-3 py-2 text-zinc-900 outline-none ring-zinc-400 focus:ring-2 dark:border-zinc-700 dark:bg-zinc-950 dark:text-zinc-50"
                />
                <button
                  type="button"
                  onClick={() => void onScrapeFromUrl()}
                  disabled={scrapePending}
                  className="shrink-0 rounded-lg border border-zinc-300 bg-zinc-100 px-4 py-2 text-sm font-medium text-zinc-900 disabled:opacity-50 dark:border-zinc-600 dark:bg-zinc-800 dark:text-zinc-100"
                >
                  {scrapePending ? "Importing…" : "Import from URL"}
                </button>
              </div>
            </label>
            {scrapeHint && (
              <p className="text-xs text-amber-700 dark:text-amber-300">{scrapeHint}</p>
            )}
          </div>