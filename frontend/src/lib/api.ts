const API_BASE = import.meta.env.VITE_API_BASE ?? "/api";

let authToken: string | null = localStorage.getItem("sarada.token");

export function setAuthToken(token: string | null) {
  authToken = token;
  if (token) localStorage.setItem("sarada.token", token);
  else localStorage.removeItem("sarada.token");
}

export function getAuthToken() {
  return authToken;
}

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(authToken ? { Authorization: `Bearer ${authToken}` } : {}),
      ...init?.headers,
    },
  });
  if (res.status === 401) {
    setAuthToken(null);
    window.dispatchEvent(new Event("sarada:unauthorized"));
    throw new ApiError(401, "Session expired");
  }
  if (!res.ok) {
    const body = await res.json().catch(() => null);
    throw new ApiError(res.status, body?.message ?? `Request failed (${res.status})`);
  }
  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: "POST", body: body ? JSON.stringify(body) : undefined }),
};
