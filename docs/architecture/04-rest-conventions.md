# REST Conventions

## Resource Naming

Resources use plural nouns.

Example:

```
/categories
/products
/orders
```

## HTTP Methods

POST

Creates a resource.

Returns:

```
201 Created
```

PUT

Completely updates a resource.

Returns:

```
200 OK
```

PATCH

Updates a specific business state.

Example:

```
PATCH /categories/{id}/status
```

DELETE

Removes a resource permanently.

Returns:

```
204 No Content
```

GET

Returns resources.

## API Versioning

All endpoints are versioned.

Example:

```
/api/v1/categories
```
