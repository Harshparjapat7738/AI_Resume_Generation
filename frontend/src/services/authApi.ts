/**
 * auth-service — see docs/API_CATALOG.md &sect;3 (Milestone 2).
 * Field names mirror the backend DTOs exactly (ai.careerforge.auth.api.dto.*).
 */
import { apiFetch } from './apiClient';

export interface AuthResponse {
  accessToken: string;
  expiresIn: number;
}

export interface RegisterResponse {
  userId: string;
  email: string;
}

export interface MeResponse {
  userId: string;
  email: string;
  displayName: string;
  roles: string[];
}

export function register(input: {
  email: string;
  password: string;
  displayName: string;
}): Promise<RegisterResponse> {
  return apiFetch<RegisterResponse>('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export function login(input: { email: string; password: string }): Promise<AuthResponse> {
  return apiFetch<AuthResponse>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

/** Relies on the HttpOnly refresh cookie the browser sends automatically. */
export function refresh(): Promise<AuthResponse> {
  return apiFetch<AuthResponse>('/api/auth/refresh', { method: 'POST' });
}

export function logout(): Promise<void> {
  return apiFetch<void>('/api/auth/logout', { method: 'POST' });
}

export function me(): Promise<MeResponse> {
  return apiFetch<MeResponse>('/api/auth/me');
}
