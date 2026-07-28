import pytest
from app.etl import parse_airports, parse_routes, haversine


def test_parse_airports_valid():
    content = """1,"Goroka Airport","Goroka","Papua New Guinea","GKA","AYGA",-6.081689,145.391881,5282,10,"U","Pacific/Port_Moresby","airport","OurAirports"
2,"Madang Airport","Madang","Papua New Guinea","MAG","AYMD",-5.207083,145.789001,20,10,"U","Pacific/Port_Moresby","airport","OurAirports"
3,"Mount Hagen Kagamuga Airport","Mount Hagen","Papua New Guinea","HGH","AYMH",-5.826789,144.296061,5388,10,"U","Pacific/Port_Moresby","airport","OurAirports"
"""
    airports = parse_airports(content)
    assert len(airports) == 3
    assert airports[0]["code"] == "GKA"
    assert airports[0]["name"] == "Goroka Airport"
    assert airports[0]["city"] == "Goroka"
    assert airports[0]["lat"] == -6.081689
    assert airports[0]["lon"] == 145.391881


def test_parse_airports_skip_invalid_iata():
    content = """1,"Test Airport","Test","Test","XX","XYZ",-5.0,145.0,20,10,"U","UTC","airport","OurAirports"
2,"Real Airport","Real","Real","ABC","ABCD",-5.0,145.0,20,10,"U","UTC","airport","OurAirports"
"""
    airports = parse_airports(content)
    assert len(airports) == 1
    assert airports[0]["code"] == "ABC"


def test_parse_airports_skip_backslash_n():
    content = """1,"Unknown Airport","Unknown","Unknown","-5.0","145.0","20","10","U","\\N","yes","UTC","airport","OurAirports"
"""
    airports = parse_airports(content)
    assert len(airports) == 0


def test_parse_routes_valid():
    content = """2B,410,AER,2965,KZN,2990,0,0,CR2
2B,410,ASF,2966,KZN,2990,0,0,CR2
"""
    routes = parse_routes(content)
    assert len(routes) == 2
    assert routes[0]["src_iata"] == "AER"
    assert routes[0]["dst_iata"] == "KZN"


def test_parse_routes_skip_backslash_n():
    content = """2B,410,\\N,2965,KZN,2990,0,0,CR2
2B,410,AER,2965,\\N,2990,0,0,CR2
"""
    routes = parse_routes(content)
    assert len(routes) == 0


def test_haversine_distance():
    lat1, lon1 = 40.7128, -74.0060
    lat2, lon2 = 51.5074, -0.1278
    distance = haversine(lat1, lon1, lat2, lon2)
    assert 5500 < distance < 5600
