package br.com.vrinteriorpaulista.validator_infra.dto.response;

public record CnpjResponseDTO(
        String cnpj,
        String razaoSocial,
        String nomeFantasia,
        String endereco,
        String cidade,
        String uf,
        String telefone,
        String email,
        /** Selo do público-alvo (Lucro Real). Só informativo: nada é bloqueado por ele. */
        PerfilTributarioDTO perfil
) {}
