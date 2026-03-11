# Nostos IntelliJ Plugin

IntelliJ IDEA language plugin for [Nostos](https://heynostos.tech) — a functional-first programming language with
lightweight concurrency, pattern matching, and non-blocking I/O.

## Features

- **Syntax highlighting** — keywords, strings, numbers, comments, operators, types
- **Code completion** — keyword completion with context-aware suffixes
- **Go to Definition** — navigate to declarations of values, functions, and types
- **Find Usages** — find all references to a symbol across files
- **Code formatting** — automatic indentation and spacing (Ctrl+Alt+L)
- **Structure view** — outline of declarations, modules, types, and traits
- **Code folding** — collapse blocks, functions, and modules
- **Brace matching** — highlight matching braces, brackets, and parentheses
- **Commenting** — toggle line comments (`//`) and block comments (`/* */`)

## Installation

### From JetBrains Marketplace

Search for "Nostos" in **Settings → Plugins → Marketplace**.

### From disk

1. Download the latest release zip from [Releases](https://github.com/hallyhaa/intellij-nostos/releases)
2. In IntelliJ IDEA, go to **Settings → Plugins → ⚙️ → Install Plugin from Disk...**
3. Select the zip file

## Supported file types

`.nos` files are automatically recognized as Nostos source files.

## Compatibility

- IntelliJ IDEA 2024.3 and later (Community and Ultimate)
- Other JetBrains IDEs based on the IntelliJ Platform (CLion, PyCharm, etc.)

## Building from source

```bash
./gradlew build -x buildSearchableOptions
```

The plugin zip will be in `build/distributions/`.

## About Nostos

Nostos (νόστος, "homecoming") is a functional-first language inspired by the journey of Odysseus. It features:

- Pattern matching with guards
- Lightweight concurrency with spawn/receive
- Algebraic data types and traits
- Non-blocking I/O
- Module system

See the [Nostos repository](https://github.com/pegesund/nostos) for the language itself.
