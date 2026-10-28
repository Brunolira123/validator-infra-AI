package br.com.vrinteriorpaulista.validator_infra.prospeccao.dto;

import br.com.vrinteriorpaulista.validator_infra.prospeccao.entity.CategoriaProspeccao;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.entity.Prospect;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.entity.StatusProspect;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.entity.TemperaturaNegociacao;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.google.LugarGoogle;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Lugar achado no Google, para exibir na hora. Nada daqui vai para o banco (termos do Google), só o placeId.
 * fotoUrl é relativa à base da API (/api) e exige o Bearer: o front baixa como blob.
 * prospectId, statusProspect e temperatura vêm preenchidos quando o lugar já foi salvo como prospect.
 */
public record LugarEncontradoResponse(
        String placeId,
        String nome,
        String endereco,
        Double latitude,
        Double longitude,
        List<CategoriaProspeccao> categorias,
        String fotoUrl,
        String fotoAutor,
        String fotoAutorUrl,
        String googleMapsUrl,
        String rotaUrl,
        String statusFuncionamento,
        Long prospectId,
        StatusProspect statusProspect,
        TemperaturaNegociacao temperatura
) {
    public static final int LARGURA_FOTO = 400;

    public static LugarEncontradoResponse from(LugarGoogle lugar, List<CategoriaProspeccao> categorias) {
        Double lat = lugar.location() != null ? lugar.location().latitude() : null;
        Double lng = lugar.location() != null ? lugar.location().longitude() : null;

        LugarGoogle.Foto foto = lugar.photos() == null || lugar.photos().isEmpty() ? null : lugar.photos().get(0);
        LugarGoogle.Autor autor = foto == null || foto.authorAttributions() == null || foto.authorAttributions().isEmpty()
                ? null : foto.authorAttributions().get(0);

        return new LugarEncontradoResponse(
                lugar.id(),
                lugar.displayName() != null ? lugar.displayName().text() : null,
                lugar.formattedAddress(),
                lat,
                lng,
                categorias,
                foto != null ? "/prospeccao/foto?nome=" + codificar(foto.name()) + "&w=" + LARGURA_FOTO : null,
                autor != null ? autor.displayName() : null,
                autor != null ? autor.uri() : null,
                lugar.googleMapsUri(),
                rota(lugar.id(), lat, lng, lugar.formattedAddress()),
                lugar.businessStatus(),
                null,
                null,
                null
        );
    }

    /** Marca o lugar com o prospect salvo (só os dados nossos: id, status e temperatura). */
    public LugarEncontradoResponse comProspect(Prospect prospect) {
        return new LugarEncontradoResponse(placeId, nome, endereco, latitude, longitude, categorias, fotoUrl, fotoAutor,
                fotoAutorUrl, googleMapsUrl, rotaUrl, statusFuncionamento,
                prospect.getId(), prospect.getStatus(), prospect.getTemperatura());
    }

    private static String rota(String placeId, Double lat, Double lng, String endereco) {
        String destino = lat != null && lng != null ? lat + "," + lng : endereco;
        return "https://www.google.com/maps/dir/?api=1&destination=" + codificar(destino)
                + "&destination_place_id=" + codificar(placeId);
    }

    private static String codificar(String valor) {
        return URLEncoder.encode(valor == null ? "" : valor, StandardCharsets.UTF_8);
    }
}
