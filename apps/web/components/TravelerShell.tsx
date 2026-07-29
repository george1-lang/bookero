"use client";

import { useAuth } from "@/lib/auth";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { ReactNode } from "react";
import styles from "./TravelerShell.module.css";

const NAV = [
  { href: "/", label: "Search" },
  { href: "/bookings", label: "My bookings" },
];

/** Chrome shared by every traveller-facing page: wordmark, navigation, sign out. */
export function TravelerShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const { user, logout } = useAuth();

  return (
    <div className={styles.shell}>
      <header className={styles.bar}>
        <Link href="/" className={styles.brand}>
          BOOKERO
        </Link>

        <nav className={styles.nav} aria-label="Traveller">
          {NAV.map((item) => (
            <Link
              key={item.href}
              href={item.href}
              className={pathname === item.href ? styles.linkActive : styles.link}
              aria-current={pathname === item.href ? "page" : undefined}
            >
              {item.label}
            </Link>
          ))}
        </nav>

        <div className={styles.account}>
          {user && <span className={styles.email}>{user.email}</span>}
          <button type="button" className={styles.signOut} onClick={logout}>
            Sign out
          </button>
        </div>
      </header>

      <div className={styles.body}>{children}</div>
    </div>
  );
}
