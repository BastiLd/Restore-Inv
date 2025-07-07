import json
import re

json_path = 'src/main/resources/fabric.mod.json'

with open(json_path, 'r', encoding='utf-8') as f:
    data = f.read()

# Find the version string
match = re.search(r'"version"\s*:\s*"([0-9]+)\.([0-9]+)\.([0-9]+)"', data)
if not match:
    raise Exception('Version string not found!')

major, minor, patch = map(int, match.groups())

# Increment version
if patch < 9:
    patch += 1
elif minor < 9:
    patch = 0
    minor += 1
else:
    patch = 0
    minor = 0
    major += 1

new_version = f'{major}.{minor}.{patch}'

# Replace in file
new_data = re.sub(r'("version"\s*:\s*")([0-9]+\.[0-9]+\.[0-9]+)(")', f'"version": "{new_version}"', data)

with open(json_path, 'w', encoding='utf-8') as f:
    f.write(new_data)

print(f'Version bumped to {new_version}') 