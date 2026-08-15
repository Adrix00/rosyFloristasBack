# ArchUnit Rules

Architecture rules are enforced automatically.

Examples:

- Domain cannot depend on Spring.
- Domain cannot depend on Infrastructure.
- Services implement UseCases.
- Controllers depend only on UseCases.
- Persistence adapters implement Output Ports.
- Web classes never access persistence directly.
- DTOs never enter the Domain.
