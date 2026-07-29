import pytest

from app.db import _normalize_database_url


@pytest.mark.parametrize(
    "raw,expected",
    [
        ("postgresql://u:p@h/db", "postgresql+psycopg://u:p@h/db"),
        ("postgres://u:p@h/db", "postgresql+psycopg://u:p@h/db"),
        ("postgresql+psycopg://u:p@h/db", "postgresql+psycopg://u:p@h/db"),
        ('  "postgresql://u:p@h/db"  ', "postgresql+psycopg://u:p@h/db"),
        (
            "postgresql://u:p@h/db?sslmode=require&channel_binding=require",
            "postgresql+psycopg://u:p@h/db?sslmode=require&channel_binding=require",
        ),
        ("jdbc:postgresql://u:p@h/db", "postgresql+psycopg://u:p@h/db"),
    ],
)
def test_accepts_every_shape_a_postgres_url_arrives_in(raw, expected):
    assert _normalize_database_url(raw) == expected


def test_rejects_empty_url():
    with pytest.raises(ValueError, match="empty"):
        _normalize_database_url("")


def test_rejects_a_non_postgres_scheme():
    with pytest.raises(ValueError, match="must start with"):
        _normalize_database_url("mysql://u:p@h/db")


def test_rejects_the_credential_free_jdbc_form():
    """Pasting the Spring value here is an easy mistake; it must fail loudly."""
    with pytest.raises(ValueError, match="no user or password"):
        _normalize_database_url("jdbc:postgresql://host.neon.tech/neondb?sslmode=require")
