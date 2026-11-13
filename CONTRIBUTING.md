# Contributing

Thanks for helping. Bug reports, false warnings and pull requests are all welcome.

## Reporting a problem

Open an issue with the smallest nginx config that shows it, what the plugin did and what you expected. False
warnings are bugs: if `nginx -t` accepts a config, the plugin should not flag it.

## Making a change

1. Build and test with JDK 25: `./gradlew test` (see [Development](README.md#development)).
2. Add a test next to the existing ones in `src/test/kotlin/`. A false-warning fix usually needs one config in a
   highlighting test.
3. Follow the [project layout](README.md#project-layout): one class per file, helper functions in their own files,
   shared constants in `NginxConstants.kt`. Run the IDE's **Reformat Code** and **Optimize Imports** before you commit.
4. Do not edit the generated files in `src/main/resources/nginx/`; change the generators in `tools/` instead.
5. Open a pull request against `main`. CI and CodeQL must pass, and PRs are squash-merged.

By contributing you agree that your work is licensed under the [Apache License 2.0](LICENSE).
