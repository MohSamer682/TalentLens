import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { User } from '@/types';

/** Which sign-in page this browser last used, so a signed-out visitor goes back to the same one. */
export type Door = 'staff' | 'candidate';

export const doorOf = (user: User): Door => (user.role === 'CANDIDATE' ? 'candidate' : 'staff');

interface AuthState {
  token: string | null;
  user: User | null;
  door: Door;
  login: (token: string, user: User) => void;
  setUser: (user: User) => void;
  logout: () => void;
  isAuthenticated: () => boolean;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      token: null,
      user: null,
      door: 'staff',
      login: (token, user) => set({ token, user, door: doorOf(user) }),
      setUser: (user) => set({ user }),
      logout: () => set({ token: null, user: null }),
      isAuthenticated: () => !!get().token,
    }),
    {
      name: 'auth-storage',
      // Sessions saved before doors existed have no door; work it out from the stored user
      merge: (persisted, current) => {
        const saved = (persisted ?? {}) as Partial<AuthState>;
        return { ...current, ...saved, door: saved.door ?? (saved.user ? doorOf(saved.user) : current.door) };
      },
    }
  )
);