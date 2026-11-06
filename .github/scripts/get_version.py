from pathlib import Path
import re

text = Path("CHANGELOG.md").read_text(encoding="utf-8")
m = re.search(r"^##\s+InstallBlock\s+([0-9]+\.[0-9]+\.[0-9]+)\s*$", text, re.M)
if not m:
    m = re.search(r"^##\s+\[?([0-9]+\.[0-9]+\.[0-9]+)\]?\s*$", text, re.M)
if not m:
    raise SystemExit("Could not find a top changelog heading like: ## InstallBlock 1.0.0 or ## 1.0.0")
print(m.group(1))
