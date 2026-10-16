package br.com.vrinteriorpaulista.validator_infra.dto.request;

import br.com.vrinteriorpaulista.validator_infra.enums.Perfil;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UsuarioCriacaoRequest(
        @NotBlank @Size(max = 50)
        @Pattern(regexp = "[A-Za-z0-9._-]+", message = "use só letras, números, ponto, hífen e sublinhado")
        String login,
        @NotBlank @Size(max = 100) String nome,
        @NotBlank @Email @Size(max = 150) String email,
        @NotNull Perfil perfil,
        @NotBlank @Size(min = 8, max = 100, message = "a senha precisa ter pelo menos 8 caracteres") String senha
) {}
