package org.example.php2kotlinspingworkshop.persistence

import org.springframework.data.annotation.Id
import org.springframework.data.repository.CrudRepository

interface UserRepository : CrudRepository<User, Long> {
    fun findUserByFirstName(firstName: String): User?
}

data class User(
    @Id
    val id: Long,
    val email: String,
    val firstName: String,
)
