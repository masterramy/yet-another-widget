#!/usr/bin/env python3
"""Guard the exact accepted event-horizon and Weather.gov labels across shipped locales.

The event horizon is an upper/look-ahead bound, never a minimum waiting time.
Run this before the Android build so mistaken copy cannot silently reappear.
"""
from pathlib import Path
import xml.etree.ElementTree as ET

RES = Path("app/src/main/res")
TITLES = {
    "default": "Show events until",
    "da": "Vis begivenheder frem til",
    "de": "Termine anzeigen bis",
    "es": "Mostrar eventos hasta",
    "fr": "Afficher les événements jusqu\\'à",
    "in": "Tampilkan acara hingga",
    "it": "Mostra eventi fino a",
    "pl": "Pokazuj wydarzenia do",
    "pt": "Mostrar eventos até",
    "ru": "Показывать события",
    "sk": "Zobrazovať udalosti do",
    "zh-rCN": "最晚显示活动",
}
FORWARD = {
    "de": ["in 3 Stunden","in 6 Stunden","in 12 Stunden","in 24 Stunden","in 3 Tagen","in 7 Tagen","in 30 Minuten","in 1 Stunde"],
    "fr": ["dans 3 heures","dans 6 heures","dans 12 heures","dans 24 heures","dans 3 jours","dans 7 jours","dans 30 minutes","dans 1 heure"],
    "sk": ["o 3 hodiny","o 6 hodín","o 12 hodín","o 24 hodín","o 3 dni","o 7 dní","o 30 minút","o 1 hodinu"],
    "ru": ["через 3 часа","через 6 часов","через 12 часов","через 24 часа","через 3 дня","через 7 дней","через 30 минут","через 1 час"],
}
CREDIT = r"Weather.gov (USA)\nby National Weather Service"


def strings_for(locale):
    suffix = "" if locale == "default" else "-" + locale
    path = RES / ("values" + suffix) / "strings.xml"
    root = ET.parse(path).getroot()
    strings = {}
    for elem in root.findall("string"):
        key = elem.attrib.get("name")
        if key in strings:
            raise AssertionError(f"{path}: duplicate string {key}")
        strings[key] = "".join(elem.itertext())
    return path, strings


def require_equal(path, name, actual, expected):
    if actual != expected:
        raise AssertionError(f"{path} {name}: expected {expected!r}, got {actual!r}")


for locale, title in TITLES.items():
    path, strings = strings_for(locale)
    require_equal(path, "settings_show_until_title",
                  strings.get("settings_show_until_title"), title)
    credit = strings.get("settings_weather_provider_weather_gov")
    if credit is not None:
        require_equal(path, "settings_weather_provider_weather_gov", credit, CREDIT)

for locale, expected_list in FORWARD.items():
    path, strings = strings_for(locale)
    for index, text in enumerate(expected_list):
        key = f"settings_show_until_subtitle_{index}"
        require_equal(path, key, strings.get(key), text)

for path in RES.glob("values*/strings.xml"):
    if "National Weather Services" in path.read_text(encoding="utf-8"):
        raise AssertionError(f"{path}: obsolete incorrect NOAA provider name")

print(f"YAW locale copy contract PASS: {len(TITLES)} titles, "
      f"{sum(len(v) for v in FORWARD.values())} time labels, NWS attributions")
