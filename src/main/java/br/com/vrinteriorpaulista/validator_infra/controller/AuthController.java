package br.com.vrinteriorpaulista.validator_infra.controller;

import br.com.vrinteriorpaulista.validator_infra.dto.request.LoginRequest;
import br.com.vrinteriorpaulista.validator_infra.dto.request.RefreshTokenRequest;
import br.com.vrinteriorpaulista.validator_infra.dto.response.LoginResponse;
import br.com.vrinteriorpaulista.validator_infra.dto.response.TokenResponse;
import br.com.vrinteriorpaulista.validator_infra.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Autenticação", description = "Login, renovação e revogação de tokens JWT")
@SecurityRequirements
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @Operation(summary = "Autentica e devolve access token (15 min) e refresh token (7 dias)", description = "Retorna 401 \"Credenciais inválidas\" para login inexistente, senha errada ou usuário inativo.")
    @PostMapping("/login")
    public LoginResponse login(@RequestBody @Valid LoginRequest req) {
        return service.login(req);
    }

    @Operation(summary = "Gera um novo access token a partir do refresh token", description = "Retorna 401 se o refresh token for inválido, expirado ou revogado.")
    @PostMapping("/refresh")
    public TokenResponse refresh(@RequestBody @Valid RefreshTokenRequest req) {
        return service.refresh(req.refreshToken());
    }

    @Operation(summary = "Revoga o refresh token", description = "O access token já emitido continua válido até expirar.")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestBody @Valid RefreshTokenRequest req) {
        service.logout(req.refreshToken());
    }
}
