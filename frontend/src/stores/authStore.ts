import { create } from "zustand";
import { api, setAuthToken, getAuthToken } from "@/lib/api";

interface AuthState {
  token: string | null;
  username: string | null;
  loggingIn: boolean;
  error: string | null;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  token: getAuthToken(),
  username: localStorage.getItem("sarada.username"),
  loggingIn: false,
  error: null,

  login: async (username, password) => {
    set({ loggingIn: true, error: null });
    try {
      const res = await api.post<{ token: string; username: string }>("/auth/login", {
        username,
        password,
      });
      setAuthToken(res.token);
      localStorage.setItem("sarada.username", res.username);
      set({ token: res.token, username: res.username, loggingIn: false });
    } catch (e) {
      set({ loggingIn: false, error: e instanceof Error ? e.message : "Login failed" });
      throw e;
    }
  },

  logout: () => {
    setAuthToken(null);
    localStorage.removeItem("sarada.username");
    set({ token: null, username: null });
  },
}));

window.addEventListener("sarada:unauthorized", () => {
  useAuthStore.setState({ token: null, username: null });
});
