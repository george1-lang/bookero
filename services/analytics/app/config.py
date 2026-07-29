import os
from pathlib import Path


class Settings:
    database_url: str = os.getenv(
        "DATABASE_URL",
        "postgresql://bookero:bookero@localhost:5432/bookero"
    )

    # Comma-separated browser origins allowed to call this service directly.
    cors_allowed_origins: list[str] = [
        origin.strip()
        for origin in os.getenv("CORS_ALLOWED_ORIGINS", "http://localhost:3000").split(",")
        if origin.strip()
    ]

    data_raw_dir: Path = Path(os.getenv("DATA_RAW_DIR", "data/raw"))
    data_processed_dir: Path = Path(os.getenv("DATA_PROCESSED_DIR", "data/processed"))

    openflights_airports_url: str = os.getenv(
        "OPENFLIGHTS_AIRPORTS_URL",
        "https://raw.githubusercontent.com/jpatokal/openflights/master/data/airports.dat",
    )
    openflights_routes_url: str = os.getenv(
        "OPENFLIGHTS_ROUTES_URL",
        "https://raw.githubusercontent.com/jpatokal/openflights/master/data/routes.dat",
    )

    def __init__(self):
        self.data_raw_dir.mkdir(parents=True, exist_ok=True)
        self.data_processed_dir.mkdir(parents=True, exist_ok=True)


settings = Settings()
