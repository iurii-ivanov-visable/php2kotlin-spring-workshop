# Spring Data — From JDBC to High-Level Abstractions

## Abstraction overview

```
Service
  ↓
Spring Data (JDBC / JPA)
  ↓
Spring JDBC Template / Hibernate
  ↓
JDBC
  ↓
Database
```

## 1. What is JDBC and what are drivers?

### JDBC

JDBC (Java Database Connectivity) is the low-level standard API (contract) for working with relational databases in
Java/Kotlin.

Similar to PDO in PHP.

JDBC provides:

- a common API
- SQL-first access
- database-agnostic interfaces

---

### Drivers

JDBC itself does not know how to talk to a database.

That responsibility is delegated to drivers:

- PostgreSQL: `org.postgresql.Driver`
- MySQL: com.mysql.cj.jdbc.Driver`

Drivers:

- implement JDBC interfaces
- are loaded at runtime
- translate JDBC calls into database-specific protocol

---

### PHP analogy (PDO)

PDO works the same way:

- one common API (`PDO`)
- database-specific drivers (`pdo_pgsql`, `pdo_mysql`)
- same code, different driver

---

## 2. Plain database access: JDBC vs PDO (no frameworks)

### Connection

**JDBC**

```kotlin
val conn = DriverManager.getConnection(
    "jdbc:postgresql://localhost:5432/db",
    "user",
    "password"
)
```

**PDO**

```php
$pdo = new PDO(
    'pgsql:host=localhost;port=5432;dbname=db',
    'user',
    'password',
    [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
    ]
);
```

---

### Prepared statements

**JDBC**

```kotlin
val stmt = conn.prepareStatement(
    "SELECT id, name FROM users WHERE id = ?"
)
stmt.setInt(1, 42)

val rs = stmt.executeQuery()
if (rs.next()) {
    val id = rs.getInt("id")
    val name = rs.getString("name")
}
```

**PDO**

```php
$stmt = $pdo->prepare(
    'SELECT id, name FROM users WHERE id = :id'
);

$stmt->execute(['id' => 42]);

$row = $stmt->fetch(PDO::FETCH_ASSOC);
```

---

### Plain Transactions

**JDBC**

```kotlin
conn.autoCommit = false
try {
    // some SQL statements
    conn.commit()
} catch (e: Exception) {
    conn.rollback()
}
```

**PDO**

```php
$pdo->beginTransaction();

try {
    // SQL statements
    $pdo->commit();
} catch (Throwable $e) {
    $pdo->rollBack();
    throw $e;
}
```

---

## 3. Spring JDBC Template

### The problem with plain JDBC

Plain JDBC requires:

- manual connection handling
- manual resource closing
- manual exception handling

```kotlin
val conn = dataSource.connection
val stmt = conn.prepareStatement("SELECT ...")
val rs = stmt.executeQuery()
// map rows
rs.close()
stmt.close()
conn.close()
```

This leads to:

- boilerplate
- bugs
- inconsistent error handling

---

### Spring JDBC Template

Spring JDBC Template is a thin abstraction over JDBC.

Purpose:

- remove boilerplate
- manage resources
- standardize exceptions
- keep SQL explicit

```kotlin
val jdbcTemplate = JdbcTemplate(dataSource)

val user = jdbcTemplate.queryForObject(
    "SELECT id, email FROM users WHERE id = ?",
    { rs, _ ->
        User(
            id = rs.getLong("id"),
            email = rs.getString("email")
        )
    },
    id
)
```

Spring handles:

- opening/closing connections
- preparing statements
- exception translation

## 4. Spring Data JDBC

### Repository pattern in PHP

```php
namespace App\Domain;

interface UserRepository {
    public function findById(int $id): ?User;
}
```

```php
final class UserRepository {
    public function __construct(private PDO $pdo) {}

    public function findById(int $id): ?array {
        $stmt = $this->pdo->prepare(
            'SELECT id, name FROM users WHERE id = :id'
        );
        $stmt->execute(['id' => $id]);
        return $stmt->fetch(PDO::FETCH_ASSOC) ?: null;
    }
}
```

```php
use App\Domain\UserRepository;
use PDO;

final class UserService {
    public function __construct(
        private PDO $pdo,
        private UserRepository $users,
    ) {}

    public function renameUser(int $id, string $newName): User {
        $this->pdo->beginTransaction();
        try {
            $u = $this->users->findById($id);
            // business logic
            $this->pdo->commit();
            return $u;
        } catch (\Throwable $e) {
            $this->pdo->rollBack();
            throw $e;
        }
    }
}
```

---

### Spring Data JDBC

Spring Data JDBC generates repository implementations automatically.

```kotlin
interface UserRepository : CrudRepository<User, Long> {
    fun findByEmail(email: String): User?
}
```

Usage:

```kotlin
class UserService(private val userRepository: UserRepository) {
    fun renameUser(email: String, newName: String) {
        val u = users.findByEmail(email)
        users.save(u.copy(name = newName))
    }
}
```

Spring Data JDBC:

1. generates repository implementations
2. executes SQL via JDBC Template
3. maps rows to objects

---

### PHP analogue

Closest analogue in PHP are symfony repositories:

```php
class UserRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, User::class);
    }

    public function findByEmail(string $email): ?User
    {
        return $this->createQueryBuilder('u')
            ->andWhere('u.email = :email')
            ->setParameter('email', $email)
            ->getQuery()
            ->getOneOrNullResult();
    }
}
```

But Spring Data goes further by generating implementations from interfaces.

---

## 5. JPA, Hibernate, and Spring Data JPA

### What is JPA?

**JPA (Java Persistence API)** is:

- a specification
- a set of annotations
- a contract for ORMs

JPA defines what an ORM must do, not how (requires implementation).

---

### Hibernate

**Hibernate** is:

- the most popular JPA implementation
- a full ORM
- responsible for SQL generation, caching and so on

Hibernate adds:

- entity state tracking
- lazy loading
- cache
- JPQL (Jakarta Persistence Query Language)

---

### Spring Data JPA

**Spring Data JPA**:

- builds on top of JPA (Hibernate)
- provides repository abstractions
- generates queries from method names

```kotlin
interface UserRepository : JpaRepository<User, Long> {
    fun findByEmail(email: String): User?
}
```

---

## 6. Transactions: from JDBC to Spring Data

### Transactions in plain JDBC

Problems:

- manual `commit` / `rollback`
- error-prone

```kotlin
try {
    // multiple DAO calls
    conn.commit()
} catch (e: Exception) {
    conn.rollback()
}
```

---

### Transactions with Spring (JDBC Template / Spring Data)

Spring introduces declarative transactions.

```kotlin
@Transactional
fun renameUser(email: String, newName: String) {
    val u = users.findByEmail(email)
    users.save(u.copy(name = newName))
}
```

Spring handles rollback/commit automatically and:

- propagation
- isolation
- transaction management

NOTE: too deep and not covered here

## 7. DataSource and application.yaml

Used for configurations.

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/db
    username: user
    password: password
    driver-class-name: org.postgresql.Driver
```
