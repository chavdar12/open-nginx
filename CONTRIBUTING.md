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
5. Open a pull request against `main`. CI, CodeQL and detekt must pass, and PRs are squash-merged.

### detekt

[detekt](https://detekt.dev) runs with its default rules and fails on any finding. Findings that predate it are
listed in `config/detekt/baseline.xml`; fix them when you touch that code, but don't add new ones. To run it
locally, download `detekt-cli-1.23.8-all.jar` from the
[release page](https://github.com/detekt/detekt/releases/tag/v1.23.8) and run:

```sh
java -jar detekt-cli-1.23.8-all.jar --build-upon-default-config --config config/detekt/detekt.yml \
  --baseline config/detekt/baseline.xml --input src/main/kotlin,src/test/kotlin
```

After fixing baselined findings, add `--create-baseline` once to shrink the baseline.

By contributing you agree that your work is licensed under the [Apache License 2.0](LICENSE).
