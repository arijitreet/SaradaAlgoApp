import { create } from "zustand";

const KEY = "sarada.calm";

/** Reflect calm state on <html data-calm> so CSS can strip ambient effects globally. */
function apply(calm: boolean) {
  document.documentElement.dataset.calm = calm ? "true" : "false";
}

const initial = (() => {
  try {
    return localStorage.getItem(KEY) === "1";
  } catch {
    return false;
  }
})();

apply(initial);

interface UiState {
  /** "Calm mode" — strips aurora, grain, gradient borders, glow and number rolls. */
  calmMode: boolean;
  toggleCalm: () => void;
}

export const useUiStore = create<UiState>((set, get) => ({
  calmMode: initial,
  toggleCalm: () => {
    const next = !get().calmMode;
    try {
      localStorage.setItem(KEY, next ? "1" : "0");
    } catch {
      /* ignore quota / privacy-mode errors */
    }
    apply(next);
    set({ calmMode: next });
  },
}));
