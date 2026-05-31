# Nostos IntelliJ Plugin

IntelliJ IDEA language plugin for [Nostos](https://heynostos.tech) — a functional-first programming language with
lightweight concurrency, pattern matching, and non-blocking I/O.

## Features

- **Live diagnostics** — compile errors shown in the editor via `nostos-lsp`
- **Quick-fixes** — "Add missing import" on unknown function/variable errors, via `nostos-lsp` code actions
- **Hover documentation** — type and doc information on hover and Ctrl+Q
- **Parameter info** — signature help while calling functions
- **Syntax highlighting** — keywords, strings, numbers, comments, operators, types
- **Code completion** — keyword completion with context-aware suffixes
- **Go to Definition** — navigate to declarations of values, functions, and types
- **Go to Symbol** — jump to any function or type across the project (Ctrl+Alt+Shift+N)
- **Find Usages** — find all references to a symbol across files
- **Highlight usages** — highlight every occurrence of the symbol under the caret (Ctrl+Shift+F7)
- **Call Hierarchy** — explore incoming and outgoing calls of a function (Ctrl+Alt+H)
- **CodeVision** — "N references" hint above function and type definitions
- **Inlay type hints** — inferred types shown inline
- **Rename refactoring** — scope-aware rename via `nostos-lsp` (Shift+F6)
- **Code formatting** — automatic indentation and spacing (Ctrl+Alt+L)
- **Run configurations** — run Nostos programs directly from the IDE
- **New Project wizard** — scaffold a Nostos project, with a New Nostos File action
- **Structure view** — outline of declarations, modules, types, and traits
- **Code folding** — collapse blocks, functions, and modules
- **Brace matching** — highlight matching braces, brackets, and parentheses
- **Commenting** — toggle line comments (`#`) and block comments (`#* *#`)

## Installation

### From JetBrains Marketplace

Search for "Nostos" in **Settings → Plugins → Marketplace**.

### From disk

1. Download the latest release zip from [Releases](https://github.com/hallyhaa/intellij-nostos/releases)
2. In IntelliJ IDEA, go to **Settings → Plugins → ⚙️ → Install Plugin from Disk...**
3. Select the zip file

## Supported file types

`.nos` files are automatically recognized as Nostos source files.

## Requirements

- **Nostos 0.2.18 or later** — the plugin uses `nostos-lsp` for live diagnostics, which ships with the Nostos distribution
- IntelliJ IDEA 2025.3 and later (Community and Ultimate)
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
