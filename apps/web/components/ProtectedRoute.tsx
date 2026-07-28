"use client";

import { useAuth } from "@/lib/auth";
import { useRouter } from "next/navigation";
import { ReactNode, useEffect, useState } from "react";
import styles from "./ProtectedRoute.module.css";

interface ProtectedRouteProps {
  children: ReactNode;
  requiredRole?: "ANALYST" | "TRAVELER";
}

export function ProtectedRoute({
  children,
  requiredRole,
}: ProtectedRouteProps) {
  const { user, isLoading } = useAuth();
  const router = useRouter();
  const [isValid, setIsValid] = useState(false);

  useEffect(() => {
    if (isLoading) return;

    if (!user) {
      router.push("/login");
      return;
    }

    if (requiredRole && user.role !== requiredRole) {
      router.push(user.role === "ANALYST" ? "/ops" : "/");
      return;
    }

    setIsValid(true);
  }, [user, isLoading, requiredRole, router]);

  if (isLoading) {
    return (
      <div className={styles.gate}>
        Loading...
      </div>
    );
  }

  if (!isValid) {
    return null;
  }

  return <>{children}</>;
}
