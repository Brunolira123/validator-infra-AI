package br.com.vrinteriorpaulista.validator_infra.prospeccao.service;

import br.com.vrinteriorpaulista.validator_infra.entity.Usuario;
import br.com.vrinteriorpaulista.validator_infra.enums.Perfil;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.dto.SalvarProspectRequest;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.entity.CategoriaProspeccao;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.entity.Prospect;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.entity.StatusProspect;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.repository.ProspectRepository;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.repository.VisitaProspectRepository;
import br.com.vrinteriorpaulista.validator_infra.repository.ClienteRepository;
import br.com.vrinteriorpaulista.validator_infra.security.UsuarioAutenticado;
import br.com.vrinteriorpaulista.validator_infra.service.ClienteService;
import br.com.vrinteriorpaulista.validator_infra.service.LevantamentoService;
import br.com.vrinteriorpaulista.validator_infra.service.UsuarioLogadoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Corrida no salvar: os dois não acham o placeId e o segundo bate no unique ao inserir.
 * A segunda tentativa (transação nova) já enxerga o prospect gravado pelo primeiro.
 */
class ProspectServiceTest {

    private static final long EU = 1L;
    private static final long OUTRO = 2L;

    private ProspectRepository repo;
    private ProspectService service;

    @BeforeEach
    void setUp() {
        repo = mock(ProspectRepository.class);
        UsuarioLogadoService usuarioLogado = mock(UsuarioLogadoService.class);
        when(usuarioLogado.usuario()).thenReturn(usuario(EU, "Eu"));
        when(usuarioLogado.atual()).thenReturn(new UsuarioAutenticado(EU, "eu", Perfil.COMERCIAL));

        service = new ProspectService(repo, mock(VisitaProspectRepository.class), mock(ClienteRepository.class),
                mock(ClienteService.class), mock(LevantamentoService.class), mock(ProspeccaoService.class),
                usuarioLogado, new TransactionTemplate(mock(PlatformTransactionManager.class)));
    }

    @Test
    void corridaDoMesmoUsuarioDevolveOExistente() {
        Prospect gravadoPeloOutroClique = prospect(10L, usuario(EU, "Eu"));
        when(repo.findByPlaceIdIncluindoExcluidos(anyString()))
                .thenReturn(Optional.empty(), Optional.of(gravadoPeloOutroClique));
        when(repo.saveAndFlush(any(Prospect.class))).thenThrow(violacaoDoPlaceId());

        ProspectService.Salvo salvo = service.salvar(request());

        assertThat(salvo.criado()).isFalse();
        assertThat(salvo.prospect().id()).isEqualTo(10L);
        verify(repo, times(1)).saveAndFlush(any(Prospect.class));
    }

    @Test
    void corridaComOutroComercialDa409ComONomeDele() {
        when(repo.findByPlaceIdIncluindoExcluidos(anyString()))
                .thenReturn(Optional.empty(), Optional.of(prospect(10L, usuario(OUTRO, "Maria"))));
        when(repo.saveAndFlush(any(Prospect.class))).thenThrow(violacaoDoPlaceId());

        assertThatThrownBy(() -> service.salvar(request()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Este lugar já é prospect de Maria");
    }

    @Test
    void outraViolacaoNaoTentaDeNovo() {
        when(repo.findByPlaceIdIncluindoExcluidos(anyString())).thenReturn(Optional.empty());
        when(repo.saveAndFlush(any(Prospect.class))).thenThrow(new DataIntegrityViolationException(
                "null value in column \"cidade\" violates not-null constraint"));

        assertThatThrownBy(() -> service.salvar(request())).isInstanceOf(DataIntegrityViolationException.class);
        verify(repo, times(1)).findByPlaceIdIncluindoExcluidos(anyString());
    }

    private static DataIntegrityViolationException violacaoDoPlaceId() {
        return new DataIntegrityViolationException(
                "could not execute statement [ERROR: duplicate key value violates unique constraint \""
                        + Prospect.UK_PLACE_ID + "\"]");
    }

    private static Prospect prospect(Long id, Usuario responsavel) {
        Prospect p = new Prospect();
        p.setId(id);
        p.setPlaceId("ChIJcorrida");
        p.setCidade("Limeira");
        p.setUf("SP");
        p.setStatus(StatusProspect.NOVO);
        p.setResponsavel(responsavel);
        return p;
    }

    private static Usuario usuario(Long id, String nome) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setNome(nome);
        return u;
    }

    private static SalvarProspectRequest request() {
        return new SalvarProspectRequest("ChIJcorrida", "Limeira", "SP", CategoriaProspeccao.PADARIA);
    }
}
