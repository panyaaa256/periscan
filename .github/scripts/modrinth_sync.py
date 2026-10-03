#!/usr/bin/env python3
"""Brings the Modrinth project page in line with the repository.

The page is described by .github/modrinth/project.json: the name, summary,
categories, license and links, plus the files holding the description, the
icon and the gallery images. Only fields that differ from the project are
sent. The environment (client only) is not set here: Modrinth takes it from
the versions, which get it from the jar's fabric.mod.json on upload.

Usage: modrinth_sync.py [--icon] [--gallery] [--versions] [--dry-run]
       modrinth_sync.py --delete-versions <mod version> [--dry-run]

  --icon      also upload the icon
  --gallery   also upload the gallery images whose title is not there yet
  --versions  also list the uploaded versions, newest first (to check a release)
  --dry-run   only print what would change
  --delete-versions <mod version>
              only delete the versions of that mod version (1.0.0 deletes
              1.0.0+26.2 and so on), so that a release can be uploaded again

Environment: MODRINTH_TOKEN (needs the "Read projects" and "Write projects"
scopes, "Read versions" for --versions and "Delete versions" for
--delete-versions), MODRINTH_PROJECT_ID, and optionally MODRINTH_API for
another API (https://docs.modrinth.com/api/ lists a staging one).
"""

import argparse
import json
import os
import pathlib
import sys
import urllib.error
import urllib.parse
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[2]
CONFIG = ROOT / ".github" / "modrinth" / "project.json"
# Modrinth asks for a user agent that identifies the project.
USER_AGENT = "panyaaa256/periscan (https://github.com/panyaaa256/periscan)"
# Fields of project.json sent as they are; the description is read from body_file.
FIELDS = (
	"title", "description", "categories", "additional_categories", "license_id",
	"source_url", "issues_url", "wiki_url",
)
# Categories are compared without their order.
UNORDERED = ("categories", "additional_categories")


def request(method, path, body=None, content_type=None):
	api = os.environ.get("MODRINTH_API", "https://api.modrinth.com/v2").rstrip("/")
	headers = {"Authorization": os.environ["MODRINTH_TOKEN"], "User-Agent": USER_AGENT}
	if content_type:
		headers["Content-Type"] = content_type
	req = urllib.request.Request(api + path, data=body, method=method, headers=headers)
	try:
		with urllib.request.urlopen(req, timeout=60) as response:
			return response.read()
	except urllib.error.HTTPError as error:
		sys.exit(f"{method} {path.split('?')[0]} failed: {error.code} {error.read().decode(errors='replace')}")


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


def image_type(path):
	return "jpeg" if path.suffix.lower() in (".jpg", ".jpeg") else path.suffix.lower().lstrip(".")


def print_versions(project_id):
	"""Lists what a release uploaded: Modrinth shows none of it for a project that is not public yet."""
	versions = json.loads(request("GET", f"/project/{project_id}/version"))
	names = {}
	# Newest first, the order of the version list on Modrinth.
	for version in sorted(versions, key=lambda v: v["date_published"], reverse=True):
		dependencies = []
		for dependency in version.get("dependencies") or []:
			dependency_id = dependency.get("project_id")
			if dependency_id and dependency_id not in names:
				names[dependency_id] = json.loads(request("GET", f"/project/{dependency_id}"))["slug"]
			dependencies.append(f"{names.get(dependency_id, dependency_id)}({dependency.get('dependency_type')})")
		print(f"version {version['version_number']}: published={version['date_published']}"
				f" name={version['name']!r} type={version['version_type']}"
				f" status={version.get('status')} loaders={version['loaders']} game={version['game_versions']}"
				f" environment={version.get('environment')} dependencies={dependencies}"
				f" files={[f['filename'] for f in version['files']]}")
	print(f"versions: {len(versions)}")


def delete_versions(project_id, mod_version, dry_run):
	versions = json.loads(request("GET", f"/project/{project_id}/version"))
	matching = [v for v in versions if v["version_number"] == mod_version or v["version_number"].startswith(mod_version + "+")]
	for version in sorted(matching, key=lambda v: v["version_number"]):
		print(f"delete version {version['version_number']} ({version['id']})")
		if not dry_run:
			request("DELETE", f"/version/{version['id']}")
	print(f"versions: {len(matching)} of {len(versions)} {'would be deleted' if dry_run else 'deleted'}")


def main():
	parser = argparse.ArgumentParser(description="Brings the Modrinth project page in line with the repository.")
	parser.add_argument("--icon", action="store_true")
	parser.add_argument("--gallery", action="store_true")
	parser.add_argument("--versions", action="store_true")
	parser.add_argument("--dry-run", action="store_true")
	parser.add_argument("--delete-versions", metavar="MOD_VERSION")
	args = parser.parse_args()

	project_id = urllib.parse.quote(os.environ["MODRINTH_PROJECT_ID"], safe="")
	if args.delete_versions:
		delete_versions(project_id, args.delete_versions, args.dry_run)
		return
	config = json.loads(CONFIG.read_text(encoding="utf-8"))
	project = json.loads(request("GET", f"/project/{project_id}"))
	print(f"project: {project.get('slug')} (status: {project.get('status')})")

	changes = changed_fields(config, project)
	for field, value in changes.items():
		print(f"change {field}: {describe(field, value)}")
	if not changes:
		print("fields: up to date")
	elif not args.dry_run:
		request("PATCH", f"/project/{project_id}", json.dumps(changes).encode(), "application/json")
		print(f"fields: updated {len(changes)}")

	if args.icon:
		icon = ROOT / config["icon_file"]
		print(f"icon: {config['icon_file']}")
		if not args.dry_run:
			ext = icon.suffix.lower().lstrip(".")
			request("PATCH", f"/project/{project_id}/icon?ext={ext}", icon.read_bytes(), f"image/{image_type(icon)}")

	if args.gallery:
		existing = {image.get("title") for image in project.get("gallery") or []}
		for ordering, image in enumerate(config["gallery"]):
			if image["title"] in existing:
				print(f"gallery: '{image['title']}' is already there")
				continue
			print(f"gallery: add '{image['title']}' ({image['file']})")
			if args.dry_run:
				continue
			path = ROOT / image["file"]
			query = urllib.parse.urlencode({
				"ext": path.suffix.lower().lstrip("."),
				"featured": "true" if image["featured"] else "false",
				"title": image["title"],
				"description": image["description"],
				"ordering": ordering,
			})
			request("POST", f"/project/{project_id}/gallery?{query}", path.read_bytes(), f"image/{image_type(path)}")

	if args.versions:
		print_versions(project_id)

	if args.dry_run:
		print("dry run: nothing was changed")


if __name__ == "__main__":
	main()
