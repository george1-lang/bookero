import styles from "./LoadingState.module.css";

/** Departure-board style waiting row, used wherever a page is fetching. */
export function LoadingState({ label }: { label: string }) {
  return (
    <div className={styles.wrap} role="status" aria-live="polite">
      <span className={styles.flaps} aria-hidden="true">
        <i />
        <i />
        <i />
      </span>
      <span className={styles.label}>{label}</span>
    </div>
  );
}
