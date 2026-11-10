package br.com.vrinteriorpaulista.validator_infra.projetos.service;

import br.com.vrinteriorpaulista.validator_infra.enums.Perfil;
import br.com.vrinteriorpaulista.validator_infra.projetos.entity.Planejamento;
import br.com.vrinteriorpaulista.validator_infra.projetos.repository.PlanejamentoRepository;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.entity.StatusProspect;
import br.com.vrinteriorpaulista.validator_infra.security.UsuarioAutenticado;
import br.com.vrinteriorpaulista.validator_infra.service.UsuarioLogadoService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

/**
 * Quem vê o quê no módulo Projetos. PROJETOS, ADMIN e DIRETORIA veem e editam tudo; COMERCIAL só lê os
 * planejamentos dos clientes cujo contrato ele fechou (prospect FECHADO ou LTO dele). Os demais perfis são barrados
 * nos controllers (403). Planejamento fora do alcance responde 404, igual a inexistente.
 */
@Service
public class AcessoProjetos {

    private static final Set<StatusProspect> FECHADOS = Set.of(StatusProspect.FECHADO, StatusProspect.LTO);
    private static final Set<Perfil> EDITORES = Set.of(Perfil.ADMIN, Perfil.DIRETORIA, Perfil.PROJETOS);

    private final PlanejamentoRepository repo;
    private final UsuarioLogadoService usuarioLogado;

    public AcessoProjetos(PlanejamentoRepository repo, UsuarioLogadoService usuarioLogado) {
        this.repo = repo;
        this.usuarioLogado = usuarioLogado;
    }

    /** PROJETOS, ADMIN ou DIRETORIA. */
    public boolean editor() {
        return EDITORES.contains(usuarioLogado.atual().perfil());
    }

    /** null = todos os clientes. */
    public Set<Long> clientesVisiveis() {
        UsuarioAutenticado atual = usuarioLogado.atual();
        if (EDITORES.contains(atual.perfil())) {
            return null;
        }
        if (atual.perfil() != Perfil.COMERCIAL) {
            return Set.of();
        }
        return new HashSet<>(repo.clientesFechadosPor(atual.id(), FECHADOS));
    }

    public Planejamento visivel(Long id) {
        Planejamento p = repo.findById(id).orElse(null);
        if (p == null) {
            throw naoEncontrado();
        }
        Set<Long> clientes = clientesVisiveis();
        if (clientes != null && !clientes.contains(p.getCliente().getId())) {
            throw naoEncontrado();
        }
        return p;
    }

    /** CRIADO_ARTIA e CANCELADO são somente leitura: 409. */
    public Planejamento editavel(Long id) {
        Planejamento p = visivel(id);
        if (!p.getSituacao().editavel()) {
            throw new IllegalStateException("Planejamento " + p.getSituacao() + " é somente leitura");
        }
        return p;
    }

    private static EntityNotFoundException naoEncontrado() {
        return new EntityNotFoundException("Planejamento não encontrado");
    }
}
