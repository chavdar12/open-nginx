# open-nginx

[![CI](https://github.com/chavdar12/open-nginx/actions/workflows/ci.yml/badge.svg)](https://github.com/chavdar12/open-nginx/actions/workflows/ci.yml)
[![Code Quality](https://github.com/chavdar12/open-nginx/actions/workflows/code-quality.yml/badge.svg)](https://github.com/chavdar12/open-nginx/actions/workflows/code-quality.yml)

An IntelliJ Platform plugin for nginx configuration files. It works in every JetBrains IDE (2026.2 and later): IntelliJ
IDEA,
PyCharm, WebStorm, GoLand and the rest.

Directive data is generated from nginx's own documentation and source code, so checks and docs follow what nginx
actually
accepts.

## Features

**Editing**

- Syntax highlighting, structure view, folding, brace matching and a configurable formatter (column alignment, tabs,
  wrapping)
- Completion that knows which block you are in, including inside snippets pulled in by `include`: directives, values,
  variables, HTTP headers, MIME types and file paths
- Completion across files: upstreams, `$variables` from `map`/`set`/`geo`, and `@named` locations from any file in the
  project
- Hover docs from nginx.org for directives, their parameters, variables and HTTP headers
- Live templates: `proxy`, `upstream`, `ssl`, `redirect`, `ws`, `php`, `cors`, `cache`, `static`, `gzip`, `spa`

**Navigation and refactoring**

- Go to declaration, Find Usages, Rename and Safe Delete for upstreams, variables and named locations
- Cmd/Ctrl+click on `include` (globs open every match), `proxy_pass http://upstream`, `js_import` and
  `js_content module.function`
- Extract a block into an include file, or inline an include back

**Checks**

- Argument counts and values, directives in the wrong block, typos ("did you mean"), unknown variables, missing include
  files
- Directives nginx rejects as duplicate (the list is generated from nginx's C sources), clashing `server_name` and
  `default_server` across included files, obsolete directives
- Invalid regexes in `location`, `rewrite`, `if`, `server_name` and `map`, plus ReDoS-prone patterns
- Security checks in the spirit of [gixy](https://github.com/yandex/gixy): alias path traversal, lost `add_header`, Host
  spoofing, HTTP response splitting, weak origin checks, reflected CORS, weak TLS, SSRF, version disclosure
- `nginx -t` errors shown on the right lines when an `nginx` binary is on `PATH`
- Quick fixes: replace an invalid value, fix a typo, move to the right block, remove a duplicate, copy inherited
  `add_header` lines, add an HTTP to HTTPS redirect

**Modules and file types**

- Third-party modules: OpenResty (Lua), headers-more, set-misc, echo, brotli and geoip2. Unknown directives and
  variables
  can be added to the project's known list (stored in `.idea/nginx.xml`).
- Lua inside `*_by_lua_block` is treated as Lua when a Lua plugin such as EmmyLua2 is installed
- nginx files are recognized by name (`nginx.conf`, `*.nginx`, `fastcgi_params` and friends) and by content (any
  `*.conf`
  that looks like nginx), including Docker `*.conf.template` files with `${ENV}` placeholders

## Installation

The plugin is not on JetBrains Marketplace yet. To install it from source:

```sh
./gradlew buildPlugin
```

Then in the IDE: **Settings | Plugins | ⚙ | Install Plugin from Disk…** and pick `build/distributions/open-nginx-*.zip`.

## Development

Needs a JDK (CI and development use JDK 25). Gradle downloads the IDE to build against on first run.

| Command                 | What it does                                                                                          |
|-------------------------|-------------------------------------------------------------------------------------------------------|
| `./gradlew runIde`      | Starts a sandbox IDE with the plugin installed                                                        |
| `./gradlew test`        | Runs the tests, including 508 configs from nginx's own test suite that must produce no false warnings |
| `./gradlew buildPlugin` | Builds the installable zip in `build/distributions/`                                                  |

The same tasks are available as run configurations in `.run/`.

If tests fail with `NoSuchMethodError` after pulling, Gradle's build cache served a stale class:
`./gradlew test --no-build-cache --rerun-tasks`.

### Project layout

One class per file. Helper functions live in their own files (`NginxSniffing.kt`, `NginxFixes.kt`, `NginxWhitespace.kt`...)
and shared constants in `NginxConstants.kt`.

```
src/main/kotlin/com/css/nginx/
  NginxConstants.kt       shared names, patterns and directive sets
  lexer/ parser/ psi/     tokens, parse tree, references and cross-file declarations
  completion/ docs/       completion and hover docs
  inspection/             checks and their quick fixes
  intention/              extract/inline include, HTTPS redirect
  formatter/ editor/      formatter, folding, braces, commenter, live template context
  navigation/ structure/  find usages, rename, safe delete, structure view
  lang/ highlighting/     file type detection, colors
  inject/                 Lua injection
src/main/resources/nginx/ directive, variable and parameter data (generated, see below)
src/test/testData/        hand-written configs and the nginx-tests corpus
tools/                    generators for the data files
```

### Regenerating the data

The files in `src/main/resources/nginx/` are generated; edit the generators in `tools/`, not the files.

| Data                                                                    | Source                                                    | Command                                                       |
|-------------------------------------------------------------------------|-----------------------------------------------------------|---------------------------------------------------------------|
| Directives, variables, parameters, obsolete directives (with HTML docs) | [nginx/nginx.org](https://github.com/nginx/nginx.org)     | `python3 tools/gen_directives.py <nginx.org checkout>`        |
| Directives that may appear once per block                               | [nginx/nginx](https://github.com/nginx/nginx)             | `python3 tools/gen_single.py <nginx checkout>`                |
| Third-party module directives                                           | the modules' READMEs                                      | `python3 tools/gen_thirdparty.py <folder of READMEs>`         |
| Test corpus                                                             | [nginx/nginx-tests](https://github.com/nginx/nginx-tests) | `python3 tools/extract_nginx_tests.py <nginx-tests checkout>` |

`headers.tsv` is the one hand-written data file.

## Credits

Directive data and documentation come from the nginx documentation and source code (© Igor Sysoev, Nginx, Inc.,
BSD-2-Clause) and from the READMEs of the supported third-party modules (BSD-licensed). The test corpus is taken from
nginx-tests (BSD-2-Clause). Security checks are modeled on [gixy](https://github.com/yandex/gixy).

## License

[Apache License 2.0](LICENSE), © 2026 Chavdar Shishkov. The generated nginx data keeps its upstream BSD-2-Clause terms,
noted at the top of each generated file.
