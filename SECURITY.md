# Security policy

## Supported versions

Only the latest release (or `main`, until the first release) gets security fixes.

## Reporting a vulnerability

Please do not open a public issue. Report it privately through
[GitHub's private vulnerability reporting](https://github.com/chavdar12/open-nginx/security/advisories/new).
You will get an answer within a week.

This covers the plugin itself, for example running `nginx -t` on an untrusted project or a crafted config that
crashes or hangs the IDE. Weaknesses in nginx configs that the plugin fails to warn about are regular bugs: open an
issue for those.
