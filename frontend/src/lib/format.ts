const inr = new Intl.NumberFormat("en-IN", {
  style: "currency",
  currency: "INR",
  maximumFractionDigits: 0,
});

const inrPrecise = new Intl.NumberFormat("en-IN", {
  style: "currency",
  currency: "INR",
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

const num = new Intl.NumberFormat("en-IN", {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

export const fmtInr = (v: number | null | undefined) =>
  v == null ? "—" : inr.format(v);

export const fmtInrPrecise = (v: number | null | undefined) =>
  v == null ? "—" : inrPrecise.format(v);

export const fmtNum = (v: number | null | undefined) =>
  v == null ? "—" : num.format(v);

export const fmtSigned = (v: number | null | undefined) =>
  v == null ? "—" : `${v >= 0 ? "+" : ""}${num.format(v)}`;

export const fmtTime = (iso: string | null | undefined) =>
  iso
    ? new Date(iso).toLocaleTimeString("en-IN", {
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
        hour12: false,
        timeZone: "Asia/Kolkata",
      })
    : "—";

export const fmtDate = (iso: string | null | undefined) =>
  iso
    ? new Date(iso).toLocaleDateString("en-IN", {
        day: "2-digit",
        month: "short",
        timeZone: "Asia/Kolkata",
      })
    : "—";

export const pnlColor = (v: number | null | undefined) =>
  v == null || v === 0 ? "text-slate-300" : v > 0 ? "text-profit" : "text-loss";
