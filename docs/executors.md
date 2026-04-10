# Executors

Executors perform an action on the target block. Each takes one argument, which can be a literal or a `value_of()` expression.

Some executors are only available for specific block types. If the target block is not compatible, the executor is silently skipped.

---

## All Blocks

Available on: any target block

| Executor       | Argument | Description                                     |
|----------------|----------|-------------------------------------------------|
| `set_redstone` | `<0-15>` | Provides a redstone signal to the target block. |

Examples:

```
set_redstone 15
set_redstone value_of(get_page - 1)
```

---

## Data Lectern / Lectern

The following commands require the target block to be a `Data Lectern` or a regular `Lectern` with a book.

| Executor     | Argument   | Description                                                          |
|--------------|------------|----------------------------------------------------------------------|
| `set_page`   | `<1-100>`  | Sets the current page of the book in the lectern. Pages are 1-based. |
| `write_page` | `<string>` | Writes text to the current page, replacing its existing content.     |

Examples:

```
set_page 1
set_page value_of(redstone + 1)
```

```
write_page "Hello, world!"
write_page value_of(dimension as_string)
```

---

## Radio Transmitter / Radio Receiver

The following command requires the target block to be a `Radio Transmitter` or `Radio Receiver`.

| Executor        | Argument      | Description                                   |
|-----------------|---------------|-----------------------------------------------|
| `set_frequency` | `<frequency>` | Sets the radio channel the block is tuned to. |

Example:

```
set_frequency 10KHz
```

---

## Additional Executors

- [Create](create.md) — auto-generated executors for Create mod blocks (`set_rpm`, `set_filter`, `set_rotation_mode`, ...)
