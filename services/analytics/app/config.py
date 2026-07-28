import os
from pathlib import Path


class Settings:
    database_url: str = os.getenv(
        "DATABASE_URL",
        "postgresql://bookero@127.0.0.1:5433/bookero"
    )

    data_raw_dir: Path = Path(os.getenv("DATA_RAW_DIR", "data/raw"))
    data_processed_dir: Path = Path(os.getenv("DATA_PROCESSED_DIR", "data/processed"))

    openflights_airports_url: str = (
        "https://raw.githubusercontent.com/jpatokal/openflights/master/data/airports.dat"
    )
    openflights_routes_url: str = (
        "https://raw.githubusercontent.com/jpatokal/openflights/master/data/routes.dat"
    )

    def __init__(self):
        self.data_raw_dir.mkdir(parents=True, exist_ok=True)
        self.data_processed_dir.mkdir(parents=True, exist_ok=True)


settings = Settings()
