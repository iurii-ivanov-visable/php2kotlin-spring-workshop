# Spring Web and REST

Spring Web is the HTTP layer of Spring Framework.
It maps HTTP request, calls specific method and returns a response.

It is annotation driven and declarative.

## REST Controllers

For example:
`GET /users/<id>?platform=ep`

```kotlin
@RestController
@RequestMapping("/users")
class UserController(
    private val service: UserService
) {
    @GetMapping("/{userId}")
    fun getRfqs(
        @PathVariable("userId") userId: UUID,
        @RequestParam("platform") platform: String,
    ): ResponseEntity<UserDto> {
        // some logic and calling UserService
        val user = service.find(..)
        return ResponseEntity.ok(user)
    }
}
```

Here JSON serialization is automatic (implicit), it uses Jackson lib (comes with Spring).

In Symfony JSON response is explicit:

```php
#[Route('/users')]
final class UserController
{
    public function __construct(private UserService $service) {}

    #[Route('/{userId}', methods: ['GET'])]
    public function get(int $id, Request $request): JsonResponse
    {
        // request parameter
        $platform = $request->query->get('platform');
    
        return new JsonResponse(
            $this->service->find($id, $platform)
        );
    }
}
```

## Mappings

### @GetMapping

```kotlin
@GetMapping("/{id}")
fun get(@PathVariable id: Long): UserDto = service.find(id)
```

### @PostMapping

```kotlin
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
fun create(@RequestBody dto: CreateUserDto): UserDto = service.create(dto)
```

CreateUserDto - data transfer object / JSON Request body

### @DeleteMapping

```kotlin
@DeleteMapping("/{id}")
@ResponseStatus(HttpStatus.NO_CONTENT)
fun delete(@PathVariable id: Long) {
    service.delete(id)
}
```

Status code controlled by annotation

## Parameters binding

In Spring parameters are bind declaratively.
Unlike Symphony, where they are bind imperatively.

For example,

in Simphony, we have to deal with a request object:

```php
$q = $request->query->getBoolean('isActive', false);
$id = $request->headers->get('X-Request-ID');
```

In Spring, everything is injected automatically:

```kotlin
@GetMapping("/{id}")
fun getSomething(
    @PathVariable id: UUID,
    @RequestParam isActive: Boolean?,
    @RequestHeader("X-Request-ID") requestId: String
)
```

## Validation

Symphony:

```php
$errors = $validator->validate($dto);
if (count($errors) > 0) {
    return new JsonResponse($errors, 400);
}
```

It is explicit, the validation and error handling.

Spring:

```kotlin
@PostMapping
fun create(@Valid @RequestBody dto: CreateUserDto): UserDto
```

DTO should have validators:

```kotlin
data class CreateUserDto(
    @field:NotBlank
    val name: String,

    @field:Email
    val email: String,

    @field:Min(18)
    @field:Max(99)
    val age: Int
)
```

Custom validators can be implemented as well.

## Exception

```kotlin
@ControllerAdvice
class ErrorHandler {

    @ExceptionHandler(NotFoundException::class)
    fun notFound() =
        ResponseEntity.notFound().build()
}
```

## How Spring Web Works internally

```
HTTP request
  ↓
DispatcherServlet
  ↓
HandlerMapping (find controller)
  ↓
ArgumentResolvers (bind params)
  ↓
Controller method
  ↓
MessageConverters (JSON)
  ↓
HTTP response
```

## Good Practices to keep

### 1. Usage of appropriate status codes

Do not use the same status codes all the time:

- 200 OK for creation
- 200 OK for deletion
- 500 for user input mistake

Instead:

- 201 Created on POST that create resource
- 204 No Content on DELETE
- 400 Bad Request on invalid input
- 404 Not Found

```kotlin
@DeleteMapping
@ResponseStatus(HttpStatus.DELETED)
fun create(usrId: Long) {
    service.delete(userId)
}
```

### 2. Use DTO

Instead of leaking internal entities (JPA or persistence models / aggregates) directly or domain objects,
consider using a DTO pattern.

Bad idea:

```kotlin
@GetMapping("/{id}")
fun get(@PathVariable id: Long): UserEntity = repo.findById(id).get()
```

Better:

```kotlin
data class UserDto(val id: Long, val email: String, val name: String)

@GetMapping("/{id}")
fun get(@PathVariable id: Long): UserDto = service.getUser(id)
```

### 3. Avoid business logic in Controllers

Controllers are thin web layers.
With fat controllers they become untestable, inconsistent, code is more duplicated and less reusable, because logic is
spread across endpoints.

Rule of thumb: Controller = request/response mapping + validation + delegation to appropriate services.

### 4. Consider using versioning of APIs

For example, `/api/v1/...`
