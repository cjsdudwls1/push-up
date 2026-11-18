#!/usr/bin/env python3
"""
The app's Play tracks, read and moved from the command line.

CI uploads every build on the install branch to the internal track (release.yml); this moves a
build that is already on Play from one track to another, so the closed test and production get the
very bundle the internal testers already had — never a rebuild of it.

Usage:
  tools/play.py status
  tools/play.py promote --from internal --to alpha [--version-code N] [--fraction 0.2]
                        [--notes-file distribution/whatsnew/whatsnew-ko-KR] [--draft]
  tools/play.py listing [--dir docs/store-assets] [--dry-run]

listing replaces the Korean listing's icon, feature graphic and phone screenshots with
icon-512.png, feature-graphic.png and screenshots/*.png (in name order) from --dir, in one edit.
The text of the listing is left as it is. tools/store_screenshots.py makes the pictures.

Tracks: internal, alpha (the closed test), beta (open), production.
--fraction rolls a production release out to that share of users (inProgress); without it the
release is completed, i.e. everyone on the track gets it.

The service account comes from PLAY_SERVICE_ACCOUNT_JSON (the JSON itself, as CI has it) or from
--key / PLAY_SERVICE_ACCOUNT_FILE (a path). It needs the Play Console permissions to release to the
target track; releasing to production is a separate permission from releasing to testing tracks.
Needs google-auth and requests (pip install google-auth requests).
"""
import argparse
import json
import os
import pathlib
import sys

PACKAGE = "io.github.cjsdudwls1.pushuprpg"
API = f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{PACKAGE}"
UPLOAD = f"https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/{PACKAGE}"
SCOPES = ["https://www.googleapis.com/auth/androidpublisher"]
NOTES_LANGUAGE = "ko-KR"


def session(key_path):
    from google.auth.transport.requests import AuthorizedSession
    from google.oauth2 import service_account

    raw = os.environ.get("PLAY_SERVICE_ACCOUNT_JSON")
    if raw:
        creds = service_account.Credentials.from_service_account_info(json.loads(raw), scopes=SCOPES)
    else:
        path = key_path or os.environ.get("PLAY_SERVICE_ACCOUNT_FILE")
        if not path:
            sys.exit("No service account: set PLAY_SERVICE_ACCOUNT_JSON or pass --key.")
        creds = service_account.Credentials.from_service_account_file(path, scopes=SCOPES)
    return AuthorizedSession(creds)


def call(http, method, path, body=None):
    r = http.request(method, API + path, json=body)
    if r.status_code >= 400:
        sys.exit(f"{method} {path}: {r.status_code}\n{r.text}")
    return r.json() if r.content else {}


class Edit:
    """One Play edit: everything inside it lands together on commit, or not at all."""

    def __init__(self, http):
        self.http = http
        self.id = call(http, "POST", "/edits")["id"]
        self.done = False

    def get(self, path):
        return call(self.http, "GET", f"/edits/{self.id}{path}")

    def put(self, path, body):
        return call(self.http, "PUT", f"/edits/{self.id}{path}", body)

    def commit(self):
        call(self.http, "POST", f"/edits/{self.id}:commit")
        self.done = True

    def close(self):
        if not self.done:
            self.http.request("DELETE", f"{API}/edits/{self.id}")


def latest_release(track):
    """The release on a track with the highest version code, whatever its status."""
    releases = [r for r in track.get("releases", []) if r.get("versionCodes")]
    if not releases:
        return None
    return max(releases, key=lambda r: max(int(v) for v in r["versionCodes"]))


def status(http, _args):
    edit = Edit(http)
    try:
        tracks = edit.get("/tracks")["tracks"]
    finally:
        edit.close()
    for t in tracks:
        releases = t.get("releases") or []
        if not releases:
            print(f"{t['track']:<11} -")
        for r in releases:
            fraction = f" {r['userFraction']:.0%}" if "userFraction" in r else ""
            print(f"{t['track']:<11} {','.join(r.get('versionCodes', []))} "
                  f"{r.get('status')}{fraction}  {r.get('name', '')}")


def promote(http, args):
    edit = Edit(http)
    try:
        source = latest_release(edit.get(f"/tracks/{args.source}"))
        if not source:
            sys.exit(f"Nothing on {args.source} to promote.")
        codes = [str(args.version_code)] if args.version_code else source["versionCodes"]

        target = edit.get(f"/tracks/{args.target}")
        current = latest_release(target)
        if current and current["versionCodes"] == codes and current.get("status") == "completed":
            print(f"{args.target} already has {','.join(codes)}; nothing to do.")
            return

        release = {"name": source.get("name") or ",".join(codes), "versionCodes": codes}
        if args.notes_file:
            with open(args.notes_file, encoding="utf-8") as f:
                text = f.read().strip()
            if len(text) > 500:
                sys.exit(f"{args.notes_file} is {len(text)} characters; Play takes 500.")
            release["releaseNotes"] = [{"language": NOTES_LANGUAGE, "text": text}]
        elif source.get("releaseNotes"):
            release["releaseNotes"] = source["releaseNotes"]

        if args.draft:
            release["status"] = "draft"
        elif args.fraction is not None:
            release["status"] = "inProgress"
            release["userFraction"] = args.fraction
        else:
            release["status"] = "completed"

        edit.put(f"/tracks/{args.target}", {"track": args.target, "releases": [release]})
        edit.commit()
        print(f"{','.join(codes)} → {args.target} ({release['status']}"
              f"{' ' + format(args.fraction, '.0%') if args.fraction is not None else ''})")
    finally:
        edit.close()


def listing_images(folder):
    """What the listing gets, by Play's image type, each checked against Play's limits first."""
    from PIL import Image  # only this command needs it

    root = pathlib.Path(folder)
    shots = sorted((root / "screenshots").glob("*.png"))
    images = {
        "icon": [root / "icon-512.png"],
        "featureGraphic": [root / "feature-graphic.png"],
        "phoneScreenshots": shots,
    }
    if not 2 <= len(shots) <= 8:
        sys.exit(f"Play takes 2 to 8 phone screenshots; {root / 'screenshots'} has {len(shots)}.")
    for kind, files in images.items():
        for f in files:
            if not f.is_file():
                sys.exit(f"Missing {f}.")
            w, h = Image.open(f).size
            if kind == "icon" and (w, h) != (512, 512):
                sys.exit(f"{f} is {w}x{h}; the icon is 512x512.")
            if kind == "featureGraphic" and (w, h) != (1024, 500):
                sys.exit(f"{f} is {w}x{h}; the feature graphic is 1024x500.")
            if kind == "phoneScreenshots" and (min(w, h) < 320 or max(w, h) > 3840 or max(w, h) > 2 * min(w, h)):
                sys.exit(f"{f} is {w}x{h}; a screenshot is 320-3840 px a side, the long one at most twice the short.")
    return images


def listing(http, args):
    images = listing_images(args.dir)
    for kind, files in images.items():
        print(f"{kind}: {', '.join(str(f) for f in files)}")
    if args.dry_run:
        return
    edit = Edit(http)
    try:
        for kind, files in images.items():
            path = f"/edits/{edit.id}/listings/{NOTES_LANGUAGE}/{kind}"
            call(http, "DELETE", path)
            for f in files:
                r = http.post(UPLOAD + path, params={"uploadType": "media"},
                              data=f.read_bytes(), headers={"Content-Type": "image/png"})
                if r.status_code >= 400:
                    sys.exit(f"upload {f} as {kind}: {r.status_code}\n{r.text}")
        edit.commit()
        print(f"Listing ({NOTES_LANGUAGE}) updated; Google reviews it before the store shows it.")
    finally:
        edit.close()


def main():
    p = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    p.add_argument("--key", help="service account JSON file")
    sub = p.add_subparsers(dest="cmd", required=True)
    sub.add_parser("status")
    pr = sub.add_parser("promote")
    pr.add_argument("--from", dest="source", default="internal")
    pr.add_argument("--to", dest="target", required=True)
    pr.add_argument("--version-code", type=int)
    pr.add_argument("--notes-file")
    pr.add_argument("--fraction", type=float)
    pr.add_argument("--draft", action="store_true")
    li = sub.add_parser("listing")
    li.add_argument("--dir", default="docs/store-assets")
    li.add_argument("--dry-run", action="store_true", help="check the pictures, upload nothing")
    args = p.parse_args()
    if getattr(args, "fraction", None) is not None and not 0 < args.fraction < 1:
        sys.exit("--fraction is a share between 0 and 1; leave it out for everyone.")
    if args.cmd == "listing" and args.dry_run:
        listing(None, args)
        return
    http = session(args.key)
    {"status": status, "promote": promote, "listing": listing}[args.cmd](http, args)


if __name__ == "__main__":
    main()
