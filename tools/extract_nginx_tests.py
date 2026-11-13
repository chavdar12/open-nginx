#!/usr/bin/env python3
"""Extract the nginx.conf files embedded in https://github.com/nginx/nginx-tests (BSD-2-Clause)
into src/test/testData/nginx-tests/, as a real-world corpus for NginxCorpusTest.

usage: python3 tools/extract_nginx_tests.py <path-to-nginx-tests-checkout>
"""
import pathlib, re, sys

src = pathlib.Path(sys.argv[1])
out = pathlib.Path(__file__).parent.parent / "src/test/testData/nginx-tests"
out.mkdir(parents=True, exist_ok=True)
for old in out.glob("*.conf"):
    old.unlink()


def expand(m):  # same placeholders nginx-tests' write_file_expand() fills in
    k = m.group(1)
    if k.startswith("PORT_"):
        return re.sub(r"\D", "", k) or "8080"
    return {"TESTDIR": "/tmp/nginx-test", "TEST_GLOBALS": "", "TEST_GLOBALS_HTTP": "",
            "TEST_GLOBALS_STREAM": "", "RETURN": "return", "LONG_PATTERN": "long_pattern"}[k]


n = 0
for t in sorted(src.glob("*.t")):
    blocks = re.findall(r"write_file_expand\('nginx\.conf', <<'EOF'\)[^\n]*\n(.*?)\nEOF\n", t.read_text("utf-8", "replace"), re.S)
    for i, conf in enumerate(blocks):
        name = t.stem + (f"_{i + 1}" if len(blocks) > 1 else "") + ".conf"
        (out / name).write_text(re.sub(r"%%([A-Z_0-9]+)%%", expand, conf) + "\n")
        n += 1
(out / "LICENSE").write_text((src / "LICENSE").read_text())
print(f"{n} configs -> {out}")
