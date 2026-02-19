package com.ainexus.hospital.patient.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "Hospital Management System — Patient Module API",
        version = "1.0.0",
        description = """
            REST API for the Patient Module. All endpoints require a valid Bearer JWT token
            issued by the Auth Module. Role-based access control is enforced server-side on
            every request.
            """
    ),
    servers = {
        @Server(url = "https://localhost/api/v1", description = "Local Docker environment"),
        @Server(url = "http://localhost:8080/api/v1", description = "Direct backend (dev only)")
    },
    security = @SecurityRequirement(name = "BearerAuth")
)
@SecurityScheme(
    name = "BearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    description = "JWT issued by the Auth Module. Must contain claims: userId, username, role."
)
public class OpenApiConfig {}
