import type { RiskLevel } from "@/lib/api";

const styles: Record<RiskLevel, string> = {
  LOW: "border-emerald-200 bg-emerald-50 text-emerald-700",
  MEDIUM: "border-amber-200 bg-amber-50 text-amber-700",
  HIGH: "border-rose-200 bg-rose-50 text-rose-700",
};

export function RiskBadge({ level }: { level: RiskLevel | string }) {
  const style = styles[level as RiskLevel] ?? styles.LOW;

  return (
    <span className={`inline-flex items-center rounded border px-2 py-1 text-xs font-semibold ${style}`}>
      {level}
    </span>
  );
}
