from sqlalchemy import create_engine, pool, text
from sqlalchemy.orm import sessionmaker
from sqlalchemy.exc import OperationalError

from app.config import settings


_ACCEPTED_PREFIXES = (
    "postgresql+psycopg://",
    "postgresql://",
    "postgres://",
    "jdbc:postgresql://",
)


def _normalize_database_url(url: str) -> str:
    """Coerce the several shapes a Postgres URL arrives in into one psycopg URL.

    Hosting dashboards hand out `postgres://`, Neon hands out `postgresql://`, and it
    is easy to paste the Spring `jdbc:postgresql://` form here by mistake. All three
    describe the same database, so accept them rather than failing with SQLAlchemy's
    opaque "Could not parse SQLAlchemy URL" error.
    """
    cleaned = (url or "").strip().strip('"').strip("'")

    if not cleaned:
        raise ValueError(
            "DATABASE_URL is empty. Set it to a Postgres URL such as "
            "postgresql://user:password@host/dbname?sslmode=require"
        )

    if cleaned.startswith("jdbc:"):
        # The Spring form carries credentials in separate variables, so accepting it
        # here would silently produce an anonymous connection that fails much later.
        authority = cleaned[len("jdbc:"):].split("://", 1)[-1].split("/", 1)[0]
        if "@" not in authority:
            raise ValueError(
                "DATABASE_URL looks like the Spring JDBC form, which carries no user or "
                "password. The Python service needs the full URI, for example "
                "postgresql://user:password@host/dbname?sslmode=require"
            )
        cleaned = cleaned[len("jdbc:"):]

    if cleaned.startswith("postgresql+psycopg://"):
        return cleaned

    for prefix in ("postgresql://", "postgres://"):
        if cleaned.startswith(prefix):
            return "postgresql+psycopg://" + cleaned[len(prefix):]

    raise ValueError(
        f"DATABASE_URL must start with one of {', '.join(_ACCEPTED_PREFIXES)}, "
        f"got {cleaned.split('://', 1)[0] + '://' if '://' in cleaned else cleaned!r}. "
        "Note the JDBC form carries no credentials; the Python service needs the full "
        "postgresql:// URI including user and password."
    )


normalized_url = _normalize_database_url(settings.database_url)

engine = create_engine(
    normalized_url,
    poolclass=pool.QueuePool,
    pool_size=10,
    max_overflow=20,
    pool_pre_ping=True,
    echo=False,
)

SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


def healthcheck() -> bool:
    try:
        with engine.connect() as conn:
            conn.execute(text("SELECT 1"))
        return True
    except OperationalError:
        return False
