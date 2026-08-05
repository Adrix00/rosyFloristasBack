# Transaction Conventions

Transactions belong to the Application layer.

## Writing Use Cases

Must be transactional.

```
@Transactional
```

## Reading Use Cases

Must not open transactions.

Controllers never define transactions.

Repositories never define transactions.
