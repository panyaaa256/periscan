#!/usr/bin/env python3
"""Brings the Modrinth project page in line with the repository.

The page is described by .github/modrinth/project.json: the name, summary,
categories, license and links, plus the file holding the description. Only
fields that differ from the project are sent. The release workflow runs this
after every release.

Not set here: the icon and the gallery (changed on Modrinth itself), and the
environment (Modrinth takes it from the versions, which get it from the jar's
fabric.mod.json on upload).

Environment: MODRINTH_TOKEN (needs the "Read projects" and "Write projects"
scopes), MODRINTH_PROJECT_ID, and optionally MODRINTH_API for another API
(https://docs.modrinth.com/api/ lists a staging one).
"""

import json
import os
import pathlib
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[2]
CONFIG = ROOT / ".github" / "modrinth" / "project.json"
# Modrinth asks for a user agent that identifies the project.
USER_AGENT = "panyaaa256/periscan (https://github.com/panyaaa256/periscan)"
# Fields of project.json sent as they are; the description is read from body_file.
FIELDS = (
    "title",
    "description",
    "categories",
    "additional_categories",
    "license_id",
    "source_url",
    "issues_url",
    "wiki_url",
)
# Categories are compared without their order.
UNORDERED = ("categories", "additional_categories")
# Seconds to wait before each new try of a request that failed temporarily.
RETRY_DELAYS = (5, 15, 30)


def request(method, path, body=None, content_type=None):
    api = os.environ.get("MODRINTH_API", "https://api.modrinth.com/v2").rstrip("/")
    headers = {"Authorization": os.environ["MODRINTH_TOKEN"], "User-Agent": USER_AGENT}
    if content_type:
        headers["Content-Type"] = content_type
    req = urllib.request.Request(api + path, data=body, method=method, headers=headers)
    name = f"{method} {path}"
    # A gateway error or a timeout is tried again: Modrinth sometimes answers
    # 502 for a few minutes.
    delays = list(RETRY_DELAYS)
    while True:
        try:
            with urllib.request.urlopen(req, timeout=60) as response:
                return response.read()
        except urllib.error.HTTPError as error:
            failure = f"{error.code} {error.read().decode(errors='replace')[:300]}"
            temporary = error.code in (502, 503, 504)
        except (urllib.error.URLError, TimeoutError) as error:
            failure = str(error)
            temporary = True
        if not temporary or not delays:
            sys.exit(f"{name} failed: {failure}")
        delay = delays.pop(0)
        print(f"{name} failed ({failure.splitlines()[0][:80]}), trying again in {delay} s")
        time.sleep(delay)


def current_license_id(project):
    license_info = project.get("license")
    return license_info.get("id") if isinstance(license_info, dict) else None


def changed_fields(config, project):
    wanted = {field: config[field] for field in FIELDS}
    wanted["body"] = (ROOT / config["body_file"]).read_text(encoding="utf-8").strip()
    changes = {}
    for field, value in wanted.items():
        current = current_license_id(project) if field == "license_id" else project.get(field)
        if field == "body" and isinstance(current, str):
            current = current.strip()
        if field in UNORDERED:
            same = sorted(current or []) == sorted(value)
        else:
            same = current == value
        if not same:
            changes[field] = value
    return changes


def describe(field, value):
    return f"{len(value)} characters" if field == "body" else json.dumps(value, ensure_ascii=False)


def main():
    project_id = urllib.parse.quote(os.environ["MODRINTH_PROJECT_ID"], safe="")
    config = json.loads(CONFIG.read_text(encoding="utf-8"))
    project = json.loads(request("GET", f"/project/{project_id}"))
    print(f"project: {project.get('slug')} (status: {project.get('status')})")

    changes = changed_fields(config, project)
    for field, value in changes.items():
        print(f"change {field}: {describe(field, value)}")
    if not changes:
        print("fields: up to date")
        return
    request("PATCH", f"/project/{project_id}", json.dumps(changes).encode(), "application/json")
    print(f"fields: updated {len(changes)}")


if __name__ == "__main__":
    main()
