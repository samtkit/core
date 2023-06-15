<h1 align="center">SAMT - Simple API Modeling Toolkit</h1>

<div align="center">

[![Latest Stable Release on GitHub](https://img.shields.io/github/v/release/samtkit/core?display_name=tag&sort=semver)](https://github.com/samtkit/core/releases/latest)
[![Maven Central](https://img.shields.io/maven-central/v/tools.samt/public-api)](https://central.sonatype.com/namespace/tools.samt)
[![Total Downloads on GitHub](https://img.shields.io/github/downloads/samtkit/core/total)](https://github.com/samtkit/core/releases/latest)
[![MIT License](https://img.shields.io/github/license/samtkit/core)](./LICENSE)

</div>

<p align="center">
  <i>Tired of unreadable OpenAPI YAML files and a plethora of different tools?
    <br>SAMT is a developer-focused, extensible and easy-to-learn toolkit for modeling APIs using a business-first approach</i>
  <br>
</p>

<hr>

## Documentation

Get started with SAMT, learn fundamental concepts or extend SAMT with a custom generator.

- [Getting Started](https://github.com/samtkit/core/wiki/Getting-Started)
- [Modeling Concepts](https://github.com/samtkit/core/wiki/Modeling-Concepts)
- [SAMT Template](https://github.com/samtkit/template)
- [Visual Studio Code Plugin](https://marketplace.visualstudio.com/items?itemName=samt.samt)
- [API Documentation](https://docs.samt.tools)

### Advanced

- [Authoring Generators](https://github.com/samtkit/core/wiki/Authoring-Generators)
- [Architecture](https://github.com/samtkit/core/wiki/Architecture)

## Development Setup

SAMT is written in [Kotlin](https://kotlinlang.org/) and uses [Gradle](https://gradle.org/) as a build tool,
for the best developer experience we recommend using [IntelliJ IDEA](https://www.jetbrains.com/idea/).

If you want to start SAMT locally, simply clone the repository and compile it using Gradle:

```shell
./gradlew assemble
```

And then use this locally compiled SAMT to compile your SAMT files:

```shell
java -jar ./cli/build/libs/samt-cli.jar compile ./specification/examples/todo-service/*.samt
```

If you are interested in learning about the functionality and operation of the [SAMT Visual Studio Code Plugin](https://github.com/samtkit/vscode)
or methods for running and debugging the related language server on your local machine,
related documentation is available in the [SAMT VS Code Wiki](https://github.com/samtkit/vscode/wiki).

## Contributing

Want to report a bug, contribute code, or improve documentation? Excellent!
Simply create an [issue](https://github.com/samtkit/core/issues),
open a [pull request](https://github.com/samtkit/core/pulls) or
start a [discussion](https://github.com/samtkit/core/discussions).
