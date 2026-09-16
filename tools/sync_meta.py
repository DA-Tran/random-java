"""Rewrites each hand-written project's META literal to match the catalogue.

A hand-written project declares its own `Meta` in Java while `catalog.py`
declares the same facts in Python, and `generate.py` deliberately refuses to
overwrite a hand-written file - so the two drift the moment anyone writes a
description from memory instead of copying it. `SuiteTests` catches the drift,
but only after a build and a full test run.

This fixes it at the source: the catalogue is authoritative, and this rewrites
the Java to agree. Run it after adding a project, before building.
"""
import re
import sys
import pathlib

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import catalog


def java_string(text: str) -> str:
    return '"%s"' % text.replace("\\", "\\\\").replace('"', '\\"')


def main() -> None:
    changed = []
    for row in catalog.rows():
        if not row["done"]:
            continue
        folder = pathlib.Path("projects") / f"{row['id']:03d}-{row['slug']}"
        source = folder / f"{row['klass']}.java"
        if not source.exists():
            continue
        body = source.read_text(encoding="utf-8")

        # The Meta literal, however it happens to be wrapped across lines.
        pattern = re.compile(
            r"new Meta\(\s*" + str(row["id"]) + r"\s*,\s*"
            + re.escape(java_string(row["slug"])) + r"\s*,.*?\);",
            re.S,
        )
        if not pattern.search(body):
            print(f"  ?  {row['slug']}: no Meta literal found, left alone")
            continue

        replacement = (
            "new Meta({id}, {slug}, {name}, {category}, Kind.{kind},\n"
            "            Difficulty.{difficulty}, {description},\n"
            "            {stack}, {done});"
        ).format(
            id=row["id"],
            slug=java_string(row["slug"]),
            name=java_string(row["name"]),
            category=java_string(row["category"]),
            kind=row["kind"],
            difficulty=row["difficulty"].upper(),
            description=java_string(row["description"]),
            stack=java_string(row.get("stack") or ""),
            done="true",
        )
        updated = pattern.sub(lambda _: replacement, body, count=1)
        if updated != body:
            source.write_text(updated, encoding="utf-8")
            changed.append(row["slug"])

    print(f"meta synced: {len(changed)} file(s) updated")
    for slug in changed:
        print("   ", slug)


if __name__ == "__main__":
    main()
