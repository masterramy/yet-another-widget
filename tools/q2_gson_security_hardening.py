from pathlib import Path

build = Path('app/build.gradle')
network = Path('app/src/main/java/com/tommasoberlose/anotherwidget/network/WeatherNetworkApi.kt')

build_text = build.read_text()
old_gson = "implementation 'com.google.code.gson:gson:2.8.6'"
new_gson = "implementation 'com.google.code.gson:gson:2.14.0'"
assert build_text.count(old_gson) == 1, 'expected exact Gson 2.8.6 pin once'
assert new_gson not in build_text
build_text = build_text.replace(old_gson, new_gson)
build.write_text(build_text)

text = network.read_text()
assert text.count('import com.google.gson.internal.LinkedTreeMap\n') == 1
assert text.count('LinkedTreeMap') >= 10, 'expected historical nested JSON implementation casts'
text = text.replace('import com.google.gson.internal.LinkedTreeMap\n', '')
text = text.replace('List<LinkedTreeMap<String, Any>>', 'List<Map<String, Any>>')
text = text.replace('LinkedTreeMap<String, Any>', 'Map<String, Any>')
text = text.replace('LinkedTreeMap<*, *>', 'Map<*, *>')
assert 'LinkedTreeMap' not in text
network.write_text(text)

remaining = []
for path in Path('app/src/main/java').rglob('*'):
    if path.is_file() and path.suffix in {'.kt', '.java'}:
        if 'com.google.gson.internal.LinkedTreeMap' in path.read_text(errors='ignore'):
            remaining.append(str(path))
assert not remaining, f'internal Gson LinkedTreeMap imports remain: {remaining}'

print('Gson security hardening applied: 2.14.0 and public Map-only nested JSON parsing.')
