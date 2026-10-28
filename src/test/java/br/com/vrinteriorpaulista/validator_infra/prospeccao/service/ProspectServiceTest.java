package br.com.vrinteriorpaulista.validator_infra.prospeccao.service;

import br.com.vrinteriorpaulista.validator_infra.entity.Usuario;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.dto.SalvarProspectRequest;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.entity.CategoriaProspeccao;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.entity.Prospect;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.repository.ProspectRepository;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.repository.VisitaProspectRepository;
import br.com.vrinteriorpaulista.validator_infra.repository.ClienteRepository;
import br.com.vrinteriorpaulista.validator_infra.service.ClienteService;
import br.com.vrinteriorpaulista.validator_infra.service.LevantamentoService;
import br.com.vrinteriorpaulista.validator_infra.service.UsuarioLogadoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Corrida no salvar: os dois não acham o placeId e o segundo bate no unique ao inserir. */
class ProspectServiceTest {

    private ProspectRepository repo;
    private ProspectService service;

    @BeforeEach
    void setUp() {
        repo = mock(ProspectRepository.class);
        UsuarioLogadoService usuarioLogado = mock(UsuarioLogadoService.class);
        when(usuarioLogado.usuario()).thenReturn(new Usuario());
        when(repo.findByPlaceIdIncluindoExcluidos(anyString())).thenReturn(Optional.empty());

        service = new ProspectService(repo, mock(VisitaProspectRepository.class), mock(ClienteRepository.class),
                mock(ClienteService.class), mock(LevantamentoService.class), mock(ProspeccaoService.class),
                usuarioLogado, new TransactionTemplate(mock(PlatformTransactionManager.class)));
    }

    @Test
    void salvarConcorrenteNoMesmoPlaceIdViraConflito() {
        when(repo.saveAndFlush(any(Prospect.class))).thenThrow(new DataIntegrityViolationException(
                "could not execute statement [ERROR: duplicate key value violates unique constraint \""
                        + Prospect.UK_PLACE_ID + "\"]"));

        assertThatThrownBy(() -> service.salvar(request()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("acabou de ser salvo");
    }

    @Test
    void outraViolacaoNaoViraConflitoDePlaceId() {
        when(repo.saveAndFlush(any(Prospect.class))).thenThrow(new DataIntegrityViolationException(
                "null value in column \"cidade\" violates not-null constraint"));

        assertThatThrownBy(() -> service.salvar(request())).isInstanceOf(DataIntegrityViolationException.class);
    }

    private static SalvarProspectRequest request() {
        return new SalvarProspectRequest("ChIJcorrida", "Limeira", "SP", CategoriaProspeccao.PADARIA);
    }
}
