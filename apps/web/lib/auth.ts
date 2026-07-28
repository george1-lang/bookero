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
  login: (email: string, password: string) => Promise<void>;
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

  const login = async (email: string, password: string) => {
    const apiUrl = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8090";
    try {
      const res = await fetch(`${apiUrl}/api/auth/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password }),
      });
      if (!res.ok) {
        throw new Error("Login failed");
      }
      const data: LoginResponse = await res.json();
      sessionStorage.setItem("token", data.token);
      const userData = { id: "", email: data.email, role: data.role };
      setUser(userData);
      sessionStorage.setItem("user", JSON.stringify(userData));
    } catch (err) {
      throw err;
    }
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
