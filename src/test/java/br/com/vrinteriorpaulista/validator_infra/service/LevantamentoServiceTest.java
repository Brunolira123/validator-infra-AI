package br.com.vrinteriorpaulista.validator_infra.service;

import br.com.vrinteriorpaulista.validator_infra.entity.Analise;
import br.com.vrinteriorpaulista.validator_infra.entity.Equipamento;
import br.com.vrinteriorpaulista.validator_infra.entity.Levantamento;
import br.com.vrinteriorpaulista.validator_infra.enums.StatusAnalise;
import br.com.vrinteriorpaulista.validator_infra.enums.StatusLevantamento;
import br.com.vrinteriorpaulista.validator_infra.repository.ClienteRepository;
import br.com.vrinteriorpaulista.validator_infra.repository.EquipamentoRepository;
import br.com.vrinteriorpaulista.validator_infra.repository.LevantamentoRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LevantamentoServiceTest {

    private static final Long ID = 10L;

    @Mock
    private LevantamentoRepository levantamentoRepo;

    @Mock
    private ClienteRepository clienteRepo;

    @Mock
    private EquipamentoRepository equipamentoRepo;

    @Mock
    private UsuarioLogadoService usuarioLogado;

    private LevantamentoService service;

    @BeforeEach
    void setUp() {
        service = new LevantamentoService(levantamentoRepo, clienteRepo, equipamentoRepo, usuarioLogado);
        lenient().when(levantamentoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Nested
    class Concluir {

        @Test
        void deveConcluirQuandoTodosOsEquipamentosEstiveremAnalisados() {
            Levantamento l = dadoLevantamento(StatusLevantamento.EM_ANALISE,
                    equipamento(1, StatusAnalise.ATENDE), equipamento(2, StatusAnalise.REQUER_ANALISE));

            assertThat(service.concluir(ID).getStatus()).isEqualTo(StatusLevantamento.CONCLUIDO);
            verify(levantamentoRepo).save(l);
        }

        @Test
        void deveRetornar409ComSequenciasPendentes() {
            dadoLevantamento(StatusLevantamento.EM_ANALISE,
                    equipamento(1, StatusAnalise.ATENDE), equipamentoSemAnalise(3), equipamento(2, StatusAnalise.PENDENTE));

            assertThatThrownBy(() -> service.concluir(ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("2 equipamentos ainda não analisados (sequências: 2, 3)");
            verify(levantamentoRepo, never()).save(any());
        }

        @Test
        void naoDeveConcluirLevantamentoCancelado() {
            dadoLevantamento(StatusLevantamento.CANCELADO, equipamento(1, StatusAnalise.ATENDE));

            assertThatThrownBy(() -> service.concluir(ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Levantamento cancelado não pode ser concluído");
        }
    }

    @Nested
    class Cancelar {

        @Test
        void deveCancelarLevantamentoEmRascunho() {
            dadoLevantamento(StatusLevantamento.RASCUNHO);

            assertThat(service.cancelar(ID).getStatus()).isEqualTo(StatusLevantamento.CANCELADO);
        }

        @Test
        void deveCancelarLevantamentoEmAnalise() {
            dadoLevantamento(StatusLevantamento.EM_ANALISE, equipamentoSemAnalise(1));

            assertThat(service.cancelar(ID).getStatus()).isEqualTo(StatusLevantamento.CANCELADO);
        }

        @Test
        void deveRetornar409AoCancelarLevantamentoConcluido() {
            dadoLevantamento(StatusLevantamento.CONCLUIDO);

            assertThatThrownBy(() -> service.cancelar(ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Levantamento concluído não pode ser cancelado. Reabra-o primeiro.");
            verify(levantamentoRepo, never()).save(any());
        }

        @Test
        void deveSerIdempotenteQuandoJaEstiverCancelado() {
            dadoLevantamento(StatusLevantamento.CANCELADO);

            assertThat(service.cancelar(ID).getStatus()).isEqualTo(StatusLevantamento.CANCELADO);
            verify(levantamentoRepo, never()).save(any());
        }
    }

    @Nested
    class Reabrir {

        @Test
        void deveReabrirLevantamentoConcluidoParaEmAnalise() {
            dadoLevantamento(StatusLevantamento.CONCLUIDO, equipamento(1, StatusAnalise.ATENDE));

            assertThat(service.reabrir(ID).getStatus()).isEqualTo(StatusLevantamento.EM_ANALISE);
        }

        @Test
        void deveRetornar409AoReabrirLevantamentoEmAnalise() {
            dadoLevantamento(StatusLevantamento.EM_ANALISE);

            assertThatThrownBy(() -> service.reabrir(ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Só é possível reabrir levantamento concluído (status atual: EM_ANALISE)");
        }

        @Test
        void deveRetornar409AoReabrirLevantamentoCancelado() {
            dadoLevantamento(StatusLevantamento.CANCELADO);

            assertThatThrownBy(() -> service.reabrir(ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("CANCELADO");
            verify(levantamentoRepo, never()).save(any());
        }

        @Test
        void deveRetornar404QuandoLevantamentoNaoExistir() {
            when(levantamentoRepo.findById(ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.reabrir(ID))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    private Levantamento dadoLevantamento(StatusLevantamento status, Equipamento... equipamentos) {
        Levantamento l = new Levantamento();
        l.setId(ID);
        l.setStatus(status);
        for (Equipamento e : equipamentos) {
            e.setLevantamento(l);
            l.getEquipamentos().add(e);
        }
        when(levantamentoRepo.findById(ID)).thenReturn(Optional.of(l));
        return l;
    }

    private static Equipamento equipamento(int sequencia, StatusAnalise resultado) {
        Equipamento e = equipamentoSemAnalise(sequencia);
        Analise a = new Analise();
        a.setEquipamento(e);
        a.setResultado(resultado);
        e.setAnalise(a);
        e.setStatus(resultado);
        return e;
    }

    private static Equipamento equipamentoSemAnalise(int sequencia) {
        Equipamento e = new Equipamento();
        e.setSequencia(sequencia);
        return e;
    }
}
