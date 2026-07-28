"use client";

import { useAuth } from "@/lib/auth";
import { useRouter } from "next/navigation";
import { useState } from "react";
import styles from "./page.module.css";

export default function LoginPage() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const { login, user } = useAuth();
  const router = useRouter();

  if (user) {
    router.push(user.role === "ANALYST" ? "/ops" : "/");
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    setIsLoading(true);
    try {
      await login(email, password);
      router.push(email.includes("analyst") ? "/ops" : "/");
    } catch {
      setError("Login failed. Check email and password.");
    } finally {
      setIsLoading(false);
    }
  };

  const setCredentials = (email: string, password: string) => {
    setEmail(email);
    setPassword(password);
  };

  return (
    <div className={styles.container}>
      <div className={styles.shell}>
        <div className={styles.wordmark}>
          <span>B</span>
          <span>O</span>
          <span>O</span>
          <span>K</span>
          <span>E</span>
          <span>R</span>
          <span>O</span>
        </div>

        <form className={styles.form} onSubmit={handleSubmit}>
          <div className={styles.panel}>
            <h1 className={styles.title}>FLIGHT OPS ACCESS</h1>

            <div className={styles.field}>
              <label htmlFor="email">Email</label>
              <input
                id="email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="analyst@bookero.local"
                required
              />
            </div>

            <div className={styles.field}>
              <label htmlFor="password">Password</label>
              <input
                id="password"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••"
                required
              />
            </div>

            {error && <div className={styles.error}>{error}</div>}

            <button
              type="submit"
              disabled={isLoading}
              className={styles.submit}
            >
              {isLoading ? "CONNECTING..." : "ACCESS SYSTEM"}
            </button>

            <div className={styles.divider}>Demo Credentials</div>

            <div className={styles.chips}>
              <button
                type="button"
                className={styles.chip}
                onClick={() =>
                  setCredentials("analyst@bookero.local", "password")
                }
              >
                <span className={styles.chipLabel}>ANALYST</span>
                <span className={styles.chipEmail}>analyst@bookero.local</span>
              </button>
              <button
                type="button"
                className={styles.chip}
                onClick={() =>
                  setCredentials("traveler@bookero.local", "password")
                }
              >
                <span className={styles.chipLabel}>TRAVELER</span>
                <span className={styles.chipEmail}>traveler@bookero.local</span>
              </button>
            </div>

            <div className={styles.footer}>
              <small>Password for demo accounts: <code>password</code></small>
            </div>
          </div>
        </form>
      </div>
    </div>
  );
}
