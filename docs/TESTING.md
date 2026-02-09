# Testing in Spring

Testing:

- using MockK (unit-testing)
- integration Spring Boot tests

NOTE: WireMock is out of scope.

## Why mocking at all?

Testing without involving undeterministic/slow logic:

- network calls
- database queries
- filesystem interactions
- other external services (AWS, Mail, APIs)

What mocks do not give:

- SQL correctness (need of real DB container)
- Spring configuration correctness
- Serialization/deserialization correctness (WireMock or something similar)
- Real AWS behavior (localstack container)

## Unit Testing

Unit tests should not involve Spring at all, they should test only the logic in Kotlin code.

Example service:

```kotlin
class UserService(private val repo: UserRepository) {
    fun renameUser(email: String, newName: String): User {
        val u = repo.findByEmail(email) ?: throw NoSuchElementException("User not found")
        val updated = u.copy(name = newName)
        repo.save(updated)
        return updated
    }
}

interface UserRepository {
    fun findByEmail(email: String): User?
    fun save(user: User)
}

data class User(val id: Long, val email: String, val name: String)
```

```kotlin
class UserServiceTest {

    private val repo = mockk<UserRepository>()
    private val service = UserService(repo)

    @Test
    fun `should rename existing user`() {
        val u = User(1, "a@b.com", "Old")

        every { repo.findByEmail("a@b.com") } returns u
        every { repo.save(any()) } just runs

        val result = service.renameUser("a@b.com", "New")

        assertEquals("New", result.name)
        verify(exactly = 1) { repo.save(u.copy(name = "New")) }
    }

    @Test
    fun `should throw when user not found`() {
        every { repo.findByEmail(any()) } returns null

        assertThrows(NoSuchElementException::class.java) {
            service.renameUser("x@y.com", "Name")
        }

        verify(exactly = 0) { repo.save(any()) }
    }
}
```

There is also a need for some dependencies: `testImplementation("io.mockk:mockk:1.13.12")`

## Integration Testing

Need when there is a real writing with a real DB (in a running container).

Integration tests verify:

- Spring configuration correctness
- Beans wiring
- Serialization/Deserialization
- Repository correctness and SQL
- Transactions

Example:

```kotlin
@SpringBootTest
class UserServiceIntegrationTest {
    @Autowired
    private val userService: UserService

    @Test
    fun `should rename user`() {
        // uses real datasource, real repository, real transaction
    }
}
```

How is it done in terms of real DB?

Answer: docker/docker-compose

`docker-compose.yaml`

```yaml
services:
  postgres:
    image: public.ecr.aws/docker/library/postgres:16
    ports:
      - "5432:5432"
    environment:
      - POSTGRES_HOST_AUTH_METHOD=trust
      - POSTGRES_DB=postgres
      - POSTGRES_USER=postgres
      - POSTGRES_PASSWORD=postgres
    volumes:
      - "./postgres:/docker-entrypoint-initdb.d"
  localstack:
    image: localstack/localstack:3.4.0
    ports:
      - "4566:4566"
    environment:
      - SERVICES=sqs
      - DOCKER_HOST=unix:///var/run/docker.sock
      - DEFAULT_REGION=eu-central-1
      - CLUSTER_NAME=LOCAL_CLUSTER
    volumes:
      - './localstack:/var/lib/localstack'
      - "./localstack/init-aws.sh:/etc/localstack/init/ready.d/init-aws.sh"
      - "./localstack/init-scripts/:/etc/localstack/init/ready.d/init-scripts/"
```

### Testing Controllers

Usually we do not do that, but there is a way to test web layer separately from other layers.

```kotlin
@WebMvcTest(UserController::class)
class UserControllerTest(
    @Autowired val mvc: MockMvc
) {
    @MockBean
    lateinit var userService: UserService

    @Test
    fun `should return user`() {
        every { userService.findById(any()) } returns UserDto(1, "a@b.com")

        mvc.perform(get("/users/1"))
            .andExpect(status().isOk)
    }
}
```

### Application config for integration tests

We can use `application-test.yaml` so it will be merged with default `application.yaml` config

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5433/app_test
    username: username
    password: password

  flyway:
    enabled: true
```

Add the annotation to the integration test:

```kotlin
@SpringBootTest
@ActiveProfiles("test")
class SomeIntegrationTest
```

## Other

### @DataJdbcTest

To test repositories in separation

```kotlin
@DataJdbcTest
@ActiveProfiles("test")
class UserRepositoryIntegrationTest
```
