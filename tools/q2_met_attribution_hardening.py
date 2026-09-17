from pathlib import Path

base = Path('.')
helper = base / 'app/src/main/java/com/tommasoberlose/anotherwidget/helpers/WeatherHelper.kt'
strings = base / 'app/src/main/res/values/strings.xml'

def replace_exact(path: Path, old: str, new: str, count: int = 1):
    text = path.read_text(encoding='utf-8')
    actual = text.count(old)
    if actual != count:
        raise SystemExit(f'{path}: expected {count} occurrence(s) of {old!r}, found {actual}')
    path.write_text(text.replace(old, new), encoding='utf-8')

replace_exact(
    helper,
    'Constants.WeatherProvider.YR -> "https://www.yr.no/"',
    'Constants.WeatherProvider.YR -> "https://api.met.no/weatherapi/locationforecast/2.0/documentation"'
)
replace_exact(
    strings,
    '<string name="settings_weather_provider_yr" translatable="false">YR.no/Met.no\\nby Meteorologisk Institutt</string>',
    '<string name="settings_weather_provider_yr" translatable="false">MET Norway\\nLocationforecast</string>'
)
replace_exact(
    strings,
    '<string name="weather_provider_info_yr_subtitle">\\n</string>',
    '<string name="weather_provider_info_yr_subtitle">Weather data provided by MET Norway Locationforecast.</string>'
)

print('MET provider attribution hardening applied: official naming, attribution, and documentation link.')
