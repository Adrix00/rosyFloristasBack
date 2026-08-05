# Naming Conventions

## Use Cases

```
CreateCategoryUseCase
```

## Services

```
CreateCategoryService
```

## Commands

```
CreateCategoryCommand
```

## Queries

```
GetCategoryQuery
```

## Requests

```
CreateCategoryRequest
```

## Responses

```
CategoryResponse
```

## Ports

Output Ports follow a capability-based naming convention.

Examples:

```
SaveCategoryPort

FindCategoryByIdPort

FindCategoriesPort

ExistsCategoryByNamePort

DeleteCategoryPort
```

Generic names like:

```
CategoryRepository

CategoryGateway
```

must not be used.
