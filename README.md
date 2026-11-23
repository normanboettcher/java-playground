# java-playground

Small, runnable Java showcases that go with my LinkedIn posts.

Each showcase takes **one claim** about backend engineering and **proves it with code** you can
run yourself. No slides and no hand-waving: just a focused module with a test suite that shows the
behaviour, including the broken behaviour.

## Showcases

| # | Showcase | Takeaway | Tech | Post |
|---|---|---|---|---|
| 1 | [**Late commits break cursors**](late-commit-showcase) | A cursor on any value assigned before commit (timestamp *or* id) silently loses data. The transactional outbox doesn't. | Java · JDBC · PostgreSQL · MariaDB · Testcontainers | [LinkedIn](TODO-link) · TODO-date |

More showcases will be added as new posts go live.

## What every showcase has in common

- **Deterministic.** No sleeps, no thread races, no flaky timing. Every run gives the same result.
- **Executable documentation.** Tests pass when the documented behaviour happens, so a passing
  test can document a failure mode as well as its fix.
- **Real infrastructure.** Databases and brokers run in Docker via Testcontainers, not as mocks.
- **Minimal on purpose.** Plain Java wherever possible, so no framework hides what actually happens.
- **Self-contained.** Each module has its own README explaining the problem, the proof and the fix.

## Run it

Requirements: Java 25+, Maven 3.9+, and Docker running (for Testcontainers).

```bash
# all showcases
mvn verify

# a single showcase
mvn -pl late-commit-showcase verify
```

## Repository layout

```
java-playground/
├── pom.xml                  # parent: shared versions (JUnit, Testcontainers, AssertJ, JDBC drivers)
└── late-commit-showcase/    # one Maven module per showcase, each with its own README
```

## Feedback

Found an edge case I missed, or disagree with a conclusion? Open an issue or comment on the
LinkedIn post. Challenging the claims is exactly what these showcases are for.
