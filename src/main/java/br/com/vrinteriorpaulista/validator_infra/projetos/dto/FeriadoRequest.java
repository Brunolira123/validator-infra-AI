package br.com.vrinteriorpaulista.validator_infra.projetos.dto;

import br.com.vrinteriorpaulista.validator_infra.projetos.entity.EscopoFeriado;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** LOCAL exige uf; cidade é opcional (sem cidade, vale para a UF inteira). NACIONAL não aceita cidade nem uf. */
public record FeriadoRequest(
        @NotNull LocalDate data,
        @NotBlank @Size(max = 100) String descricao,
        @NotNull EscopoFeriado escopo,
        @Size(max = 100) String cidade,
        @Size(min = 2, max = 2) String uf
) {}
