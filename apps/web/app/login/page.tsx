"use client";

import { SplitFlap } from "@/components/SplitFlap";
import { useAuth } from "@/lib/auth";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import styles from "./page.module.css";

const HOME_FOR = { ANALYST: "/ops", TRAVELER: "/" } as const;

const DEMO_ACCOUNTS = [
  { role: "ANALYST", email: "analyst@bookero.local", blurb: "revenue desk · lab · dashboard" },
  { role: "TRAVELER", email: "traveler@bookero.local", blurb: "search · fares · booking" },
] as const;

export default function LoginPage() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const { login, user } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (user) router.replace(HOME_FOR[user.role]);
  }, [user, router]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    setIsLoading(true);
    try {
      // Route on the role the API returns, never on the shape of the address.
      const authenticated = await login(email, password);
      router.replace(HOME_FOR[authenticated.role]);
    } catch {
      setError("Invalid email or password.");
      setIsLoading(false);
    }
  };

  return (
    <main className={styles.container}>
      <div className={styles.stub} aria-hidden="true">
        <div className={styles.wordmark}>
          <SplitFlap value="BOOKERO" size="lg" />
        </div>
        <p className={styles.tagline}>
          Revenue operations for a single carrier - simulate demand, reprice the ladder,
          watch the cabin fill.
        </p>
        <dl className={styles.spec}>
          <div>
            <dt>Network</dt>
            <dd>ACC hub · OpenFlights backbone</dd>
          </div>
          <div>
            <dt>Fare ladder</dt>
            <dd>Y · B · M · J</dd>
          </div>
          <div>
            <dt>Algorithms</dt>
            <dd>10 families, one shared engine</dd>
          </div>
        </dl>
      </div>

      <form className={styles.panel} onSubmit={handleSubmit}>
        <h1 className={styles.title}>Flight ops access</h1>

        <div className={styles.field}>
          <label htmlFor="email">Email</label>
          <input
            id="email"
            type="email"
            autoComplete="username"
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
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="••••••••"
            required
          />
        </div>

        {error && (
          <p className={styles.error} role="alert">
            {error}
          </p>
        )}

        <button type="submit" disabled={isLoading} className={styles.submit}>
          {isLoading ? "connecting…" : "Access system"}
        </button>

        <div className={styles.divider}>
          <span>demo credentials</span>
        </div>

        <div className={styles.chips}>
          {DEMO_ACCOUNTS.map((account) => (
            <button
              key={account.email}
              type="button"
              className={styles.chip}
              onClick={() => {
                setEmail(account.email);
                setPassword("password");
              }}
            >
              <span className={styles.chipRole}>{account.role}</span>
              <span className={styles.chipEmail}>{account.email}</span>
              <span className={styles.chipBlurb}>{account.blurb}</span>
            </button>
          ))}
        </div>

        <p className={styles.footer}>
          Both demo accounts use the password <code>password</code>.
        </p>
      </form>
    </main>
  );
}
