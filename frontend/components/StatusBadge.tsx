const styles: Record<string, string> = {
  DEPLOYED: "border-sky-200 bg-sky-50 text-sky-700",
  ROLLED_BACK: "border-zinc-300 bg-zinc-100 text-zinc-700",
  COMPLETED: "border-emerald-200 bg-emerald-50 text-emerald-700",
  PENDING: "border-amber-200 bg-amber-50 text-amber-700",
  FAILED: "border-rose-200 bg-rose-50 text-rose-700",
};

export function StatusBadge({ status }: { status: string }) {
  return (
    <span className={`inline-flex items-center rounded border px-2 py-1 text-xs font-semibold ${styles[status] ?? styles.PENDING}`}>
      {status.replace("_", " ")}
    </span>
  );
}
