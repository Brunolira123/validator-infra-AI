package br.com.vrinteriorpaulista.validator_infra.prospeccao.controller;

import br.com.vrinteriorpaulista.validator_infra.prospeccao.dto.PaginaBuscaProspectResponse;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.entity.CategoriaProspeccao;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.google.FotoGoogle;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.service.ProspeccaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

@RestController
@Tag(name = "Prospecção", description = "Busca de comércios de alimentos no Google Places para o comercial visitar")
@RequestMapping("/api/prospeccao")
@PreAuthorize("hasAnyRole('ADMIN', 'COMERCIAL', 'TECNICO')")
public class ProspeccaoController {

    private final ProspeccaoService service;

    public ProspeccaoController(ProspeccaoService service) {
        this.service = service;
    }

    @Operation(summary = "Busca comércios de alimentos numa cidade",
            description = "Uma consulta ao Google por categoria (padrão: todas), sem duplicados. Para carregar mais, "
                    + "repita cidade/uf e mande proximaPagina em 'pagina'. 503 se a busca não estiver configurada; "
                    + "429 quando o limite diário de buscas acabar.")
    @GetMapping("/busca")
    public PaginaBuscaProspectResponse buscar(@RequestParam @Size(max = 80) String cidade,
                                              @RequestParam String uf,
                                              @RequestParam(required = false) List<CategoriaProspeccao> categorias,
                                              @RequestParam(required = false) String pagina) {
        return service.buscar(cidade, uf, categorias, pagina);
    }

    @Operation(summary = "Foto de um lugar (proxy do Google)",
            description = "nome = places/{placeId}/photos/{ref}, como vem na fotoUrl da busca. 404 se a foto não "
                    + "existir ou o limite diário de fotos acabar (o front mostra o placeholder).")
    @GetMapping("/foto")
    public ResponseEntity<byte[]> foto(@RequestParam String nome, @RequestParam(required = false) Integer w) {
        FotoGoogle foto = service.foto(nome, w);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(foto.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                .body(foto.bytes());
    }
}
