package com.oficina.execution.infrastructure.security;

import com.oficina.execution.infrastructure.security.service.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SEGREDO = "chave-de-teste-super-longa-para-hmac-sha-256-minimo-32-chars";

    private final JwtService service = new JwtService(SEGREDO);

    private String token(String segredo, String issuer, String sub, String role, Instant expiracao) {
        var builder = Jwts.builder().subject(sub).issuer(issuer).expiration(Date.from(expiracao))
            .signWith(Keys.hmacShaKeyFor(segredo.getBytes(StandardCharsets.UTF_8)));
        if (role != null) {
            builder.claim("role", role);
        }
        return builder.compact();
    }

    @Test
    void aceitaTokenDoEmissorComPapelConhecido() {
        var sub = UUID.randomUUID().toString();
        var token = token(SEGREDO, "oficina-auth", sub, "FUNCIONARIO", Instant.now().plusSeconds(60));

        assertThat(service.isTokenValido(token)).isTrue();
        assertThat(service.extrairSujeito(token)).isEqualTo(sub);
        assertThat(service.extrairRole(token)).isEqualTo("FUNCIONARIO");
    }

    @Test
    void papelAusenteEquivaleACliente() {
        var token = token(SEGREDO, "oficina-auth", UUID.randomUUID().toString(), null, Instant.now().plusSeconds(60));

        assertThat(service.isTokenValido(token)).isTrue();
        assertThat(service.extrairRole(token)).isEqualTo("CLIENTE");
    }

    @Test
    void recusaEmissorPapelSujeitoAssinaturaEValidadeInvalidos() {
        var sub = UUID.randomUUID().toString();
        var futuro = Instant.now().plusSeconds(60);

        assertThat(service.isTokenValido(token(SEGREDO, "outro-emissor", sub, "CLIENTE", futuro))).isFalse();
        assertThat(service.isTokenValido(token(SEGREDO, "oficina-auth", sub, "ADMIN", futuro))).isFalse();
        assertThat(service.isTokenValido(token(SEGREDO, "oficina-auth", "nao-e-uuid", "CLIENTE", futuro))).isFalse();
        assertThat(service.isTokenValido(token(SEGREDO.replace('a', 'b'), "oficina-auth", sub, "CLIENTE", futuro))).isFalse();
        assertThat(service.isTokenValido(token(SEGREDO, "oficina-auth", sub, "CLIENTE", Instant.now().minusSeconds(60)))).isFalse();
        assertThat(service.isTokenValido("nao-e-um-jwt")).isFalse();
    }
}
