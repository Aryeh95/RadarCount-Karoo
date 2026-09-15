#!/usr/bin/env python3
"""Print the CHANGELOG.md section for one version, for use as release notes.

    scripts/release_notes.py 0.2.16          # section body to stdout
    scripts/release_notes.py --check 0.2.16  # exit 1 if there is no section
    scripts/release_notes.py --list          # every version that has a section
"""
import re, sys

HEADING = re.compile(r"^## (\d+\.\d+\.\d+)(?:\s+—\s+(\S+))?\s*$")


def sections():
    out, cur, buf = {}, None, []
    for line in open("CHANGELOG.md", encoding="utf-8"):
        m = HEADING.match(line)
        if m:
            if cur:
                out[cur] = "".join(buf).strip() + "\n"
            cur, buf = m.group(1), []
        elif cur:
            buf.append(line)
    if cur:
        out[cur] = "".join(buf).strip() + "\n"
    return out


def main():
    args = sys.argv[1:]
    secs = sections()
    if args == ["--list"]:
        print("\n".join(secs))
        return
    check = args and args[0] == "--check"
    version = args[-1] if args else ""
    if version not in secs:
        print(f"CHANGELOG.md has no section for {version}", file=sys.stderr)
        sys.exit(1)
    if not check:
        sys.stdout.write(secs[version])


if __name__ == "__main__":
    main()
