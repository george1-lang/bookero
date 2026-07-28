import httpx
import io
import csv
from pathlib import Path
from math import radians, cos, sin, asin, sqrt
from typing import Tuple
from uuid import uuid4

from sqlalchemy import text
from sqlalchemy.orm import Session

from app.config import settings


def haversine(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    lon1, lat1, lon2, lat2 = map(radians, [lon1, lat1, lon2, lat2])
    dlon = lon2 - lon1
    dlat = lat2 - lat1
    a = sin(dlat / 2) ** 2 + cos(lat1) * cos(lat2) * sin(dlon / 2) ** 2
    c = 2 * asin(sqrt(a))
    r = 6371
    return c * r


def download_file(url: str, cache_path: Path, refresh: bool = False) -> str:
    if not refresh and cache_path.exists():
        return cache_path.read_text(encoding="utf-8")

    response = httpx.get(url, timeout=30.0)
    response.raise_for_status()
    content = response.text
    cache_path.write_text(content, encoding="utf-8")
    return content


def parse_airports(content: str) -> list[dict]:
    airports = []
    reader = csv.reader(io.StringIO(content))
    for row in reader:
        if len(row) < 8:
            continue
        code, name, city, country, lat_str, lon_str = row[4], row[1], row[2], row[3], row[6], row[7]
        if code == "\\N" or len(code) != 3:
            continue
        try:
            lat = float(lat_str)
            lon = float(lon_str)
        except (ValueError, IndexError):
            continue

        airports.append({
            "code": code,
            "name": name,
            "city": city,
            "country": country,
            "lat": lat,
            "lon": lon,
        })
    return airports


def parse_routes(content: str) -> list[dict]:
    routes = []
    reader = csv.reader(io.StringIO(content))
    for row in reader:
        if len(row) < 5:
            continue
        src_iata, dst_iata = row[2], row[4]
        if src_iata == "\\N" or dst_iata == "\\N":
            continue
        routes.append({
            "src_iata": src_iata,
            "dst_iata": dst_iata,
        })
    return routes


def upsert_airports(db: Session, airports: list[dict]) -> int:
    if not airports:
        return 0

    values_clause = ",".join(
        f"('{a['code']}', '{a['name'].replace(chr(39), chr(39) * 2)}', "
        f"'{a['city'].replace(chr(39), chr(39) * 2)}', "
        f"'{a['country'].replace(chr(39), chr(39) * 2)}', {a['lat']}, {a['lon']})"
        for a in airports
    )

    query = f"""
    INSERT INTO airport (code, name, city, country, lat, lon)
    VALUES {values_clause}
    ON CONFLICT (code) DO UPDATE SET
      name = EXCLUDED.name,
      city = EXCLUDED.city,
      country = EXCLUDED.country,
      lat = EXCLUDED.lat,
      lon = EXCLUDED.lon;
    """

    db.execute(text(query))
    db.commit()
    return len(airports)


def upsert_routes(
    db: Session,
    routes: list[dict],
    airport_codes: set[str]
) -> int:
    if not routes:
        return 0

    airports_by_code = {
        row[0]: (row[1], row[2])
        for row in db.execute(text("SELECT code, lat, lon FROM airport")).fetchall()
    }

    filtered_routes = []
    seen = set()
    for route in routes:
        src, dst = route["src_iata"], route["dst_iata"]
        if src not in airports_by_code or dst not in airports_by_code:
            continue
        if (src, dst) in seen:
            continue

        lat1, lon1 = airports_by_code[src]
        lat2, lon2 = airports_by_code[dst]
        distance_km = int(haversine(lat1, lon1, lat2, lon2))

        filtered_routes.append({
            "id": str(uuid4()),
            "src_iata": src,
            "dst_iata": dst,
            "distance_km": distance_km,
        })
        seen.add((src, dst))

    if not filtered_routes:
        return 0

    values_clause = ",".join(
        f"('{r['id']}', '{r['src_iata']}', '{r['dst_iata']}', {r['distance_km']})"
        for r in filtered_routes
    )

    query = f"""
    INSERT INTO route (id, origin_code, dest_code, distance_km)
    VALUES {values_clause}
    ON CONFLICT (origin_code, dest_code) DO UPDATE SET
      distance_km = EXCLUDED.distance_km;
    """

    db.execute(text(query))
    db.commit()
    return len(filtered_routes)


async def run_etl(db: Session, refresh: bool = False) -> Tuple[int, int]:
    airports_cache = settings.data_raw_dir / "airports.dat"
    routes_cache = settings.data_raw_dir / "routes.dat"

    airports_content = download_file(
        settings.openflights_airports_url,
        airports_cache,
        refresh=refresh
    )
    routes_content = download_file(
        settings.openflights_routes_url,
        routes_cache,
        refresh=refresh
    )

    airports = parse_airports(airports_content)
    routes = parse_routes(routes_content)

    airports_upserted = upsert_airports(db, airports)
    routes_upserted = upsert_routes(db, routes, set(a["code"] for a in airports))

    return airports_upserted, routes_upserted
