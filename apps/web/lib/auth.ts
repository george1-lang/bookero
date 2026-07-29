import { getApiUrl } from "./api";
import React, { ReactNode, createContext, useContext, useEffect, useState } from "react";
import { useRouter } from "next/navigation";

export type UserRole = "TRAVELER" | "ANALYST";

export interface User {
  id: string;
  email: string;
  role: UserRole;
}

export interface AuthContextType {
  user: User | null;
  isLoading: boolean;
  login: (email: string, password: string) => Promise<User>;
  logout: () => void;
  isAnalyst: () => boolean;
  isTraveler: () => boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function useAuth(): AuthContextType {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within AuthProvider");
  }
  return context;
}

export type LoginFailure = "credentials" | "unreachable" | "server";

export class LoginError extends Error {
  constructor(readonly reason: LoginFailure, readonly status?: number) {
    super(reason);
    this.name = "LoginError";
  }
}

interface LoginResponse {
  token: string;
  role: UserRole;
  email: string;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const router = useRouter();

  useEffect(() => {
    const storedToken = sessionStorage.getItem("token");
    const storedUser = sessionStorage.getItem("user");
    if (storedToken && storedUser) {
      try {
        setUser(JSON.parse(storedUser));
      } catch {
        sessionStorage.removeItem("token");
        sessionStorage.removeItem("user");
      }
    }
    setIsLoading(false);
  }, []);

  const login = async (email: string, password: string): Promise<User> => {
    let res: Response;
    try {
      res = await fetch(`${getApiUrl()}/api/auth/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password }),
      });
    } catch {
      // fetch only rejects for transport failures: the API being down, DNS, or a
      // CORS preflight the browser refused. Those are not bad credentials, and
      // saying so sends people hunting for the wrong problem.
      throw new LoginError("unreachable");
    }

    if (res.status === 401) throw new LoginError("credentials");
    if (!res.ok) throw new LoginError("server", res.status);

    const data: LoginResponse = await res.json();
    sessionStorage.setItem("token", data.token);
    const userData: User = { id: "", email: data.email, role: data.role };
    setUser(userData);
    sessionStorage.setItem("user", JSON.stringify(userData));
    return userData;
  };

  const logout = () => {
    sessionStorage.removeItem("token");
    sessionStorage.removeItem("user");
    setUser(null);
    router.push("/login");
  };

  const isAnalyst = () => user?.role === "ANALYST";
  const isTraveler = () => user?.role === "TRAVELER";

  const value: AuthContextType = {
    user,
    isLoading,
    login,
    logout,
    isAnalyst,
    isTraveler,
  };

  return React.createElement(
    AuthContext.Provider,
    { value },
    children
  );
}
