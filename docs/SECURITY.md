# Spring Security

Goal: protect your API

Spring Security sits in front of Spring Web (API/MVC) endpoints and decides whether request can be let through.

## What it does

1. Intercepts every HTTP request (before reaching `@RestController`)
2. Then Authentication happens (question: who are you?):
    - client sends the token (i.e. JWT)
    - Spring Security validates the token
    - if valid => created an Authentication object that stores SecurityContext for the rest of the request
    - if fails => 401 Unauthorized
3. Then Authorization happens (question: are you allowed to do/see this?):
    - check URL patterns like /admin/, /internal_api/ and so on (configured via `SecurityFilterChain`)
    - checks roles and scopes as well
    - if fails => 403 Forbidden

By default, it is defensive protection; everything requires authentication, meaning whatever is not allowed, it is
forbidden.
And you will get either 401 or 403 depending on the configuration.

Therefore, if Spring Security dependency was added, you have to configure it (allow access) explicitly.

Spring security starter:

```kotlin
implementation("org.springframework.boot:spring-boot-starter-security")
```

If you need to protect your API using AWS Cognito (what we use in the company), you need to additionally add these
dependencies:

```kotlin
implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
implementation("org.springframework.boot:spring-boot-starter-oauth2-client") // when you need to call other services (service-to-service)
```

Minimal Configuration:

- stateless
- API accepts bearer token (i.e., JWT) issued by provider (for example, Cognito)
- API validates the token and decides whether to allow access or not

```kotlin
@Configuration
class SecurityConfiguration {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() } // common for stateless APIs
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { authorizeHttpRequests ->
                authorizeHttpRequests.requestMatchers("/status/**").permitAll()
                authorizeHttpRequests.requestMatchers("/actuator/**").permitAll()
                authorizeHttpRequests.requestMatchers(HttpMethod.GET, "/api/**").hasAuthority("SCOPE_read")
                authorizeHttpRequests.requestMatchers(HttpMethod.POST, "/api/**").hasAuthority("SCOPE_write")
                authorizeHttpRequests.anyRequest().authenticated()
            }
            .formLogin { formLogin -> formLogin.disable() }
            .logout { logout -> logout.disable() }
            .httpBasic { httpBasic -> httpBasic.disable() }
            .oauth2ResourceServer { oauth2 -> oauth2.jwt {} } // tells Spring to validate JWT token

        return http.build()
    }
}
```

## Scopes

When we create Cognito configuration (in IAC), we define scopes as well, for example:

```hcl
resource "aws_cognito_resource_server" "self" {
  identifier = "rfq-service"
  name       = "OneP RFQ Services"

  scope {
    scope_name        = "*.rw"
    scope_description = "Access to all resources"
  }

  user_pool_id = data.aws_ssm_parameter.pool_id.value
}
```

But it can be also fine-grained:

```hcl
scope { scope_name = "rfq.create" scope_description = "Create RFQs" }
scope { scope_name = "rfq.update" scope_description = "Update RFQs" }
scope { scope_name = "rfq.read" scope_description = "Read RFQs" }
```

Then the token would have the scopes in `scope` property.

For example, in conversions-service we have it like this:

```kotlin
authorizeHttpRequests
    .requestMatchers("/v0/conversations/**").hasAuthority("SCOPE_conversations/*.rw")
    .requestMatchers("/v1/conversations/**").hasAuthority("SCOPE_conversations/*.rw")
    .requestMatchers("/v0/admin/**").hasAuthority("SCOPE_conversations/*.rw")
```

Basically in all our services we just use scopes as `*.rw` without explicitly specifying for READ or WRITE.

## Client Credentials (aka service-to-service authentication)

Basically, it means calling another protected service from our API.

### application.yaml

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://${JWT_OAUTH_ISSUER_URI}
      client:
        registration:
          supplierfacts-api:
            authorization-grant-type: "client_credentials"
            client-id: ${API_SUPPLIER_FACTS_CLIENT_ID}
            client-secret: ${API_SUPPLIER_FACTS_CLIENT_SECRET}
            scope: "supplier_facts/*.rw"
            client-authentication-method: "client_secret_basic"
        provider:
          supplierfacts-api:
            token-uri: ${API_OAUTH_TOKEN_URL}
```

But to make it work, we also have to register our application (API) as a client of the downstream service we are going
to call/access,
for example, with `supplierfacts-api`: https://github.com/visable-dev/supplier-facts-iac/pull/119/changes

```yaml
conversation-platform__supplier_facts_api:
  parameter_store_namespace: /%{cluster_name}/visable/conversations
  allowed_oauth_scopes: [ "supplier_facts/*.rw" ]
  env_name_prefix: api_supplier_facts_
```

And in the code to be able to call it:

```kotlin
@ConfigurationProperties(prefix = "app.api.supplierfacts-api")
data class SupplierFactsProperties(
    val url: String,
)

@Bean
fun supplierFactsServiceClient(
    builder: RestClient.Builder,
    properties: SupplierFactsProperties,
    authorizedClientManager: OAuth2AuthorizedClientManager,
): RestClient =
    buildRestClient(
        builder,
        authorizedClientManager,
        properties.url,
        "supplierfacts-api",
    )

private fun buildRestClient(
    builder: RestClient.Builder,
    authorizedClientManager: OAuth2AuthorizedClientManager, // Bean comes from Spring itself
    url: String,
    registrationId: String, // from application.yaml
): RestClient {
    // on each HTTP call to the downstream service ensure a valid OAuth2 access token exists; 
    // or refresh and inject the token (Authorization: Bearer <token>)
    val requestInterceptor =
        OAuth2ClientHttpRequestInterceptor(authorizedClientManager)

    return builder
        .baseUrl(url)
        .requestInterceptor(requestInterceptor)
        .defaultRequest { requestSpec ->
            // gets a valid access token for the right client and then attaches Authorization: Bearer <token> 
            requestSpec.attributes(
                RequestAttributeClientRegistrationIdResolver.clientRegistrationId(registrationId),
            )
        }.build()
}
```

And then it is used something like this:

```kotlin
supplierFactsServiceClient
    .get()
    .uri("/companies/{id}", supplierId)
    .retrieve()
    .body(CompanyDetailsResponse::class.java)
```
