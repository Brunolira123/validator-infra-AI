package br.com.vrinteriorpaulista.validator_infra.dto.request;

import br.com.vrinteriorpaulista.validator_infra.enums.Perfil;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * O login não muda (é a chave que a pessoa usa para entrar). {@code senha} vazia mantém a atual.
 */
public record UsuarioAtualizacaoRequest(
        @NotBlank @Size(max = 100) String nome,
        @NotBlank @Email @Size(max = 150) String email,
        @NotNull Perfil perfil,
        @NotNull Boolean ativo,
        @Size(max = 100) String senha
) {}
